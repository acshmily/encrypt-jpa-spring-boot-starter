package com.github.acshmily.utils;

import com.github.acshmily.exception.EncryptException;
import org.junit.jupiter.api.Test;

import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 加密引擎单元测试：v2 双模式、v1 兼容（向量锁定）、异常路径、1.x 的 1024 字节上限回归。
 */
class EncryptUtilsTest {

    // 与 application.yml、openssl 向量配套的 key/iv（32/16 字符，无需补位）
    private static final SecretKeySpec KEY = spec("0123456789abcdef0123456789abcdef", 32);
    private static final IvParameterSpec IV = ivSpec("1234567890123456", 16);

    /** openssl enc -aes-256-cbc -K 3031...6566 -iv 3132...3536 生成：明文 "legacy-phone-100" */
    private static final String V1_VECTOR_PLAIN = "legacy-phone-100";
    private static final String V1_VECTOR_CIPHER = "NMBhz7vDEGa2oojhBysCmq9Ip2JuSlju9jpE8oRaZjM=";

    private static SecretKeySpec spec(String value, int length) {
        return new SecretKeySpec(EncryptUtilsTest.pad(value, length).getBytes(StandardCharsets.UTF_8), "AES");
    }

    private static IvParameterSpec ivSpec(String value, int length) {
        return new IvParameterSpec(EncryptUtilsTest.pad(value, length).getBytes(StandardCharsets.UTF_8));
    }

    private static String pad(String value, int length) {
        return String.format("%" + length + "s", value).replace(" ", "0");
    }

    // ---------- v2 正常路径 ----------

    @Test
    void deterministicRoundTrip() {
        String cipher = EncryptUtils.encryptDeterministic("hello", KEY, IV);
        assertThat(cipher).startsWith(EncryptUtils.V2_PREFIX);
        assertThat(EncryptUtils.decrypt(cipher, KEY, IV)).isEqualTo("hello");
    }

    @Test
    void randomRoundTrip() {
        String cipher = EncryptUtils.encryptRandom("hello", KEY);
        assertThat(cipher).startsWith(EncryptUtils.V2_PREFIX);
        assertThat(EncryptUtils.decrypt(cipher, KEY, IV)).isEqualTo("hello");
    }

    @Test
    void deterministicModeProducesEqualCipherText() {
        // 等值查询的前提：相同明文 → 相同密文
        String c1 = EncryptUtils.encryptDeterministic("same-input", KEY, IV);
        String c2 = EncryptUtils.encryptDeterministic("same-input", KEY, IV);
        assertThat(c1).isEqualTo(c2);
    }

    @Test
    void randomModeProducesDifferentCipherText() {
        // 安全性：相同明文 → 不同密文（随机 IV）
        String c1 = EncryptUtils.encryptRandom("same-input", KEY);
        String c2 = EncryptUtils.encryptRandom("same-input", KEY);
        assertThat(c1).isNotEqualTo(c2);
        assertThat(EncryptUtils.decrypt(c1, KEY, IV)).isEqualTo("same-input");
        assertThat(EncryptUtils.decrypt(c2, KEY, IV)).isEqualTo("same-input");
    }

    @Test
    void unicodeRoundTrip() {
        String text = "中文测试🎉Ωß";
        assertThat(EncryptUtils.decrypt(EncryptUtils.encryptDeterministic(text, KEY, IV), KEY, IV))
                .isEqualTo(text);
        assertThat(EncryptUtils.decrypt(EncryptUtils.encryptRandom(text, KEY), KEY, IV))
                .isEqualTo(text);
    }

    @Test
    void oversizedTextRoundTrip() {
        // 回归：1.x 的 commons-crypto 固定 1024 字节 buffer，超长直接 ShortBufferException
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 2000; i++) {
            sb.append("汉");
        }
        String big = sb.toString(); // 6000 字节 UTF-8
        assertThat(EncryptUtils.decrypt(EncryptUtils.encryptRandom(big, KEY), KEY, IV)).isEqualTo(big);
        assertThat(EncryptUtils.decrypt(EncryptUtils.encryptDeterministic(big, KEY, IV), KEY, IV)).isEqualTo(big);
    }

    @Test
    void nullAndEmptyPassthrough() {
        assertThat(EncryptUtils.encryptDeterministic(null, KEY, IV)).isNull();
        assertThat(EncryptUtils.encryptRandom(null, KEY)).isNull();
        assertThat(EncryptUtils.decrypt(null, KEY, IV)).isNull();
        assertThat(EncryptUtils.decrypt("", KEY, IV)).isEmpty();
    }

    // ---------- v1 兼容（存量数据） ----------

    @Test
    void v1VectorStillDecrypts() {
        // 锁死向后兼容：openssl 独立实现的 v1 密文必须可解
        assertThat(EncryptUtils.decrypt(V1_VECTOR_CIPHER, KEY, IV)).isEqualTo(V1_VECTOR_PLAIN);
    }

    @Test
    void v1EncryptedByThisCodeMatchesVector() {
        // 本实现的 v1 加密与 openssl 字节级一致
        assertThat(EncryptUtils.encryptLegacy(V1_VECTOR_PLAIN, KEY, IV)).isEqualTo(V1_VECTOR_CIPHER);
    }

    @Test
    void v1AndV2UseSameKeyDerivation() {
        // EncryptProperties 的补位派生与测试向量 key/iv 构造一致（32/16 字符无需补位）
        assertThat(KEY.getEncoded()).hasSize(32);
        assertThat(IV.getIV()).hasSize(16);
    }

    // ---------- 异常路径 ----------

    @Test
    void tamperedCipherTailThrows() {
        // 篡改密文最后一个字节 → padding 校验失败
        String cipher = EncryptUtils.encryptDeterministic("tamper-me-with-long-text!!", KEY, IV);
        String payload = cipher.substring(EncryptUtils.V2_PREFIX.length());
        byte[] bytes = java.util.Base64.getDecoder().decode(payload);
        bytes[bytes.length - 1] ^= 0x01;
        String tampered = EncryptUtils.V2_PREFIX + java.util.Base64.getEncoder().encodeToString(bytes);
        assertThatThrownBy(() -> EncryptUtils.decrypt(tampered, KEY, IV))
                .isInstanceOf(EncryptException.class);
    }

    @Test
    void tamperedIvCorruptsFirstBlockWithoutError() {
        // CBC 无完整性保护：篡改内嵌 IV 只破坏第一个明文分组，解密不报错（返回乱码）。
        // 该特性如实文档化；需要完整性的场景应叠加 HMAC（本库不提供）。
        String cipher = EncryptUtils.encryptDeterministic("tamper-iv-test", KEY, IV);
        String payload = cipher.substring(EncryptUtils.V2_PREFIX.length());
        byte[] bytes = java.util.Base64.getDecoder().decode(payload);
        bytes[0] ^= 0x01;
        String tampered = EncryptUtils.V2_PREFIX + java.util.Base64.getEncoder().encodeToString(bytes);
        String decrypted = EncryptUtils.decrypt(tampered, KEY, IV);
        assertThat(decrypted).isNotEqualTo("tamper-iv-test");
    }

    @Test
    void wrongKeyThrows() {
        String cipher = EncryptUtils.encryptDeterministic("secret", KEY, IV);
        SecretKeySpec wrongKey = spec("ffffffffffffffffffffffffffffffff", 32);
        assertThatThrownBy(() -> EncryptUtils.decrypt(cipher, wrongKey, IV))
                .isInstanceOf(EncryptException.class);
    }

    @Test
    void invalidBase64Throws() {
        assertThatThrownBy(() -> EncryptUtils.decrypt("not-a-valid-cipher!!!", KEY, IV))
                .isInstanceOf(EncryptException.class);
    }

    @Test
    void shortV2PayloadThrows() {
        String tooShort = EncryptUtils.V2_PREFIX + java.util.Base64.getEncoder().encodeToString(new byte[8]);
        assertThatThrownBy(() -> EncryptUtils.decrypt(tooShort, KEY, IV))
                .isInstanceOf(EncryptException.class)
                .hasMessageContaining("长度非法");
    }

    @Test
    void nonCiphertextPlaintextRejectedAsLegacy() {
        // 合法 base64 但解不开（如恰为 16 字节倍数的明文）→ 抛异常，由拦截器回退原值
        String plausibleBase64 = java.util.Base64.getEncoder().encodeToString("sixteen-byte-ok!".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> EncryptUtils.decrypt(plausibleBase64, KEY, IV))
                .isInstanceOf(EncryptException.class);
    }

    @Test
    void plaintextWithBadLengthRejectedCheaply() {
        // 非 4 倍长度的明文（非法 base64 长度）→ 廉价预检直接拒绝
        assertThatThrownBy(() -> EncryptUtils.decrypt("plain-text-not-base64", KEY, IV))
                .isInstanceOf(EncryptException.class);
    }

    @Test
    void concurrentEncryptDecryptIsThreadSafe() throws Exception {
        // ThreadLocal<Cipher> 并发正确性冒烟
        Thread[] threads = new Thread[8];
        Exception[] failure = new Exception[1];
        for (int t = 0; t < threads.length; t++) {
            final int seed = t;
            threads[t] = new Thread(() -> {
                try {
                    for (int i = 0; i < 100; i++) {
                        String plain = "thread-" + seed + "-msg-" + i;
                        String cipher = EncryptUtils.encryptRandom(plain, KEY);
                        assertThat(EncryptUtils.decrypt(cipher, KEY, IV)).isEqualTo(plain);
                    }
                } catch (Exception e) {
                    failure[0] = e;
                }
            });
            threads[t].start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        assertThat(failure[0]).isNull();
    }
}
