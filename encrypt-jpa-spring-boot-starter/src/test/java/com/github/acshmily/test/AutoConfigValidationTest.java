package com.github.acshmily.test;

import com.github.acshmily.config.AutoEncryptConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 配置校验 fail-fast 异常路径：1.x 在 key 缺失/超长时会静默生成垃圾密钥（含字面量 "null"）。
 */
class AutoConfigValidationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AutoEncryptConfig.class));

    @Test
    void missingKeyFailsFast() {
        runner.withPropertyValues("acshmily.encrypt.enabled=true", "acshmily.encrypt.encrypt-iv=1234567890123456")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("encrypt-key");
                });
    }

    @Test
    void oversizedKeyFailsFast() {
        runner.withPropertyValues("acshmily.encrypt.enabled=true",
                        "acshmily.encrypt.encrypt-key=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", // 34 chars
                        "acshmily.encrypt.encrypt-iv=1234567890123456")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("超过 32 字符");
                });
    }

    @Test
    void missingIvFailsFast() {
        runner.withPropertyValues("acshmily.encrypt.enabled=true",
                        "acshmily.encrypt.encrypt-key=0123456789abcdef0123456789abcdef")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("encrypt-iv");
                });
    }

    @Test
    void shortKeyAndIvArePadded() {
        // 不足长度自动左侧补 '0'（与 1.x 派生规则一致）
        runner.withPropertyValues("acshmily.encrypt.enabled=true",
                        "acshmily.encrypt.encrypt-key=short-key",
                        "acshmily.encrypt.encrypt-iv=short-iv")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(com.github.acshmily.config.EncryptProperties.class).getKey()).isNotNull();
                });
    }
}
