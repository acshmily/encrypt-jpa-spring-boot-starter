package com.github.acshmily.config;

import com.github.acshmily.interceptor.EncryptInterceptor;
import com.github.acshmily.service.JpaEncryptService;
import com.github.acshmily.service.impl.JpaEncryptServiceImpl;
import com.github.acshmily.utils.EncryptCacheContainer;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
        log.info("数据库加密拦截器开始初始化");
        return new EncryptInterceptor();
    }
    @Bean
    public JpaEncryptService jpaEncryptService(){
        log.info("数据库加密服务类开始初始化");
        return new JpaEncryptServiceImpl();
    }
    @Bean
    public EncryptCacheContainer encryptCacheContainer(){
        log.info("数据库加密缓存服务类开始初始化");
        return new EncryptCacheContainer();
    }
    private static final Logger log = LoggerFactory.getLogger(AutoEncryptConfig.class);
}
