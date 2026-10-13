package com.github.acshmily.service;

/**
 * JPA 加密解密服务，可直接注入使用（如需在非仓库入口处手工加解密）。
 *
 * @author Huanghz
 */
public interface JpaEncryptService {

    /**
     * 加密方法（v2 格式，随机 IV）。
     *
     * @param rawString 明文，不可为空白
     * @return 带 {@code enc:v2:} 前缀的密文
     * @throws IllegalArgumentException 明文为空白
     * @throws com.github.acshmily.exception.EncryptException 加密失败（2.0.0 起不再吞异常返回空串）
     */
    String encrypt(String rawString);

    /**
     * 解密方法，自动识别 v2 / v1（存量）格式。
     *
     * @param encryptString 密文，不可为空白
     * @return 明文
     * @throws IllegalArgumentException 密文为空白
     * @throws com.github.acshmily.exception.EncryptException 解密失败（2.0.0 起不再吞异常返回空串）
     */
    String deEncrypt(String encryptString);
}
