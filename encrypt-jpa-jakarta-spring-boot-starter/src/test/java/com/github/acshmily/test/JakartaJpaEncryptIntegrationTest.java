package com.github.acshmily.test;

import com.github.acshmily.exception.EncryptException;
import com.github.acshmily.service.JpaEncryptService;
import com.github.acshmily.test.domain.User;
import com.github.acshmily.test.repo.UserRepository;
import com.github.acshmily.utils.EncryptUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * jakarta 产物（Boot 3.5 + H2）集成测试：复验 javax 侧核心场景，证明双产物共用 core 可行。
 */
@SpringBootTest
class JakartaJpaEncryptIntegrationTest {

    /** v1 存量密文向量（与 javax 模块相同：openssl AES-256-CBC） */
    private static final String V1_PHONE_PLAIN = "legacy-phone-100";
    private static final String V1_PHONE_CIPHER = "NMBhz7vDEGa2oojhBysCmq9Ip2JuSlju9jpE8oRaZjM=";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JpaEncryptService jpaEncryptService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void saveWritesV2CipherTextAndFindDecrypts() {
        User saved = userRepository.save(new User("alice", "13800000001", "alice@example.com", "note-1"));

        assertThat(rawColumn(saved.getId(), "phone"))
                .startsWith(EncryptUtils.V2_PREFIX)
                .doesNotContain("13800000001");
        assertThat(rawColumn(saved.getId(), "secret_note")).startsWith(EncryptUtils.V2_PREFIX);

        Optional<User> found = userRepository.findByPhone("13800000001");
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("alice@example.com");
        assertThat(found.get().getSecretNote()).isEqualTo("note-1");
    }

    @Test
    void encryptParamNotAtFirstIndexAndDerivedQueries() {
        User saved = userRepository.save(new User("carol", "13800000003", "carol@example.com", "note-3"));

        assertThat(userRepository.findByIdAndPhone(saved.getId(), "13800000003")).isPresent();
        assertThat(userRepository.existsByPhone("13800000003")).isTrue();
        assertThat(userRepository.searchByCustomQuery("13800000003")).hasSize(1);
    }

    @Test
    void pageAndListResultsDecrypt() {
        userRepository.save(new User("bob", "13800000002", "bob@example.com", "note-2"));

        List<User> byName = userRepository.findByName("bob");
        assertThat(byName).hasSize(1);
        assertThat(byName.get(0).getPhone()).isEqualTo("13800000002");

        Page<User> page = userRepository.findAll(PageRequest.of(0, 10));
        assertThat(page.getContent()).isNotEmpty();
        assertThat(page.getContent().stream().map(User::getPhone)).contains("13800000002");
    }

    @Test
    void repeatedSaveDoesNotDoubleEncrypt() {
        User saved = userRepository.save(new User("grace", "13800000008", "grace@example.com", "note-8"));
        String rawAfterFirst = rawColumn(saved.getId(), "phone");

        userRepository.save(saved);
        assertThat(rawColumn(saved.getId(), "phone")).isEqualTo(rawAfterFirst);
        assertThat(userRepository.findByPhone("13800000008")).isPresent();
    }

    @Test
    void nullAndBlankFieldsAreSafe() {
        User saved = userRepository.save(new User("null-fields", null, null, null));
        assertThat(userRepository.findById(saved.getId()).get().getPhone()).isNull();
    }

    @Test
    void legacyV1AndPlaintextDataPassThrough() {
        jdbcTemplate.update(
                "insert into users (id, name, phone, email, secret_note) values (?, ?, ?, ?, ?)",
                99001L, "legacy-user", V1_PHONE_CIPHER, "legacy-email", "legacy-note");
        assertThat(userRepository.findById(99001L).get().getPhone()).isEqualTo(V1_PHONE_PLAIN);

        jdbcTemplate.update(
                "insert into users (id, name, phone, email, secret_note) values (?, ?, ?, ?, ?)",
                99002L, "plain-user", "plain-phone-300", "plain-email-300", "plain-note-300");
        assertThat(userRepository.findById(99002L).get().getPhone()).isEqualTo("plain-phone-300");
    }

    @Test
    void serviceBehavesIdentically() {
        String cipher = jpaEncryptService.encrypt("service-plain");
        assertThat(jpaEncryptService.deEncrypt(cipher)).isEqualTo("service-plain");
        assertThatThrownBy(() -> jpaEncryptService.deEncrypt("not-a-valid-cipher!!!"))
                .isInstanceOf(EncryptException.class);
    }

    // ---------- 盲索引模糊查询（2.1.0） ----------

    @Test
    void blindIndexFuzzySearchWorks() {
        userRepository.save(new User("jack", "13812345678", "jack@example.com", "note"));

        // 影子列由切面自动填充；模糊查询前缀/中缀/单字命中，未命成为空
        assertThat(userRepository.findByPhoneBidxContaining("138")).isNotEmpty();
        assertThat(userRepository.findByPhoneBidxContaining("2345")).isNotEmpty();
        assertThat(userRepository.findByPhoneBidxContaining("7")).isNotEmpty();
        assertThat(userRepository.findByPhoneBidxContaining("9999")).isEmpty();
        assertThat(userRepository.findByPhoneBidxContaining("8132")).isEmpty(); // 非连续不命中
    }

    @Test
    void blindIndexUpdateAndRepeatSave() {
        User saved = userRepository.save(new User("upd", "13111111111", "u@example.com", "note"));
        assertThat(userRepository.findByPhoneBidxContaining("13111111111")).isNotEmpty();

        saved.setPhone("13222222222");
        userRepository.save(saved);
        assertThat(userRepository.findByPhoneBidxContaining("13111111111")).isEmpty();
        assertThat(userRepository.findByPhoneBidxContaining("13222222222")).isNotEmpty();

        // 重复 save 不污染索引
        userRepository.save(saved);
        assertThat(userRepository.findByPhoneBidxContaining("13222222222")).isNotEmpty();
    }

    private String rawColumn(Long id, String column) {
        return jdbcTemplate.queryForObject("select " + column + " from users where id = ?", String.class, id);
    }
}
