package com.github.acshmily.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 配置属性单元测试：补位派生、fail-fast 校验、key/iv 只构建一次（1.x 每次调用重建）。
 */
class EncryptPropertiesTest {

    private EncryptProperties properties(String key, String iv) {
        EncryptProperties properties = new EncryptProperties();
        properties.setEnabled(true);
        properties.setEncryptKey(key);
        properties.setEncryptIv(iv);
        properties.afterPropertiesSet();
        return properties;
    }

    @Test
    void shortKeyAndIvAreLeftPaddedWithZero() {
        // 1.x 派生规则：左侧补 '0'（%32s 右对齐后空格替换）
        EncryptProperties properties = properties("abc", "xyz");

        byte[] keyBytes = properties.getKey().getEncoded();
        assertThat(keyBytes).hasSize(32);
        assertThat(new String(keyBytes)).isEqualTo("00000000000000000000000000000abc");

        byte[] ivBytes = properties.getIv().getIV();
        assertThat(ivBytes).hasSize(16);
        assertThat(new String(ivBytes)).isEqualTo("0000000000000xyz");
    }

    @Test
    void exactLengthKeyAndIvAreUsedAsIs() {
        EncryptProperties properties = properties(
                "0123456789abcdef0123456789abcdef", "1234567890123456");
        assertThat(new String(properties.getKey().getEncoded()))
                .isEqualTo("0123456789abcdef0123456789abcdef");
        assertThat(new String(properties.getIv().getIV())).isEqualTo("1234567890123456");
    }

    @Test
    void keyAndIvInstancesAreCached() {
        // 1.x 每次 getKey()/getIv() 都 new；2.0.0 返回同一实例
        EncryptProperties properties = properties("k", "i");
        assertThat(properties.getKey()).isSameAs(properties.getKey());
        assertThat(properties.getIv()).isSameAs(properties.getIv());
    }

    @Test
    void keyWithSpacesFollowsLegacyDerivation() {
        // 历史怪癖锁定：值内部的空格同样被替换为 '0'（改动会解不开 v1 存量密文）
        EncryptProperties properties = properties("a b", "i v");
        assertThat(new String(properties.getKey().getEncoded()))
                .isEqualTo("00000000000000000000000000000a0b");
        assertThat(new String(properties.getIv().getIV())).isEqualTo("0000000000000i0v");
    }

    @Test
    void disabledSkipsValidation() {
        EncryptProperties properties = new EncryptProperties();
        properties.setEnabled(false);
        properties.afterPropertiesSet(); // 不抛异常
        assertThat(properties.getKey()).isNull();
    }

    @Test
    void missingKeyFailsFast() {
        assertThatThrownBy(() -> properties(null, "iv"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("encrypt-key");
        assertThatThrownBy(() -> properties("  ", "iv"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("encrypt-key");
    }

    @Test
    void oversizedKeyFailsFast() {
        assertThatThrownBy(() -> properties("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "iv")) // 33 字符
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }

    @Test
    void missingOrOversizedIvFailsFast() {
        assertThatThrownBy(() -> properties("key", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("encrypt-iv");
        assertThatThrownBy(() -> properties("key", "12345678901234567")) // 17 字符
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("16");
    }

    // ---------- 盲索引密钥 ----------

    @Test
    void blindIndexKeyDerivedFromEncryptKeyAndCached() {
        EncryptProperties p = properties("my-key", "my-iv");
        assertThat(p.getBlindIndexKey()).isNotNull().isSameAs(p.getBlindIndexKey());
        // 派生稳定：同 encryptKey → 同 indexKey
        assertThat(properties("my-key", "my-iv").getBlindIndexKey().getEncoded())
                .isEqualTo(p.getBlindIndexKey().getEncoded());
        // 不同 encryptKey → 不同 indexKey
        assertThat(properties("other-key", "my-iv").getBlindIndexKey().getEncoded())
                .isNotEqualTo(p.getBlindIndexKey().getEncoded());
    }

    @Test
    void explicitIndexKeyOverridesDerivation() throws Exception {
        EncryptProperties p = new EncryptProperties();
        p.setEnabled(true);
        p.setEncryptKey("my-key");
        p.setEncryptIv("my-iv");
        p.setIndexKey("explicit-index-key");
        p.afterPropertiesSet();

        assertThat(p.getBlindIndexKey().getEncoded())
                .isEqualTo("explicit-index-key".getBytes("UTF-8"));
    }
}
