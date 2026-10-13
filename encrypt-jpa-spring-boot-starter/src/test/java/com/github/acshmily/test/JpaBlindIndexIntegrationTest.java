package com.github.acshmily.test;

import com.github.acshmily.config.EncryptProperties;
import com.github.acshmily.test.domain.User;
import com.github.acshmily.test.repo.UserRepository;
import com.github.acshmily.utils.BlindIndexUtils;
import com.github.acshmily.utils.EncryptUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 盲索引模糊查询集成测试（2.1.0，javax 产物 / Boot 2.7.18 + H2）：
 * 正常路径（前缀/中缀/后缀/单字/整值/大小写/中文/随机加密组合）+ 边界路径（null/重复 save/修改重建）。
 */
@SpringBootTest
class JpaBlindIndexIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EncryptProperties encryptProperties;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String expectedIndex(String plain) {
        return BlindIndexUtils.buildIndexValue(plain, encryptProperties.getBlindIndexKey());
    }

    @Test
    void savePopulatesBlindIndexColumns() {
        User saved = userRepository.save(new User("idx", "13812345678", "alice@example.com", "note"));

        // 影子列内容 = 独立实现的 buildIndexValue 输出（写入正确性锁定）
        assertThat(rawColumn(saved.getId(), "phone_bidx")).isEqualTo(expectedIndex("13812345678"));
        assertThat(rawColumn(saved.getId(), "email_bidx")).isEqualTo(expectedIndex("alice@example.com"));
    }

    @Test
    void fuzzyPrefixMiddleSuffixAndFullValue() {
        userRepository.save(new User("fuzzy", "13812345678", "fuzzy@example.com", "note"));

        assertThat(userRepository.findByPhoneBidxContaining("138")).isNotEmpty();       // 前缀
        assertThat(userRepository.findByPhoneBidxContaining("2345")).isNotEmpty();      // 中缀
        assertThat(userRepository.findByPhoneBidxContaining("5678")).isNotEmpty();      // 后缀
        assertThat(userRepository.findByPhoneBidxContaining("13812345678")).isNotEmpty(); // 整值
        assertThat(userRepository.findByPhoneBidxContaining("9999")).isEmpty();          // 未命中
        // 非连续字符组合不应命中（gram 在原文中不存在）
        assertThat(userRepository.findByPhoneBidxContaining("8132")).isEmpty();
    }

    @Test
    void singleCharacterFuzzy() {
        userRepository.save(new User("single", "13700000000", "s@example.com", "note"));

        assertThat(userRepository.findByPhoneBidxContaining("7")).isNotEmpty();
        assertThat(userRepository.findByPhoneBidxContaining("9")).isEmpty();
    }

    @Test
    void searchIsCaseAndPaddingInsensitive() {
        // 带空格/大小写的值放在建了盲索引的 email 字段上
        userRepository.save(new User("norm", "13800000111", "  HeLLo@Ex.COM ", "note"));

        assertThat(userRepository.findByEmailBidxContaining("hello")).isNotEmpty();
        assertThat(userRepository.findByEmailBidxContaining("ex.com")).isNotEmpty();
        assertThat(userRepository.findByEmailBidxContaining("HELLO")).isNotEmpty();
    }

    @Test
    void randomEncryptedFieldIsBlindSearchable() {
        // 随机 IV 字段：密文不可等值查询，但盲索引模糊查询正常工作
        User saved = userRepository.save(new User("rand", "13600000000", "zhang.san@corp.cn", "note"));

        String rawEmail = rawColumn(saved.getId(), "email");
        assertThat(rawEmail).startsWith(EncryptUtils.V2_PREFIX);

        List<User> found = userRepository.findByEmailBidxContaining("zhang.san");
        assertThat(found).isNotEmpty();
        assertThat(found.get(0).getEmail()).isEqualTo("zhang.san@corp.cn"); // 解密后明文
    }

    @Test
    void chineseFuzzySearch() {
        userRepository.save(new User("cn", "13500000000", "wang@x.cn", "note"));

        // 用 email 字段测中文（值本身随意，验证 gram 切分对中文有效）
        userRepository.save(new User("cn2", "13500000001", "张三丰@武当.com", "note2"));
        assertThat(userRepository.findByEmailBidxContaining("张三")).isNotEmpty();
        assertThat(userRepository.findByEmailBidxContaining("三丰")).isNotEmpty();
        assertThat(userRepository.findByEmailBidxContaining("武当")).isNotEmpty();
        assertThat(userRepository.findByEmailBidxContaining("丰三")).isEmpty(); // 逆序不命中
    }

    @Test
    void blindIndexParamNotAtFirstIndex() {
        User saved = userRepository.save(new User("pos", "13412349876", "p@example.com", "note"));

        assertThat(userRepository.findByIdGreaterThanAndPhoneBidxContaining(0L, "4987"))
                .extracting(User::getName).contains("pos");
        assertThat(userRepository.findByIdGreaterThanAndPhoneBidxContaining(saved.getId(), "4987")).isEmpty();
    }

    @Test
    void updateRebuildsIndexOnNextSave() {
        User saved = userRepository.save(new User("upd", "13111111111", "u@example.com", "note"));
        assertThat(userRepository.findByPhoneBidxContaining("13111111111")).isNotEmpty();

        saved.setPhone("13222222222");
        userRepository.save(saved);

        assertThat(userRepository.findByPhoneBidxContaining("13111111111")).isEmpty(); // 旧值不再命中
        assertThat(userRepository.findByPhoneBidxContaining("13222222222")).isNotEmpty(); // 新值命中
    }

    @Test
    void repeatedSaveDoesNotCorruptIndex() {
        // 重复 save 同一实例：源字段已是密文，索引不得被密文 gram 污染
        User saved = userRepository.save(new User("rep", "13333333333", "r@example.com", "note"));
        String indexAfterFirst = rawColumn(saved.getId(), "phone_bidx");
        assertThat(indexAfterFirst).isEqualTo(expectedIndex("13333333333"));

        userRepository.save(saved);
        assertThat(rawColumn(saved.getId(), "phone_bidx")).isEqualTo(indexAfterFirst);
        assertThat(userRepository.findByPhoneBidxContaining("3333")).isNotEmpty();
    }

    @Test
    void nullSourceFieldYieldsNullIndex() {
        User saved = userRepository.save(new User("null-idx", null, null, null));

        assertThat(rawColumn(saved.getId(), "phone_bidx")).isNull();
        assertThat(rawColumn(saved.getId(), "email_bidx")).isNull();
        assertThat(userRepository.findByPhoneBidxContaining("anything")).isEmpty();
    }

    private String rawColumn(Long id, String column) {
        return jdbcTemplate.queryForObject("select " + column + " from users where id = ?", String.class, id);
    }
}
