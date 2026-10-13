package com.github.acshmily.config;

import com.github.acshmily.interceptor.EncryptInterceptor;
import com.github.acshmily.service.JpaEncryptService;
import com.github.acshmily.service.impl.JpaEncryptServiceImpl;
import com.github.acshmily.utils.EncryptCacheContainer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot 3.x（jakarta 命名空间，Java 17+）自动配置。
 *
 * <p>启用条件：{@code acshmily.encrypt.enabled=true} 且类路径存在 Spring Data
 * {@code Repository}。通过 {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * 注册（Boot 2.7+/3.x 机制），javax 版本见 encrypt-jpa-spring-boot-starter 模块。</p>
 *
 * <p>所有 Bean 带 {@link ConditionalOnMissingBean}，使用方可自行覆盖。</p>
 *
 * @author Huanghz
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.data.repository.Repository")
@ConditionalOnProperty(prefix = "acshmily.encrypt", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(EncryptProperties.class)
public class AutoEncryptConfig {

    @Bean
    @ConditionalOnMissingBean
    public EncryptCacheContainer encryptCacheContainer() {
        return new EncryptCacheContainer();
    }

    @Bean
    @ConditionalOnMissingBean
    public JpaEncryptService jpaEncryptService(EncryptProperties encryptProperties) {
        return new JpaEncryptServiceImpl(encryptProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    public EncryptInterceptor encryptInterceptor(EncryptProperties encryptProperties,
                                                 EncryptCacheContainer encryptCacheContainer) {
        return new EncryptInterceptor(encryptProperties, encryptCacheContainer);
    }
}
