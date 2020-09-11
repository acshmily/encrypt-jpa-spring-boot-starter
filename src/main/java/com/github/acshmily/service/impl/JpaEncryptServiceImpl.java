package com.github.acshmily.service.impl;

import com.github.acshmily.config.EncryptProperties;
import com.github.acshmily.service.JpaEncryptService;
import com.github.acshmily.utils.EncryptUtils;
import com.sun.istack.NotNull;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.ShortBufferException;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;

/**
 * @author: Huanghz
 * description: 实现类
 * date:Created in 1:37 下午 2020/9/10
 * modifyBy:
 **/
public class JpaEncryptServiceImpl implements JpaEncryptService {
    /**
     * 加密方法
     *
     * @param rawString
     * @return
     */
    @Override
    public String encrypt(@NotNull String rawString) {
        if(StringUtils.isBlank(rawString)){
            throw new IllegalArgumentException("加密原文不能为空");
        }
        log.debug("收到原文:{}加密请求",rawString);
        String re = "";
        try {
            re = EncryptUtils.encrypt(rawString,encryptProperties.getKey(),encryptProperties.getIv());
            log.debug("加密后为:{}",re);

        } catch (Exception e) {
            log.error("发生异常:{}",e.getMessage());
        }
        return re;
    }

    /**
     * 解密方法
     *
     * @param encryptString
     * @return
     */
    @Override
    public String deEncrypt(@NotNull String encryptString) {
        if(StringUtils.isBlank(encryptString)){
            throw new IllegalArgumentException("密文不能为空");
        }
        log.debug("收到密文:{}解密请求",encryptString);
        String re = "";
        try{
            re = EncryptUtils.deEncrypt(encryptString,encryptProperties.getKey(),encryptProperties.getIv());
            log.debug("解密后为:{}",re);
        }catch (Exception e){
            log.error("发生异常:{}",e.getMessage());

        }
        return re;
    }
    @Resource
    private EncryptProperties encryptProperties;
    private static final Logger log = LoggerFactory.getLogger(JpaEncryptServiceImpl.class);
}
