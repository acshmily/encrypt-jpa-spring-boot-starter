package com.github.acshmily.utils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

/**
 * 盲索引工具（2.1.0 新增）：顺序 n-gram + 连续片段 HMAC 匹配。
 *
 * <p><b>原理</b>：密文因雪崩效应无法 LIKE，改为在影子列存储原文 gram 的 HMAC。
 * 索引值按原文顺序拼接，而查询关键词是原文连续子串时其 gram 序列在拼接串中必然
 * 连续出现，因此单个 LIKE 参数即可统一支持前缀/中缀/后缀/整值模糊匹配。</p>
 *
 * <p><b>索引列格式</b>（两段，段内逗号分隔，首尾补逗号，段间分号）：</p>
 * <pre>
 * ,H(g2_1),H(g2_2),...,;,H(g1_1),H(g1_2),...,
 * </pre>
 * <p>第一段为全部 2-gram（按原文字符顺序滑动切分，长度不足 2 的原文无此段），
 * 第二段为全部 1-gram。查询关键词长度 ≥2 时转换为 2-gram 连续片段、长度为 1 时
 * 转换为单个 1-gram token。token = HMAC-SHA256(indexKey, gram) 前 8 字节的
 * 小写 hex（16 字符，64-bit）。token 为定宽 hex（{@code [0-9a-f]}），
 * 分隔符 {@code ,} 与 {@code ;} 不在其中，保证 {@code LIKE '%,token,%'} 无误配。</p>
 *
 * <p>归一化规则：{@code trim().toLowerCase()}（英文大小写不敏感，中文无影响）。
 * 按 UTF-16 字符切分（BMP 内中文单字即一个 gram；emoji 代理对会被拆开，
 * 该场景匹配按代理对字符粒度进行）。</p>
 *
 * @author Huanghz
 */
public final class BlindIndexUtils {

    /**
     * 未显式配置 indexKey 时的派生消息（兼容性契约，勿改动——更换会使存量索引全部失效）：
     * indexKey = HMAC-SHA256(key = encryptKey 原始字节, msg = 本常量)。
     */
    public static final String DERIVATION_MESSAGE = "encrypt-jpa-blind-index-v1";

    private static final char SEPARATOR = ',';
    private static final char SECTION_SEPARATOR = ';';
    private static final int TOKEN_BYTES = 8;
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    /** Mac 非线程安全，按线程复用；密钥每次 init 成本可忽略 */
    private static final ThreadLocal<Mac> MAC_HOLDER = ThreadLocal.withInitial(() -> {
        try {
            return Mac.getInstance("HmacSHA256");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("JVM 不支持 HmacSHA256", e);
        }
    });

    private BlindIndexUtils() {
    }

    /**
     * 写入侧：由明文构建索引列值。null/空白原文返回 null/""（查询自然不命中）。
     */
    public static String buildIndexValue(String plainText, SecretKeySpec indexKey) {
        if (plainText == null) {
            return null;
        }
        String normalized = normalize(plainText);
        if (normalized.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(normalized.length() * 36 + 4);
        appendGramSection(sb, gramsOfLength(normalized, 2), indexKey);
        sb.append(SECTION_SEPARATOR);
        appendGramSection(sb, gramsOfLength(normalized, 1), indexKey);
        return sb.toString();
    }

    /**
     * 查询侧：把关键词转换为 LIKE 匹配片段（带首尾分隔符，配合 Containing 使用）。
     * 长度 ≥2 的关键词转为 2-gram 连续片段；单字符关键词转为 1-gram token。
     * null/空白原样返回（由调用方决定语义）。
     */
    public static String toQueryToken(String keyword, SecretKeySpec indexKey) {
        if (keyword == null) {
            return null;
        }
        String normalized = normalize(keyword);
        if (normalized.isEmpty()) {
            return keyword;
        }
        StringBuilder sb = new StringBuilder(64);
        appendGramSection(sb, gramsOfLength(normalized, Math.min(2, normalized.length())), indexKey);
        return sb.toString();
    }

    /** trim + 小写归一化（写入与查询两侧必须一致，勿单独修改） */
    static String normalize(String value) {
        return value.trim().toLowerCase();
    }

    /** 长度为 n 的滑动 gram（按 UTF-16 字符）；原文短于 n 时返回空列表 */
    static List<String> gramsOfLength(String normalized, int n) {
        List<String> grams = new ArrayList<>(normalized.length());
        for (int i = 0; i + n <= normalized.length(); i++) {
            grams.add(normalized.substring(i, i + n));
        }
        return grams;
    }

    private static void appendGramSection(StringBuilder sb, List<String> grams, SecretKeySpec indexKey) {
        for (String gram : grams) {
            sb.append(SEPARATOR).append(hmacToken(gram, indexKey));
        }
        if (!grams.isEmpty()) {
            sb.append(SEPARATOR);
        }
    }

    /** HMAC-SHA256，返回完整 32 字节摘要（用于密钥派生；token 计算用截断版本） */
    public static byte[] hmacSha256(byte[] keyBytes, byte[] message) {
        try {
            Mac mac = MAC_HOLDER.get();
            mac.init(new SecretKeySpec(keyBytes, "HmacSHA256"));
            return mac.doFinal(message);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC 计算失败", e);
        }
    }

    /** HMAC-SHA256 截断 8 字节 → 16 个小写 hex 字符 */
    static String hmacToken(String gram, SecretKeySpec indexKey) {
        try {
            Mac mac = MAC_HOLDER.get();
            mac.init(indexKey);
            byte[] digest = mac.doFinal(gram.getBytes(StandardCharsets.UTF_8));
            char[] hex = new char[TOKEN_BYTES * 2];
            for (int i = 0; i < TOKEN_BYTES; i++) {
                int b = digest[i] & 0xFF;
                hex[i * 2] = HEX[b >>> 4];
                hex[i * 2 + 1] = HEX[b & 0xF];
            }
            return new String(hex);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC 计算失败", e);
        }
    }
}
