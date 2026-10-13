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
 * javax 产物（Boot 2.7.18 + H2）全量集成测试：正常路径 + 异常/边界路径。
 * 覆盖 1.x 的全部已知缺陷回归与 2.0.0 新能力。
 */
@SpringBootTest
class JpaEncryptIntegrationTest {

    /** v1 存量密文向量（openssl AES-256-CBC，key=0123456789abcdef0123456789abcdef，iv=1234567890123456） */
    private static final String V1_PHONE_PLAIN = "legacy-phone-100";
    private static final String V1_PHONE_CIPHER = "NMBhz7vDEGa2oojhBysCmq9Ip2JuSlju9jpE8oRaZjM=";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JpaEncryptService jpaEncryptService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ---------- 正常路径 ----------

    @Test
    void saveWritesV2CipherTextToDb() {
        User user = userRepository.save(new User("alice", "13800000001", "alice@example.com", "note-1"));

        String rawPhone = rawColumn(user.getId(), "phone");
        String rawEmail = rawColumn(user.getId(), "email");
        String rawNote = rawColumn(user.getId(), "secret_note");

        assertThat(rawPhone).startsWith(EncryptUtils.V2_PREFIX).doesNotContain("13800000001");
        assertThat(rawEmail).startsWith(EncryptUtils.V2_PREFIX).doesNotContain("alice@example.com");
        // 父类（@MappedSuperclass）字段同样被加密——1.x 只扫子类自身字段，会漏掉
        assertThat(rawNote).startsWith(EncryptUtils.V2_PREFIX).doesNotContain("note-1");
    }

    @Test
    void findDecryptsAllResultShapes() {
        User saved = userRepository.save(new User("bob", "13800000002", "bob@example.com", "note-2"));

        // Optional
        Optional<User> byPhone = userRepository.findByPhone("13800000002");
        assertThat(byPhone).isPresent();
        assertThat(byPhone.get().getPhone()).isEqualTo("13800000002");
        assertThat(byPhone.get().getEmail()).isEqualTo("bob@example.com");
        assertThat(byPhone.get().getSecretNote()).isEqualTo("note-2");

        // List
        List<User> byName = userRepository.findByName("bob");
        assertThat(byName).hasSize(1);
        assertThat(byName.get(0).getPhone()).isEqualTo("13800000002");

        // Page
        Page<User> page = userRepository.findAll(PageRequest.of(0, 10));
        assertThat(page.getContent()).isNotEmpty();
        assertThat(page.getContent().stream().map(User::getPhone)).contains("13800000002");
    }

    @Test
    void encryptParamNotAtFirstIndex() {
        // 回归：1.x 缓存下标错位导致第二次调用加密了错误的参数（本用例在 1.x 必失败）
        User saved = userRepository.save(new User("carol", "13800000003", "carol@example.com", "note-3"));

        Optional<User> first = userRepository.findByIdAndPhone(saved.getId(), "13800000003");
        assertThat(first).isPresent();

        // 第二次调用（命中缓存后下标仍正确）
        Optional<User> second = userRepository.findByIdAndPhone(saved.getId(), "13800000003");
        assertThat(second).isPresent();
    }

    @Test
    void deterministicFieldSupportsDerivedQueries() {
        userRepository.save(new User("dave", "13800000004", "dave@example.com", "note-4"));

        assertThat(userRepository.existsByPhone("13800000004")).isTrue();
        assertThat(userRepository.countByPhone("13800000004")).isEqualTo(1);
        assertThat(userRepository.getByPhone("13800000004").getEmail()).isEqualTo("dave@example.com");
    }

    @Test
    void deterministicFieldSupportsCustomQuery() {
        userRepository.save(new User("eve", "13800000005", "eve@example.com", "note-5"));

        List<User> result = userRepository.searchByCustomQuery("13800000005");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("eve");
    }

    @Test
    void randomFieldProducesDifferentCipherForEachSave() {
        // random = true：相同明文两次落库的密文不同（且都能解密）
        User u1 = userRepository.save(new User("frank", "13800000006", "same@example.com", "note-6"));
        User u2 = userRepository.save(new User("frank2", "13800000007", "same@example.com", "note-6b"));

        String c1 = rawColumn(u1.getId(), "email");
        String c2 = rawColumn(u2.getId(), "email");
        assertThat(c1).startsWith(EncryptUtils.V2_PREFIX);
        assertThat(c1).isNotEqualTo(c2);

        assertThat(userRepository.findById(u1.getId()).get().getEmail()).isEqualTo("same@example.com");
    }

    @Test
    void repeatedSaveDoesNotDoubleEncrypt() {
        // 回归：1.x 对同一实例第二次 save 会加密已加密的值（双重加密）
        User user = new User("grace", "13800000008", "grace@example.com", "note-8");
        User saved = userRepository.save(user);
        String rawAfterFirst = rawColumn(saved.getId(), "phone");

        userRepository.save(saved);
        String rawAfterSecond = rawColumn(saved.getId(), "phone");

        assertThat(rawAfterSecond).isEqualTo(rawAfterFirst);
        assertThat(userRepository.findByPhone("13800000008")).isPresent();
    }

    @Test
    void saveAllEncryptsEachEntity() {
        List<User> saved = userRepository.saveAll(java.util.Arrays.asList(
                new User("heidi", "13800000009", "heidi@example.com", "note-9"),
                new User("ivan", "13800000010", "ivan@example.com", "note-10")));

        assertThat(rawColumn(saved.get(0).getId(), "phone")).startsWith(EncryptUtils.V2_PREFIX);
        assertThat(rawColumn(saved.get(1).getId(), "phone")).startsWith(EncryptUtils.V2_PREFIX);
    }

    @Test
    void plainEntityOperationsUnaffected() {
        // 非加密操作直通：delete/count/existsById 等无 @Encrypt/@EncryptParam 方法不产生副作用
        User saved = userRepository.save(new User("judy", "13800000011", "judy@example.com", "note-11"));
        assertThat(userRepository.count()).isGreaterThanOrEqualTo(1);
        assertThat(userRepository.existsById(saved.getId())).isTrue();
        userRepository.deleteById(saved.getId());
        assertThat(userRepository.existsById(saved.getId())).isFalse();
    }

    // ---------- 异常 / 边界路径 ----------

    @Test
    void nullFieldsAreSafe() {
        // 1.x：字段为 null 直接 NPE
        User saved = userRepository.save(new User("null-fields", null, null, null));

        User found = userRepository.findById(saved.getId()).get();
        assertThat(found.getPhone()).isNull();
        assertThat(found.getEmail()).isNull();
        assertThat(found.getSecretNote()).isNull();
    }

    @Test
    void blankFieldsAreSafe() {
        User saved = userRepository.save(new User("blank-fields", "", "", ""));
        assertThat(rawColumn(saved.getId(), "phone")).isEmpty();
        assertThat(userRepository.findById(saved.getId()).get().getPhone()).isEmpty();
    }

    @Test
    void legacyV1CipherTextStillDecrypts() throws Exception {
        // 预置 1.x 写入的存量数据（v1 无前缀格式），主键固定避免冲突
        jdbcTemplate.update(
                "insert into users (id, name, phone, email, secret_note) values (?, ?, ?, ?, ?)",
                99001L, "legacy-user", V1_PHONE_CIPHER, "legacy-email", "legacy-note");

        User found = userRepository.findById(99001L).get();
        assertThat(found.getPhone()).isEqualTo(V1_PHONE_PLAIN);
    }

    @Test
    void legacyPlaintextDataPassesThrough() {
        // 加密启用前写入的明文数据：读取不抛异常，原样返回（1.x 会把 BadPaddingException 抛给调用方）
        jdbcTemplate.update(
                "insert into users (id, name, phone, email, secret_note) values (?, ?, ?, ?, ?)",
                99002L, "plain-user", "plain-phone-300", "plain-email-300", "plain-note-300");

        User found = userRepository.findById(99002L).get();
        assertThat(found.getPhone()).isEqualTo("plain-phone-300");
    }

    @Test
    void oversizedTextBeyondLegacyLimit() {
        // 回归：1.x 存在 1024 字节硬上限；2.0.0 任意长度
        String bigText = repeat("汉字与unicode混合-", 200); // 约 3400 字节 UTF-8
        User saved = userRepository.save(new User("big", "13800000012", bigText, bigText));

        User found = userRepository.findById(saved.getId()).get();
        assertThat(found.getEmail()).isEqualTo(bigText);
        assertThat(found.getSecretNote()).isEqualTo(bigText);
    }

    @Test
    void unicodeRoundTrip() {
        String text = "中文密文测试🎉emoji-Ωß";
        User saved = userRepository.save(new User("unicode", text, text, text));
        assertThat(userRepository.findById(saved.getId()).get().getPhone()).isEqualTo(text);
        assertThat(userRepository.findByPhone(text)).isPresent();
    }

    // ---------- JpaEncryptService ----------

    @Test
    void serviceRoundTripAndErrors() {
        String cipher = jpaEncryptService.encrypt("service-plain");
        assertThat(cipher).startsWith(EncryptUtils.V2_PREFIX);
        assertThat(jpaEncryptService.deEncrypt(cipher)).isEqualTo("service-plain");

        // 确定性：输出可直接用于等值查询参数
        String again = jpaEncryptService.encrypt("service-plain");
        assertThat(again).isEqualTo(cipher);

        assertThatThrownBy(() -> jpaEncryptService.encrypt("  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> jpaEncryptService.deEncrypt(null))
                .isInstanceOf(IllegalArgumentException.class);
        // 非密文输入：2.0.0 抛 EncryptException（1.x 静默返回空字符串）
        assertThatThrownBy(() -> jpaEncryptService.deEncrypt("not-a-valid-cipher!!!"))
                .isInstanceOf(EncryptException.class);
    }

    // ---------- helpers ----------

    private String rawColumn(Long id, String column) {
        return jdbcTemplate.queryForObject("select " + column + " from users where id = ?", String.class, id);
    }

    private static String repeat(String s, int times) {
        StringBuilder sb = new StringBuilder(s.length() * times);
        for (int i = 0; i < times; i++) {
            sb.append(s);
        }
        return sb.toString();
    }
}
