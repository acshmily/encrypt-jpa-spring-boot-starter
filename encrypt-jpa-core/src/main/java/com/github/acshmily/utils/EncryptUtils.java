package com.github.acshmily.utils;

import com.github.acshmily.exception.EncryptException;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 加密/解密工具类（2.0.0 重写）。
 *
 * <p>密文格式分两个版本：</p>
 * <ul>
 *   <li><b>v2（当前写入格式）</b>：{@code "enc:v2:" + Base64(IV[16] + AES/CBC/PKCS5密文)}。
 *       IV 内嵌于密文，解密端统一读取。两种模式：
 *       {@link #encryptDeterministic}（静态 IV，相同明文=相同密文，密文可用于 SQL 等值查询，
 *       {@code @EncryptParam} 参数恒用此模式）与 {@link #encryptRandom}（随机 IV，安全性最优，
 *       但密文不可用于等值查询）。明文长度不再受限（1.x 存在 1024 字节硬上限）。</li>
 *   <li><b>v1（历史格式，仅解密兼容）</b>：无前缀 {@code Base64(AES/CBC/PKCS5密文)}，
 *       使用配置的静态 IV。1.x 写入的存量数据仍可解密。</li>
 * </ul>
 *
 * <p>实现基于 {@code javax.crypto.Cipher}（1.x 依赖的 commons-crypto 已移除），
 * 通过 {@link ThreadLocal} 复用 Cipher 实例避免每次调用的 provider 查找开销。</p>
 *
 * @author Huanghz
 */
public final class EncryptUtils {

    /** v2 密文前缀，用于识别新格式（也用于防止对同一值重复加密） */
    public static final String V2_PREFIX = "enc:v2:";

    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final int IV_LENGTH = 16;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64_ENCODER = Base64.getEncoder();
    private static final Base64.Decoder BASE64_DECODER = Base64.getDecoder();

    /** Cipher 非线程安全，按线程复用 */
    private static final ThreadLocal<Cipher> CIPHER_HOLDER = ThreadLocal.withInitial(() -> {
        try {
            return Cipher.getInstance(TRANSFORMATION);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("JVM 不支持 " + TRANSFORMATION, e);
        }
    });

    private EncryptUtils() {
    }

    /**
     * v2 加密（随机 IV 模式）：每次加密生成随机 IV，相同明文产生不同密文。
     * 用于 {@code @Encrypt(random = true)} 的字段。随机 IV 的密文无法用于等值查询。
     *
     * @param plainText 明文，允许为 null（原样返回，由调用方决定语义）
     * @param key       AES 密钥
     * @return 带 v2 前缀的 Base64 密文
     * @throws EncryptException 加密失败
     */
    public static String encryptRandom(String plainText, SecretKeySpec key) {
        if (plainText == null) {
            return null;
        }
        byte[] iv = new byte[IV_LENGTH];
        RANDOM.nextBytes(iv);
        return encryptWithIv(plainText, key, iv);
    }

    /**
     * v2 加密（确定性模式）：使用配置的静态 IV，相同明文产生相同密文，
     * 因此该模式的密文可继续用于 SQL 等值查询（WHERE col = ?）。
     * v2 与 v1 的确定性密文字节不同（v2 前缀 + 内嵌 IV），存量 v1 数据需迁移后才能被新查询命中。
     *
     * @param plainText 明文，允许为 null（原样返回，由调用方决定语义）
     * @param key       AES 密钥
     * @param iv        静态 IV
     * @return 带 v2 前缀的 Base64 密文
     * @throws EncryptException 加密失败
     */
    public static String encryptDeterministic(String plainText, SecretKeySpec key, IvParameterSpec iv) {
        if (plainText == null) {
            return null;
        }
        return encryptWithIv(plainText, key, iv.getIV());
    }

    private static String encryptWithIv(String plainText, SecretKeySpec key, byte[] iv) {
        try {
            Cipher cipher = CIPHER_HOLDER.get();
            cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(cipherText, 0, out, iv.length, cipherText.length);
            return V2_PREFIX + BASE64_ENCODER.encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new EncryptException("加密失败", e);
        }
    }

    /**
     * 解密：按前缀自动识别 v2 / v1 格式。
     * 带 v2 前缀的值按随机 IV 解析；无前缀的值按 v1 静态 IV 解密（兼容存量数据）。
     *
     * @param value    密文
     * @param key      AES 密钥
     * @param legacyIv v1 静态 IV（仅用于无前缀的存量密文）
     * @return 明文
     * @throws EncryptException 值不是合法密文（例如本就是明文的存量数据）或解密失败
     */
    public static String decrypt(String value, SecretKeySpec key, IvParameterSpec legacyIv) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (value.startsWith(V2_PREFIX)) {
            return decryptV2(value.substring(V2_PREFIX.length()), key);
        }
        return decryptLegacy(value, key, legacyIv);
    }

    /**
     * v1 加密（静态 IV、无前缀）。仅供存量数据迁移与兼容性测试使用，新数据一律走 {@link #encrypt}。
     */
    public static String encryptLegacy(String plainText, SecretKeySpec key, IvParameterSpec iv) {
        if (plainText == null) {
            return null;
        }
        try {
            Cipher cipher = CIPHER_HOLDER.get();
            cipher.init(Cipher.ENCRYPT_MODE, key, iv);
            return BASE64_ENCODER.encodeToString(cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new EncryptException("加密失败(legacy)", e);
        }
    }

    private static String decryptV2(String payload, SecretKeySpec key) {
        byte[] data = decodeBase64(payload);
        // 至少包含 16 字节 IV + 1 个密文分组
        if (data.length < IV_LENGTH * 2) {
            throw new EncryptException("v2 密文长度非法");
        }
        try {
            Cipher cipher = CIPHER_HOLDER.get();
            cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(data, 0, IV_LENGTH));
            byte[] plain = cipher.doFinal(data, IV_LENGTH, data.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new EncryptException("v2 密文解密失败", e);
        }
    }

    private static String decryptLegacy(String value, SecretKeySpec key, IvParameterSpec legacyIv) {
        byte[] data = decodeBase64(value);
        // 廉价预检：v1 密文必为合法 Base64 且解码长度为 16 的整数倍；不满足则视为明文数据
        if (data.length < IV_LENGTH || data.length % IV_LENGTH != 0) {
            throw new EncryptException("不是合法的 v1 密文（长度非法）");
        }
        try {
            Cipher cipher = CIPHER_HOLDER.get();
            cipher.init(Cipher.DECRYPT_MODE, key, legacyIv);
            return new String(cipher.doFinal(data), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new EncryptException("v1 密文解密失败（可能本就是明文）", e);
        }
    }

    private static byte[] decodeBase64(String value) {
        try {
            return BASE64_DECODER.decode(value);
        } catch (IllegalArgumentException e) {
            throw new EncryptException("不是合法的 Base64 密文", e);
        }
    }
}
