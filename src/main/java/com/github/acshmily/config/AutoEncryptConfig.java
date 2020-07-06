package com.github.acshmily.config;

import com.github.acshmily.interceptor.EncryptInterceptor;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

/**
 * @author: Huanghz
 * description: 自动注册方法
 * date:Created in 2:05 下午 2020/7/6
 * modifyBy:
 **/
@Configuration
@EnableConfigurationProperties(EncryptProperties.class)
@ConditionalOnProperty(prefix = "acshmily.encrypt", value = "enabled", havingValue = "true")
@EnableAspectJAutoProxy
public class AutoEncryptConfig {

    @Bean
    public EncryptInterceptor encryptInterceptor(){
        return new EncryptInterceptor();
    }
}
