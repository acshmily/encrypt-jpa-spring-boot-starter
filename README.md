# encrypt-jpa-spring-boot-starter

> **English documentation** is available at the bottom of this page — jump to
> [English Documentation](#english-documentation).

Transparent encryption for JPA `String` entity fields, built on Spring AOP interception of
Spring Data repositories: annotate a field with `@Encrypt` and values are stored as AES
ciphertext; queries return decrypted plaintext. Supports equality search on encrypted
columns (`@EncryptParam`) and — as of 2.1.0 — **LIKE-style fuzzy search** via HMAC blind
indexes (`@BlindIndex`). Ships two flavors: `javax` (Spring Boot 2.x, Java 8–21) and
`jakarta` (Spring Boot 3.x, Java 17+). Latest release: **2.1.0-RELEASE** on Maven Central.

## 简介

​		基于 JPA 对需要加密的 String 字段进行加密，入侵性较小（仅在需要加密的 Entity 对象属性上添加注解即可），采用 AES 对称加密保证了效率和安全性。

LastUpdate:
   - 2026年10月13日:
        - 发布 **2.1.0-RELEASE**：新增**盲索引**（@BlindIndex + @BlindIndexParam），密文字段支持 LIKE 模糊查询（前缀/中缀/后缀/单字/整值统一支持）
        - 新增可选配置 `acshmily.encrypt.index-key`（盲索引独立密钥，缺省自动派生）
        - 测试扩至 94 个（新增 33 个盲索引用例）
   - 2026年10月13日:
        - 发布 **2.0.0-RELEASE**（重大版本，建议从 1.x 迁移，见下方「2.0.0 迁移说明」）
        - **双产物发布**：javax 版（Boot 2.x / Java 8~21）与 jakarta 版（Boot 3.x / Java 17+）
        - 修复多项 1.x 逻辑缺陷（参数下标错位、重复保存双重加密、1024 字节上限、null 崩溃、父类字段漏加密等）
        - 版本化密文格式（v2 带前缀 + 内嵌 IV），1.x 存量数据（v1）仍可解密
        - `@Encrypt(random = true)` 可选随机 IV 模式（安全性最优，不可等值查询）
        - 拦截范围扩大到全部 Repository 方法（saveAll/getBy*/existsBy*/countBy*/自定义 @Query 等）
        - 从零建立全量测试（61 个用例：正常 + 异常路径）
   - 2020年09月15日:
        - 更新到了**1.1.3**版本
        - 拦截器添加了CrudRepository、PagingAndSortingRepository
   - 2020年09月14日:
        - 更新到**1.1.2**版本，解决了重大缺陷，请务必更新
        - 优化了反射缓存机制，提高执行效率
   - 2020年09月10日:
        - 新增了@EncryptParam注解方法，解决了参数加密问题;
        - 新增了JpaEncryptService接口并注册到Spring可以直接注入使用
        - 修复部分bug

目前已经实现：

- 对所有 Repository 写入方法（save/saveAll 等）加密后保存，含父类（@MappedSuperclass）字段
- 对查询返回（Iterable/List/Page/Slice、Optional、单个对象）解密返回
- `@EncryptParam` 参数自动加密（支持等值查询），覆盖 findBy/getBy/existsBy/countBy/自定义 @Query
- 存量 v1 密文与明文数据自动兼容（解不开的值原样返回，不会让查询抛异常）

未实现：

- 对非String属性的加密
- 返回 `Stream<T>` 的方法不解密

## 说明

1、maven 座标——按你的 Spring Boot 版本二选一：

```xml
<!-- Spring Boot 2.x（javax，Java 8~21） -->
<dependency>
  <groupId>com.github.acshmily</groupId>
  <artifactId>encrypt-jpa-spring-boot-starter</artifactId>
  <version>2.0.0-RELEASE</version>
</dependency>

<!-- Spring Boot 3.x（jakarta，Java 17+） -->
<dependency>
  <groupId>com.github.acshmily</groupId>
  <artifactId>encrypt-jpa-jakarta-spring-boot-starter</artifactId>
  <version>2.0.0-RELEASE</version>
</dependency>
```

2、在你的配置文件内添加必须要的信息，当**encrypt-key**不足32位时，会自动左侧补0，当**encrypt-iv**不足16位时，会自动左侧补0（与 1.x 规则一致；2.0.0 起缺失或超长会在启动时直接报错）：

```yml
acshmily:
  encrypt:
    enabled: true # 开启AOP加密
    encrypt-key: 12345678901234561234567890123456
    encrypt-iv: 1234567890123456
```

3、修改你的entity对象，在需要加密的String字段上添加 **@Encrypt** 注解。默认确定性加密（相同明文=相同密文，可配合 `@EncryptParam` 做等值查询）；不需要等值查询的字段可用 **@Encrypt(random = true)** 获得更强的安全性（随机 IV，相同明文产生不同密文）：

```java
@Entity
@Data
@ToString
public class TestEncrypt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Encrypt
    private String username;
    @Encrypt(random = true) // 随机 IV：更安全，但该字段不能用于 WHERE col = ? 查询
    private String idCard;
```

4、查询方法的参数使用 **@EncryptParam** 注解，执行查询时会自动加密（下标任意位置均可）：
```java
@Repository
public interface EncryptDao extends JpaRepository<TestEncrypt,Long> {
    Page<TestEncrypt> findAllByIdGreaterThan(Long id,Pageable pageable);
    TestEncrypt findByUsername(@EncryptParam String username);
    TestEncrypt findByIdAndUsername(@EncryptParam Long id,@EncryptParam String username);
    boolean existsByUsername(@EncryptParam String username); // 2.0.0：派生谓词与自定义 @Query 均已覆盖
}
```
5、如果需要自己执行加密解密操作可以直接注入 **JpaEncryptService**：
```java
@Resource
private JpaEncryptService jpaEncryptService;
```
JpaEncryptService定义方法如下:
```java
public interface JpaEncryptService {
    /**
     * 加密方法（确定性，输出可直接用于等值查询参数）
     */
    String encrypt(String rawString);

    /**
     * 解密方法（自动识别 v2 / v1 格式）
     */
    String deEncrypt(String encryptString);
}
```
注意：2.0.0 起加解密失败会抛出 `com.github.acshmily.exception.EncryptException`（1.x 静默返回空字符串）。

6、**模糊查询（LIKE）**——2.1.0 新增盲索引。密文无法直接 LIKE，本库通过「顺序 n-gram + 连续片段 HMAC 匹配」在影子列建立盲索引，前缀/中缀/后缀/单字/整值统一支持：

```java
@Entity
public class User {
    @Encrypt(random = true)                        // 有盲索引后推荐随机 IV（查询已由索引承担）
    private String phone;

    @BlindIndex("phone")                           // 影子列：库自动填充，业务不读不写
    @Column(name = "phone_bidx", length = 4000)    // 存储放大约 34 倍/字符，列务必加长（255 只够约 7 字符）
    private String phoneBidx;
}

@Repository
public interface UserDao extends JpaRepository<User, Long> {
    // 前缀/中缀/后缀/整值统一模糊匹配；等值也可用 Containing
    List<User> findByPhoneBidxContaining(@BlindIndexParam String keyword);
}
```

要点与限制（详见 AGENTS.md「盲索引」章节）：
- 归一化规则为 trim + 小写（英文大小写不敏感，中文无影响）
- `LIKE '%x%'` 为全表扫描，适用中小数据量；等值高频场景仍推荐确定性加密 + `@EncryptParam`（可建 DB 索引）
- 长文本字段（几百字符以上）不建议建盲索引（索引列会溢出）
- 不支持无序多词与正则；可选配置 `acshmily.encrypt.index-key` 使用独立索引密钥（缺省自动从 encryptKey 派生）

## 2.0.0 迁移说明

- **存量数据可读**：1.x 写入的密文（v1，无前缀）与加密启用前的明文数据，读取时自动兼容（明文/解不开的值原样返回）。
- **等值查询需迁移**：新写入的均为 v2 格式（带 `enc:v2:` 前缀），对 v1 存量行的等值查询不再命中。迁移方式：遍历实体重新 save，或用 `JpaEncryptService` 批量重写。
- **单向不兼容**：降级回 1.1.3 后无法读取 v2 格式数据，升级前请评估。
- 密钥/IV 配置保持不变，无需更换。

更多设计细节（密文格式规范、架构、发布流程）见 [AGENTS.md](AGENTS.md)。

## English Documentation

### Overview

`encrypt-jpa-spring-boot-starter` provides transparent encryption for JPA `String` entity
fields with minimal intrusion — a single `@Encrypt` annotation per field. It works by
intercepting Spring Data repository calls with a Spring AOP aspect:

- **Writes** (`save`, `saveAll`, …): annotated `String` fields (including fields declared
  in `@MappedSuperclass` superclasses) are encrypted with AES before being persisted.
- **Reads**: results of any repository method (`Iterable`/`List`/`Page`/`Slice`,
  `Optional`, or a single object) are decrypted back to plaintext.
- **Query parameters** annotated with `@EncryptParam` are encrypted before the query runs,
  so `WHERE phone = ?` equality matching keeps working against ciphertext.
- **2.1.0 — blind indexes**: fields annotated with `@BlindIndex` get an HMAC n-gram shadow
  column, enabling `LIKE`-style fuzzy search (prefix / infix / suffix / single character /
  full value) over encrypted columns.

The starter is fully automatic: register the dependency, set `acshmily.encrypt.enabled=true`
and nothing else changes in your business code.

### Installation

Pick the flavor matching your Spring Boot generation (both are on Maven Central):

```xml
<!-- Spring Boot 2.x (javax namespace, Java 8~21) -->
<dependency>
  <groupId>com.github.acshmily</groupId>
  <artifactId>encrypt-jpa-spring-boot-starter</artifactId>
  <version>2.1.0-RELEASE</version>
</dependency>

<!-- Spring Boot 3.x (jakarta namespace, Java 17+) -->
<dependency>
  <groupId>com.github.acshmily</groupId>
  <artifactId>encrypt-jpa-jakarta-spring-boot-starter</artifactId>
  <version>2.1.0-RELEASE</version>
</dependency>
```

### Quick start

1. Configure (key shorter than 32 chars / IV shorter than 16 chars are left-padded with
   `0`; missing or oversized values fail fast at startup):

```yaml
acshmily:
  encrypt:
    enabled: true
    encrypt-key: 12345678901234561234567890123456
    encrypt-iv: 1234567890123456
```

2. Annotate entity fields. Deterministic mode (default) keeps equality search working;
   `random = true` gives per-value random IVs — the strongest security, but the column can
   no longer be used with `WHERE col = ?`:

```java
@Entity
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Encrypt                                  // deterministic, equality-searchable
    private String phone;

    @Encrypt(random = true)                   // random IV, not equality-searchable
    private String idCard;
}
```

3. Annotate query parameters for equality search (any parameter position; derived verbs
   like `existsBy…`/`countBy…` and custom `@Query` methods are covered):

```java
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByPhone(@EncryptParam String phone);
    boolean existsByPhone(@EncryptParam String phone);
}
```

4. (Optional) Fuzzy search via blind index — declare a shadow column and let the starter
   populate it automatically:

```java
@Encrypt(random = true)                       // with a blind index, prefer random IV
private String phone;

@BlindIndex("phone")                          // value = source field name
@Column(name = "phone_bidx", length = 4000)   // ~34 chars of index per input char; enlarge the column!
private String phoneBidx;
```

```java
// one parameter covers prefix / infix / suffix / single char / full value
List<User> findByPhoneBidxContaining(@BlindIndexParam String keyword);
```

5. (Optional) Manual encrypt/decrypt anywhere in your code:

```java
@Resource
private JpaEncryptService jpaEncryptService;   // encrypt(...) / deEncrypt(...)
```

### Cipher format

- **v2 (written by 2.x)**: `"enc:v2:" + Base64(IV[16] + AES/CBC/PKCS5 ciphertext)` with the
  IV embedded per value. Deterministic mode reuses the configured static IV so equal
  plaintexts yield equal ciphertexts (required for SQL equality matching); `random` mode
  draws a fresh IV per encryption. No length limit (1.x had a 1024-byte cap).
- **v1 (legacy, decrypt-only)**: unprefixed `Base64(AES/CBC/PKCS5 ciphertext)` with the
  static IV — data written by 1.x remains readable. Undecryptable values (e.g. plaintext
  rows written before encryption was enabled) are returned as-is instead of throwing.

Key derivation is unchanged from 1.x: `encryptKey` is left-padded with `0` to 32 bytes,
`encryptIv` to 16 bytes.

### Blind index notes & constraints

- Normalization is `trim().toLowerCase()` (English case-insensitive; no effect on CJK).
- `LIKE '%x%'` cannot use a database index — suitable for small-to-medium tables. For
  high-frequency equality lookups prefer deterministic `@Encrypt` + `@EncryptParam`.
- Storage amplification is ~34 chars per input character: `varchar(255)` only supports
  ~7 characters of plaintext; use `length >= 4000` or `TEXT`. Do **not** put a blind
  index on long text fields (the index column overflows and the insert fails on purpose).
- Tokens are 64-bit truncated HMAC-SHA256 values; collisions only approach the birthday
  bound at millions of rows — verify matches in application code if that matters.
- Unsupported: unordered multi-word queries, regex, cross-field search. Do not feed an
  already-converted token fragment back as a query parameter (conversion is not idempotent).
- Optional dedicated index key: `acshmily.encrypt.index-key` (defaults to a value derived
  from `encryptKey`; the derivation formula is a compatibility contract).

### Migrating from 1.x

- Existing v1 ciphertext and pre-encryption plaintext rows remain readable.
- New writes are v2; equality queries with `@EncryptParam` no longer match legacy v1 rows
  until the data is rewritten (re-save entities or batch-rewrite with `JpaEncryptService`).
- Downgrading back to 1.1.3 cannot read v2 data — evaluate before upgrading.
- Keys/IVs carry over unchanged; no configuration migration needed.

See [AGENTS.md](AGENTS.md) for the full design documentation (cipher format
specification with test vectors, interceptor internals, release process).

## License

```
          Apache License
                           Version 2.0, January 2004
                        http://www.apache.org/licenses/

   TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION

   1. Definitions.

      "License" shall mean the terms and conditions for use, reproduction,
      and distribution as defined by Sections 1 through 9 of this document.

      "Licensor" shall mean the copyright owner or entity authorized by
      the copyright owner that is granting the License.

      "Legal Entity" shall mean the union of the acting entity and all
      other entities that control, are controlled by, or are under common
      control with that entity. For the purposes of this definition,
      "control" means (i) the power, direct or indirect, to cause the
      direction or management of such entity, whether by contract or
      otherwise, or (ii) ownership of fifty percent (50%) or more of the
      outstanding shares, or (iii) beneficial ownership of such entity.

      "You" (or "Your") shall mean an individual or Legal Entity
      exercising permissions granted by this License.

      "Source" form shall mean the preferred form for making modifications,
      including but not limited to software source code, documentation
      source, and configuration files.

      "Object" form shall mean any form resulting from mechanical
      transformation or translation of a Source form, including but
      not limited to compiled object code, generated documentation,
      and conversions to other media types.

      "Work" shall mean the work of authorship, whether in Source or
      Object form, made available under the License, as indicated by a
      copyright notice that is included in or attached to the work
      (an example is provided in the Appendix below).

      "Derivative Works" shall mean any work, whether in Source or Object
      form, that is based on (or derived from) the Work and for which the
      editorial revisions, annotations, elaborations, or other modifications
      represent, as a whole, an original work of authorship. For the purposes
      of this License, Derivative Works shall not include works that remain
      separable from, or merely link (or bind by name) to the interfaces of,
      the Work and Derivative Works thereof.

      "Contribution" shall mean any work of authorship, including
      the original version of the Work and any modifications or additions
      to that Work or Derivative Works thereof, that is intentionally
      submitted to Licensor for inclusion in the Work by the copyright owner
      or by an individual or Legal Entity authorized to submit on behalf of
      the copyright owner. For the purposes of this definition, "submitted"
      means any form of electronic, verbal, or written communication sent
      to the Licensor or its representatives, including but not limited to
      communication on electronic mailing lists, source code control systems,
      and issue tracking systems that are managed by, or on behalf of, the
      Licensor for the purpose of discussing and improving the Work, but
      excluding communication that is conspicuously marked or otherwise
      designated in writing by the copyright owner as "Not a Contribution."

      "Contributor" shall mean Licensor and any individual or Legal Entity
      on behalf of whom a Contribution has been received by Licensor and
      subsequently incorporated within the Work.

   2. Grant of Copyright License. Subject to the terms and conditions of
      this License, each Contributor hereby grants to You a perpetual,
      worldwide, non-exclusive, no-charge, royalty-free, irrevocable
      copyright license to reproduce, prepare Derivative Works of,
      publicly display, publicly perform, sublicense, and distribute the
      Work and such Derivative Works in Source or Object form.

   3. Grant of Patent License. Subject to the terms and conditions of
      this License, each Contributor hereby grants to You a perpetual,
      worldwide, non-exclusive, no-charge, royalty-free, irrevocable
      (except as stated in this section) patent license to make, have made,
      use, offer to sell, sell, import, and otherwise transfer the Work,
      where such license applies only to those patent claims licensable
      by such Contributor that are necessarily infringed by their
      Contribution(s) alone or by combination of their Contribution(s)
      with the Work to which such Contribution(s) was submitted. If You
      institute patent litigation against any entity (including a
      cross-claim or counterclaim in a lawsuit) alleging that the Work
      or a Contribution incorporated within the Work constitutes direct
      or contributory patent infringement, then any patent licenses
      granted to You under this License for that Work shall terminate
      as of the date such litigation is filed.

   4. Redistribution. You may reproduce and distribute copies of the
      Work or Derivative Works thereof in any medium, with or without
      modifications, and in Source or Object form, provided that You
      meet the following conditions:

      (a) You must give any other recipients of the Work or
          Derivative Works a copy of this License; and

      (b) You must cause any modified files to carry prominent notices
          stating that You changed the files; and

      (c) You must retain, in the Source form of any Derivative Works
          that You distribute, all copyright, patent, trademark, and
          attribution notices from the Source form of the Work,
          excluding those notices that do not pertain to any part of
          the Derivative Works; and

      (d) If the Work includes a "NOTICE" text file as part of its
          distribution, then any Derivative Works that You distribute must
          include a readable copy of the attribution notices contained
          within such NOTICE file, excluding those notices that do not
          pertain to any part of the Derivative Works, in at least one
          of the following places: within a NOTICE text file distributed
          as part of the Derivative Works; within the Source form or
          documentation, if provided along with the Derivative Works; or,
          within a display generated by the Derivative Works, if and
          wherever such third-party notices normally appear. The contents
          of the NOTICE file are for informational purposes only and
          do not modify the License. You may add Your own attribution
          notices within Derivative Works that You distribute, alongside
          or as an addendum to the NOTICE text from the Work, provided
          that such additional attribution notices cannot be construed
          as modifying the License.

      You may add Your own copyright statement to Your modifications and
      may provide additional or different license terms and conditions
      for use, reproduction, or distribution of Your modifications, or
      for any such Derivative Works as a whole, provided Your use,
      reproduction, and distribution of the Work otherwise complies with
      the conditions stated in this License.

   5. Submission of Contributions. Unless You explicitly state otherwise,
      any Contribution intentionally submitted for inclusion in the Work
      by You to the Licensor shall be under the terms and conditions of
      this License, without any additional terms or conditions.
      Notwithstanding the above, nothing herein shall supersede or modify
      the terms of any separate license agreement you may have executed
      with Licensor regarding such Contributions.

   6. Trademarks. This License does not grant permission to use the trade
      names, trademarks, service marks, or product names of the Licensor,
      except as required for reasonable and customary use in describing the
      origin of the Work and reproducing the content of the NOTICE file.

   7. Disclaimer of Warranty. Unless required by applicable law or
      agreed to in writing, Licensor provides the Work (and each
      Contributor provides its Contributions) on an "AS IS" BASIS,
      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
      implied, including, without limitation, any warranties or conditions
      of TITLE, NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A
      PARTICULAR PURPOSE. You are solely responsible for determining the
      appropriateness of using or redistributing the Work and assume any
      risks associated with Your exercise of permissions under this License.

   8. Limitation of Liability. In no event and under no legal theory,
      whether in tort (including negligence), contract, or otherwise,
      unless required by applicable law (such as deliberate and grossly
      negligent acts) or agreed to in writing, shall any Contributor be
      liable to You for damages, including any direct, indirect, special,
      incidental, or consequential damages of any character arising as a
      result of this License or out of the use or inability to use the
      Work (including but not limited to damages for loss of goodwill,
      work stoppage, computer failure or malfunction, or any and all
      other commercial damages or losses), even if such Contributor
      has been advised of the possibility of such damages.

   9. Accepting Warranty or Additional Liability. While redistributing
      the Work or Derivative Works thereof, You may choose to offer,
      and charge a fee for, acceptance of support, warranty, indemnity,
      or other liability obligations and/or rights consistent with this
      License. However, in accepting such obligations, You may act only
      on Your own behalf and on Your sole responsibility, not on behalf
      of any other Contributor, and only if You agree to indemnify,
      defend, and hold each Contributor harmless for any liability
      incurred by, or claims asserted against, such Contributor by reason
      of your accepting any such warranty or additional liability.

   END OF TERMS AND CONDITIONS

   APPENDIX: How to apply the Apache License to your work.

      To apply the Apache License to your work, attach the following
      boilerplate notice, with the fields enclosed by brackets "[]"
      replaced with your own identifying information. (Don't include
      the brackets!)  The text should be enclosed in the appropriate
      comment syntax for the file format. We also recommend that a
      file or class name and description of purpose be included on the
      same "printed page" as the copyright notice for easier
      identification within third-party archives.

   Copyright [yyyy] [name of copyright owner]

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
```