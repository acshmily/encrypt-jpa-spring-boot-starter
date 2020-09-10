package com.github.acshmily.service;

import com.sun.istack.NotNull;

/**
 * @author: Huanghz
 * description: Jpa加密解密接口
 * date:Created in 1:36 下午 2020/9/10
 * modifyBy:
 **/
public interface JpaEncryptService {
    /**
     * 加密方法
     * @param rawString
     * @return
     */
    String encrypt(@NotNull String rawString);

    /**
     * 解密方法
     * @param encryptString
     * @return
     */
    String deEncrypt(@NotNull String encryptString);

}
