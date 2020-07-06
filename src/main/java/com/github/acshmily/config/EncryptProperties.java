package com.github.acshmily.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.util.Arrays;
import java.util.List;

import static com.github.acshmily.utils.EncryptUtils.getUTF8Bytes;

/**
 * @Author: Huanghz
 * @Description: 配置类
 * @Date:Created in 4:11 下午 2020/7/2
 * @ModifyBy:
 **/
@ConfigurationProperties(prefix = "acshmily.encrypt")
public class EncryptProperties {
    /**
     * 是否开启加密插件
     */
    private Boolean enabled;
    /**
     * 加密字符串
     */
    private String encryptKey;
    /**
     * 加密便宜量
     */
    private String encryptIv;
    // 32字节长度的秘钥，也就是256位
    private SecretKeySpec key;
    // 固定16字节长度
    private IvParameterSpec iv;

    public String getEncryptKey() {
        return encryptKey;
    }

    public void setEncryptKey(String encryptKey) {
        this.encryptKey = encryptKey;
    }

    public SecretKeySpec getKey() {
        return new SecretKeySpec(getUTF8Bytes(String.format("%32s", getEncryptKey()).replace(" ", "0")),"AES");
    }

    public IvParameterSpec getIv() {
        return  new IvParameterSpec(getUTF8Bytes(String.format("%16s", getEncryptIv()).replace(" ", "0")));
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getEncryptIv() {
        return encryptIv;
    }

    public void setEncryptIv(String encryptIv) {
        this.encryptIv = encryptIv;
    }
}
