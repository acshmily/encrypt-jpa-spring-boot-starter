package com.github.acshmily.utils;

import com.github.acshmily.annotation.BlindIndex;
import com.github.acshmily.annotation.BlindIndexParam;
import com.github.acshmily.annotation.Encrypt;
import com.github.acshmily.annotation.EncryptParam;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 反射缓存单元测试：层级扫描、负缓存、参数下标（1.x 错位缺陷回归）、random 标志。
 */
class EncryptCacheContainerTest {

    private final EncryptCacheContainer container = new EncryptCacheContainer();

    static class Base {
        @Encrypt
        private String baseField;
        private String basePlain;
    }

    static class Child extends Base {
        @Encrypt
        private String childField;
        @Encrypt(random = true)
        private String randomField;
        private static String staticField;
        private Integer notAString;
        @Encrypt
        private Integer annotatedButNotString;
    }

    static class NoEncrypt {
        private String plain;
    }

    static class Indexed extends Base {
        @Encrypt
        private String phone;

        @BlindIndex("phone")
        private String phoneBidx;

        @BlindIndex("baseField")
        private String baseNoteBidx;   // 源字段在父类
    }

    static class BadSourceName {
        @BlindIndex("no-such-field")
        private String broken;
    }

    static class NonStringShadow {
        private String phone;

        @BlindIndex("phone")
        private Integer phoneBidx;   // 影子字段非 String
    }

    static class NonStringSource {
        private Integer phoneCount;

        @BlindIndex("phoneCount")
        private String shadow;       // 源字段非 String
    }

    abstract static class RepoFacade {
        public abstract void findByPhone(@EncryptParam String phone);

        public abstract void findByIdAndPhone(Long id, @EncryptParam String phone);

        public abstract void findByTwo(@EncryptParam String a, @EncryptParam String b);

        public abstract void noAnnotation(String a, Long b);

        public abstract void findByBidx(@BlindIndexParam String keyword);

        public abstract void findByTwoBidx(@BlindIndexParam String a, @BlindIndexParam String b);

        public abstract void findByMixed(Long id, @EncryptParam String a, @BlindIndexParam String b);

        public abstract void findByConflict(@EncryptParam @BlindIndexParam String broken);
    }

    @Test
    void scansFieldsAcrossClassHierarchy() {
        List<EncryptField> fields = container.encryptFieldsOf(Child.class);

        assertThat(fields).extracting(f -> f.getField().getName())
                .containsExactlyInAnyOrder("childField", "randomField", "baseField");
        // 非 String 字段（即使带 @Encrypt）、静态字段、无注解字段都被过滤
        assertThat(fields).extracting(f -> f.getField().getName())
                .doesNotContain("annotatedButNotString", "staticField", "basePlain", "notAString");
    }

    @Test
    void cachesRandomFlagPerField() {
        List<EncryptField> fields = container.encryptFieldsOf(Child.class);
        EncryptField randomField = fields.stream().filter(f -> f.getField().getName().equals("randomField")).findFirst().get();
        EncryptField childField = fields.stream().filter(f -> f.getField().getName().equals("childField")).findFirst().get();
        assertThat(randomField.isRandom()).isTrue();
        assertThat(childField.isRandom()).isFalse();
    }

    @Test
    void returnsEmptyListAsNegativeCache() {
        assertThat(container.encryptFieldsOf(NoEncrypt.class)).isEmpty();
        // 二次调用命中负缓存（同一空实例）
        List<EncryptField> first = container.encryptFieldsOf(NoEncrypt.class);
        List<EncryptField> second = container.encryptFieldsOf(NoEncrypt.class);
        assertThat(first).isSameAs(second);
    }

    @Test
    void returnsCachedListInstance() {
        assertThat(container.encryptFieldsOf(Child.class)).isSameAs(container.encryptFieldsOf(Child.class));
    }

    @Test
    void scansEncryptParamIndexesCorrectly() throws Exception {
        Method byPhone = RepoFacade.class.getMethod("findByPhone", String.class);
        assertThat(container.encryptParamIndexesOf(byPhone)).containsExactly(0);

        // 1.x 错位缺陷核心场景：注解不在下标 0
        Method byIdAndPhone = RepoFacade.class.getMethod("findByIdAndPhone", Long.class, String.class);
        assertThat(container.encryptParamIndexesOf(byIdAndPhone)).containsExactly(1);
        // 二次调用（缓存命中）下标不变
        assertThat(container.encryptParamIndexesOf(byIdAndPhone)).containsExactly(1);

        Method byTwo = RepoFacade.class.getMethod("findByTwo", String.class, String.class);
        assertThat(container.encryptParamIndexesOf(byTwo)).containsExactly(0, 1);
    }

    @Test
    void noEncryptParamReturnsEmptyNegativeCache() throws Exception {
        Method noAnnotation = RepoFacade.class.getMethod("noAnnotation", String.class, Long.class);
        assertThat(container.encryptParamIndexesOf(noAnnotation)).isEmpty();
        assertThat(container.encryptParamIndexesOf(noAnnotation).length).isZero();
    }

    @Test
    void fieldsAreAccessibleAfterScan() throws Exception {
        container.encryptFieldsOf(Child.class);
        Child child = new Child();
        List<EncryptField> fields = container.encryptFieldsOf(Child.class);
        for (EncryptField field : fields) {
            if (field.getField().getName().equals("childField")) {
                field.getField().set(child, "written");
                assertThat(field.getField().get(child)).isEqualTo("written");
            }
        }
    }

    // ---------- 盲索引绑定缓存 ----------

    @Test
    void scansBlindIndexBindingsWithSuperclassSource() {
        List<BlindIndexBinding> bindings = container.blindIndexBindingsOf(Indexed.class);

        assertThat(bindings).hasSize(2);
        assertThat(bindings).extracting(b -> b.getIndexField().getName())
                .containsExactlyInAnyOrder("phoneBidx", "baseNoteBidx");
        // 源字段解析正确（含父类字段）
        assertThat(bindings).extracting(b -> b.getSourceField().getName())
                .containsExactlyInAnyOrder("phone", "baseField");
        // 影子字段不是加密目标（不进 encryptFieldsOf）
        assertThat(container.encryptFieldsOf(Indexed.class))
                .extracting(f -> f.getField().getName())
                .doesNotContain("phoneBidx", "baseNoteBidx");
    }

    @Test
    void blindIndexNegativeCache() {
        assertThat(container.blindIndexBindingsOf(NoEncrypt.class)).isEmpty();
        assertThat(container.blindIndexBindingsOf(NoEncrypt.class))
                .isSameAs(container.blindIndexBindingsOf(NoEncrypt.class));
    }

    @Test
    void unknownSourceFieldFailsFast() {
        assertThatThrownBy(() -> container.blindIndexBindingsOf(BadSourceName.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no-such-field");
    }

    @Test
    void nonStringShadowOrSourceFailsFast() {
        assertThatThrownBy(() -> container.blindIndexBindingsOf(NonStringShadow.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不是 String 类型");
        assertThatThrownBy(() -> container.blindIndexBindingsOf(NonStringSource.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不是 String 类型");
    }

    // ---------- @BlindIndexParam 参数缓存 ----------

    @Test
    void scansBlindIndexParamIndexes() throws Exception {
        Method byBidx = RepoFacade.class.getMethod("findByBidx", String.class);
        assertThat(container.blindIndexParamIndexesOf(byBidx)).containsExactly(0);
        assertThat(container.blindIndexParamIndexesOf(byBidx)).containsExactly(0);

        Method byTwo = RepoFacade.class.getMethod("findByTwoBidx", String.class, String.class);
        assertThat(container.blindIndexParamIndexesOf(byTwo)).containsExactly(0, 1);

        // 与 @EncryptParam 并存（不同参数）
        Method mixed = RepoFacade.class.getMethod("findByMixed", Long.class, String.class, String.class);
        assertThat(container.encryptParamIndexesOf(mixed)).containsExactly(1);
        assertThat(container.blindIndexParamIndexesOf(mixed)).containsExactly(2);

        // 无注解方法负缓存
        Method none = RepoFacade.class.getMethod("noAnnotation", String.class, Long.class);
        assertThat(container.blindIndexParamIndexesOf(none)).isEmpty();
    }

    @Test
    void conflictingParamAnnotationsFailFast() throws Exception {
        Method conflict = RepoFacade.class.getMethod("findByConflict", String.class);
        assertThatThrownBy(() -> container.blindIndexParamIndexesOf(conflict))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("二选一");
    }
}
