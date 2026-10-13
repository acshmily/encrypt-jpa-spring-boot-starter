package com.github.acshmily.exception;

/**
 * 加密/解密失败时抛出的运行时异常。
 * <p>
 * 2.0.0 起 {@code JpaEncryptService} 加解密失败会抛出本异常（1.x 曾静默吞掉并返回空字符串）。
 *
 * @author Huanghz
 */
public class EncryptException extends RuntimeException {

    public EncryptException(String message) {
        super(message);
    }

    public EncryptException(String message, Throwable cause) {
        super(message, cause);
    }
}
