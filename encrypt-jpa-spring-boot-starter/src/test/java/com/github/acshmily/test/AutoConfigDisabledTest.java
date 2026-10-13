package com.github.acshmily.test;

import com.github.acshmily.interceptor.EncryptInterceptor;
import com.github.acshmily.service.JpaEncryptService;
import com.github.acshmily.test.domain.User;
import com.github.acshmily.test.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * enabled=false 时自动配置整体退出的异常路径。
 */
@SpringBootTest(properties = "acshmily.encrypt.enabled=false")
class AutoConfigDisabledTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void noEncryptionBeansAndNoInterception() {
        assertThat(applicationContext.getBeansOfType(EncryptInterceptor.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(JpaEncryptService.class)).isEmpty();

        User saved = userRepository.save(new User("plain-mode", "13900000001", "plain@example.com", "plain-note"));
        String rawPhone = jdbcTemplate.queryForObject(
                "select phone from users where id = ?", String.class, saved.getId());
        // 不加密：明文直通
        assertThat(rawPhone).isEqualTo("13900000001");
    }
}
