package com.github.acshmily.utils;

import org.junit.jupiter.api.Test;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 盲索引单元测试：gram 切分、token 格式与确定性、连续片段匹配原理、normalize、密钥派生。
 */
class BlindIndexUtilsTest {

    private static final SecretKeySpec KEY = new SecretKeySpec(
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8), "HmacSHA256");

    // ---------- gram 切分 ----------

    @Test
    void slidingGramsByLength() {
        assertThat(BlindIndexUtils.gramsOfLength("abcd", 2)).containsExactly("ab", "bc", "cd");
        assertThat(BlindIndexUtils.gramsOfLength("abcd", 1)).containsExactly("a", "b", "c", "d");
        assertThat(BlindIndexUtils.gramsOfLength("a", 2)).isEmpty();
        assertThat(BlindIndexUtils.gramsOfLength("", 1)).isEmpty();
        // 中文按 UTF-16 字符切分（BMP 内单字即一个 gram）
        assertThat(BlindIndexUtils.gramsOfLength("张三丰", 2)).containsExactly("张三", "三丰");
    }

    @Test
    void normalizeTrimsAndLowercases() {
        assertThat(BlindIndexUtils.normalize("  HeLLo ")).isEqualTo("hello");
        assertThat(BlindIndexUtils.normalize("张三")).isEqualTo("张三");
    }

    // ---------- 索引值格式 ----------

    @Test
    void indexValueHasDelimitedSections() {
        String value = BlindIndexUtils.buildIndexValue("abcd", KEY);
        // 两段：2-gram 段 + ';' + 1-gram 段；段内 token 以逗号包裹
        String[] sections = value.split(";", -1);
        assertThat(sections).hasSize(2);

        String[] gram2Tokens = sections[0].split(",", -1);
        // 首尾为空（首尾补逗号），中间 3 个定宽 token
        assertThat(gram2Tokens[0]).isEmpty();
        assertThat(gram2Tokens[gram2Tokens.length - 1]).isEmpty();
        assertThat(sections[0]).matches(",[0-9a-f]{16},[0-9a-f]{16},[0-9a-f]{16},");

        assertThat(sections[1]).matches("(,[0-9a-f]{16}){4},");
    }

    @Test
    void tokenIsDeterministicAndFixedWidth() {
        String t1 = BlindIndexUtils.hmacToken("ab", KEY);
        String t2 = BlindIndexUtils.hmacToken("ab", KEY);
        assertThat(t1).isEqualTo(t2).hasSize(16).matches("[0-9a-f]{16}");
        // 不同 gram / 不同密钥 → 不同 token
        assertThat(BlindIndexUtils.hmacToken("ab", KEY)).isNotEqualTo(BlindIndexUtils.hmacToken("ba", KEY));
        SecretKeySpec other = new SecretKeySpec(
                "ffffffffffffffffffffffffffffffff".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        assertThat(BlindIndexUtils.hmacToken("ab", other)).isNotEqualTo(t1);
    }

    @Test
    void nullAndEmptyInputs() {
        assertThat(BlindIndexUtils.buildIndexValue(null, KEY)).isNull();
        assertThat(BlindIndexUtils.buildIndexValue("   ", KEY)).isEmpty();
        assertThat(BlindIndexUtils.toQueryToken(null, KEY)).isNull();
        // 空白关键词原样返回（Containing 查不到任何行，与明文语义一致）
        assertThat(BlindIndexUtils.toQueryToken("  ", KEY)).isEqualTo("  ");
    }

    // ---------- 连续片段匹配原理（核心正确性） ----------

    @Test
    void queryTokenIsContiguousSubstringOfIndexValue() {
        String plain = "13800001234";
        String index = BlindIndexUtils.buildIndexValue(plain, KEY);

        // 任意连续子串（前缀/中缀/后缀/整值）的查询片段必须被索引值包含
        assertThat(index).contains(BlindIndexUtils.toQueryToken("138", KEY));       // 前缀
        assertThat(index).contains(BlindIndexUtils.toQueryToken("00001", KEY));     // 中缀
        assertThat(index).contains(BlindIndexUtils.toQueryToken("1234", KEY));      // 后缀
        assertThat(index).contains(BlindIndexUtils.toQueryToken(plain, KEY));       // 整值
    }

    @Test
    void singleCharQueryHitsOneGramSection() {
        // 单字符走 1-gram 段
        String index = BlindIndexUtils.buildIndexValue("13800", KEY);
        assertThat(index).contains(BlindIndexUtils.toQueryToken("8", KEY));
        // 单字符原文：2-gram 段为空，仅 1-gram 段
        String single = BlindIndexUtils.buildIndexValue("x", KEY);
        assertThat(single).startsWith(";");
    }

    @Test
    void nonContiguousKeywordDoesNotMatch() {
        // "非连续"关键词（如跨位挑选字符）不应命中——其 2-gram 在原文中不存在
        String index = BlindIndexUtils.buildIndexValue("13800001234", KEY);
        String query = BlindIndexUtils.toQueryToken("1038", KEY); // 1,0,3,8 非原文连续子串
        assertThat(index).doesNotContain(query);
    }

    @Test
    void noFalsePositiveAcrossTokensOrSections() {
        // 构造两个索引值：B 的任意查询片段不得出现在 A 中（无共同子串时）
        String a = BlindIndexUtils.buildIndexValue("abcdefgh", KEY);
        String b = BlindIndexUtils.buildIndexValue("zzzzzzzz", KEY);
        assertThat(a).doesNotContain(BlindIndexUtils.toQueryToken("zz", KEY));
        assertThat(b).doesNotContain(BlindIndexUtils.toQueryToken("ab", KEY));
    }

    @Test
    void normalizeMakesSearchCaseAndPaddingInsensitive() {
        String index = BlindIndexUtils.buildIndexValue("  HeLLo  ", KEY);
        assertThat(index).isEqualTo(BlindIndexUtils.buildIndexValue("hello", KEY));
        assertThat(index).contains(BlindIndexUtils.toQueryToken("ELL", KEY));
    }

    @Test
    void chineseFuzzySearch() {
        String index = BlindIndexUtils.buildIndexValue("张三丰", KEY);
        assertThat(index).contains(BlindIndexUtils.toQueryToken("张三", KEY));
        assertThat(index).contains(BlindIndexUtils.toQueryToken("三丰", KEY));
        assertThat(index).contains(BlindIndexUtils.toQueryToken("三", KEY)); // 单字
        assertThat(index).doesNotContain(BlindIndexUtils.toQueryToken("李四", KEY));
    }

    // ---------- 密钥派生 ----------

    @Test
    void derivationIsStableAndIndependentFromTokenKey() {
        byte[] d1 = BlindIndexUtils.hmacSha256(
                "my-encrypt-key".getBytes(StandardCharsets.UTF_8),
                BlindIndexUtils.DERIVATION_MESSAGE.getBytes(StandardCharsets.UTF_8));
        byte[] d2 = BlindIndexUtils.hmacSha256(
                "my-encrypt-key".getBytes(StandardCharsets.UTF_8),
                BlindIndexUtils.DERIVATION_MESSAGE.getBytes(StandardCharsets.UTF_8));
        assertThat(d1).isEqualTo(d2).hasSize(32);

        // 不同 encryptKey 派生不同 indexKey
        byte[] d3 = BlindIndexUtils.hmacSha256(
                "another-key".getBytes(StandardCharsets.UTF_8),
                BlindIndexUtils.DERIVATION_MESSAGE.getBytes(StandardCharsets.UTF_8));
        assertThat(d3).isNotEqualTo(d1);
    }

    @Test
    void storageAmplificationMatchesDocumentedBound() {
        // 每字符约 2 token × 17 字符 ≈ 34：文档承诺的量级校验（防意外膨胀）
        String value = BlindIndexUtils.buildIndexValue("12345678", KEY);
        // 8 字符 → 7 个 2-gram + 8 个 1-gram = 15 token，每 token 16 hex + 1 逗号，
        // 外加两段各自的段尾逗号与一个段分隔符
        assertThat(value).hasSize(15 * 17 + 2 + 1);
    }
}
