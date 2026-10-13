package com.github.acshmily.service.impl;

import com.github.acshmily.config.EncryptProperties;
import com.github.acshmily.service.JpaEncryptService;
import com.github.acshmily.utils.EncryptUtils;

/**
 * {@link JpaEncryptService} 默认实现。
 *
 * <p>2.0.0 变化：构造器注入（替代字段注入，同时消除对 javax/jakarta 注解的依赖）；
 * 加解密失败抛出 {@code EncryptException} 而非吞掉异常返回空字符串。</p>
 *
 * @author Huanghz
 */
public class JpaEncryptServiceImpl implements JpaEncryptService {

    private final EncryptProperties encryptProperties;

    public JpaEncryptServiceImpl(EncryptProperties encryptProperties) {
        this.encryptProperties = encryptProperties;
    }

    @Override
    public String encrypt(String rawString) {
        if (isBlank(rawString)) {
            throw new IllegalArgumentException("加密原文不能为空");
        }
        return EncryptUtils.encryptDeterministic(rawString, encryptProperties.getKey(), encryptProperties.getIv());
    }

    @Override
    public String deEncrypt(String encryptString) {
        if (isBlank(encryptString)) {
            throw new IllegalArgumentException("密文不能为空");
        }
        return EncryptUtils.decrypt(encryptString, encryptProperties.getKey(), encryptProperties.getIv());
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
