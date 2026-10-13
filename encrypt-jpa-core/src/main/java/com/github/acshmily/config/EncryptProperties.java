package com.github.acshmily.config;

import com.github.acshmily.utils.BlindIndexUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * 加密配置（前缀 {@code acshmily.encrypt}）。
 *
 * <p>密钥派生规则与 1.x 完全一致（保证 v1 存量密文可解）：
 * {@code encryptKey} 不足 32 字符时左侧补 {@code '0'} 至 32 字节 AES-256 密钥；
 * {@code encryptIv} 不足 16 字符时左侧补 {@code '0'} 至 16 字节静态 IV。</p>
 *
 * <p>2.0.0 变化：{@link #getKey()} / {@link #getIv()} 返回初始化时一次性构建的实例
 * （1.x 每次调用重建）；启用状态下配置缺失或超长会在启动时快速失败并给出明确报错
 * （1.x 会把 null 格式化成字面量 "null" 静默生成垃圾密钥）。</p>
 *
 * @author Huanghz
 */
@ConfigurationProperties(prefix = "acshmily.encrypt")
public class EncryptProperties implements InitializingBean {

    /** 是否开启加密插件 */
    private Boolean enabled;

    /** 加密密钥，长度 1~32 字符，不足左侧补 '0' 至 32 字节 */
    private String encryptKey;

    /** 加密偏移量（v1 静态 IV），长度 1~16 字符，不足左侧补 '0' 至 16 字节 */
    private String encryptIv;

    /**
     * 盲索引独立密钥（2.1.0，可选）。未配置时从 encryptKey 派生：
     * HMAC-SHA256(encryptKey, "encrypt-jpa-blind-index-v1") 全 32 字节。
     * 派生规则勿改动（更换会使存量索引全部失效）。
     */
    private String indexKey;

    /** 32 字节 AES 密钥，初始化时构建一次 */
    private SecretKeySpec key;

    /** 16 字节静态 IV（v1 兼容解密用），初始化时构建一次 */
    private IvParameterSpec iv;

    /** 盲索引 HMAC 密钥，初始化时构建一次 */
    private SecretKeySpec blindIndexKey;

    @Override
    public void afterPropertiesSet() {
        // 未启用时不校验（配置 bean 只在启用条件下注册，此处为双保险）
        if (!Boolean.TRUE.equals(enabled)) {
            return;
        }
        if (!StringUtils.hasText(encryptKey)) {
            throw new IllegalStateException("acshmily.encrypt.encrypt-key 未配置：启用加密时必须提供 1~32 字符的密钥");
        }
        if (encryptKey.length() > 32) {
            throw new IllegalStateException("acshmily.encrypt.encrypt-key 长度为 " + encryptKey.length()
                    + "，超过 32 字符上限（不足 32 位会自动左侧补 '0'）");
        }
        if (!StringUtils.hasText(encryptIv)) {
            throw new IllegalStateException("acshmily.encrypt.encrypt-iv 未配置：启用加密时必须提供 1~16 字符的 IV");
        }
        if (encryptIv.length() > 16) {
            throw new IllegalStateException("acshmily.encrypt.encrypt-iv 长度为 " + encryptIv.length()
                    + "，超过 16 字符上限（不足 16 位会自动左侧补 '0'）");
        }
        this.key = new SecretKeySpec(padZero(encryptKey, 32).getBytes(StandardCharsets.UTF_8), "AES");
        this.iv = new IvParameterSpec(padZero(encryptIv, 16).getBytes(StandardCharsets.UTF_8));
        this.blindIndexKey = deriveBlindIndexKey();
    }

    /**
     * 盲索引密钥：显式配置了 indexKey 则直接使用；否则以 encryptKey 为密钥派生。
     * 派生公式为兼容性契约（勿改动，更换会使存量索引全部失效）：
     * HMAC-SHA256(key = encryptKey 原始字节, msg = "encrypt-jpa-blind-index-v1")。
     */
    private SecretKeySpec deriveBlindIndexKey() {
        byte[] material;
        if (StringUtils.hasText(indexKey)) {
            material = indexKey.getBytes(StandardCharsets.UTF_8);
        } else {
            material = BlindIndexUtils.hmacSha256(
                    encryptKey.getBytes(StandardCharsets.UTF_8),
                    BlindIndexUtils.DERIVATION_MESSAGE.getBytes(StandardCharsets.UTF_8));
        }
        return new SecretKeySpec(material, "HmacSHA256");
    }

    /**
     * 与 1.x 的 {@code String.format("%" + len + "s", s).replace(" ", "0")} 逐字节等价：
     * 左侧补 '0'，且值内部原本包含的空格同样被替换为 '0'（历史行为，勿改动，否则 v1 密文解不开）。
     */
    private static String padZero(String value, int length) {
        return String.format("%" + length + "s", value).replace(" ", "0");
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getEncryptKey() {
        return encryptKey;
    }

    public void setEncryptKey(String encryptKey) {
        this.encryptKey = encryptKey;
    }

    public String getEncryptIv() {
        return encryptIv;
    }

    public void setEncryptIv(String encryptIv) {
        this.encryptIv = encryptIv;
    }

    public String getIndexKey() {
        return indexKey;
    }

    public void setIndexKey(String indexKey) {
        this.indexKey = indexKey;
    }

    /** 盲索引 HMAC 密钥，容器启动时构建一次 */
    public SecretKeySpec getBlindIndexKey() {
        return blindIndexKey;
    }

    /** AES 密钥，容器启动时构建一次 */
    public SecretKeySpec getKey() {
        return key;
    }

    /** v1 兼容解密用的静态 IV，容器启动时构建一次 */
    public IvParameterSpec getIv() {
        return iv;
    }
}
