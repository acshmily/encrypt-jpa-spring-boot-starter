# AGENTS.md

面向 AI 助手的本项目导读。修改本仓库前请通读本文档；与人类开发者约定以本文档为准。

## 项目是什么

JPA 实体字段透明加密 Spring Boot Starter（`com.github.acshmily`）。通过 Spring AOP 切面拦截
Spring Data Repository 调用：写入时加密带 `@Encrypt` 的 String 字段与带 `@EncryptParam`
的查询参数，读取时自动解密返回明文。使用者只需在实体字段/仓库方法参数上加注解，业务代码无感。

- 版本：**2.0.0-RELEASE**（2026-10）
- 许可：Apache 2.0
- 语言：代码注释与 README 为中文，对外 API/异常消息中英混合
- 发布物：Maven Central（Sonatype Central Portal）

## 模块结构（多产物架构）

```
encrypt-jpa-parent (根 pom, packaging=pom)
├── encrypt-jpa-core                        # 全部核心逻辑，命名空间中立
├── encrypt-jpa-spring-boot-starter         # javax 产物：Boot 2.x / Java 8~21
└── encrypt-jpa-jakarta-spring-boot-starter # jakarta 产物：Boot 3.x / Java 17+
```

**关键架构决策——"compile low, run high"**：core 用 Java 8 字节码（`maven.compiler.release=8`）
编译一次，javax 与 jakarta 两个 starter 共用。这之所以可行，是因为 core 中**没有任何
javax.*/jakarta.* 命名空间依赖**（字段注入已全部改为构造器注入，`com.sun.istack.NotNull` 已删除），
所用 Spring API（`@ConfigurationProperties`、AspectJ 注解等）在 Boot 2 与 Boot 3 中 FQCN 完全一致。
两个 starter 模块只差一个薄配置类和自动配置注册文件：

| | javax starter | jakarta starter |
|---|---|---|
| 配置类注解 | `@Configuration` | `@AutoConfiguration` |
| 注册机制 | `META-INF/spring.factories` | `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` |
| 编译基线 | Boot 2.7.18 / release 8 | Boot 3.5.x / release 17 |
| 运行面 | Boot 2.0~2.7，Java 8~21 | Boot 3.x，Java 17+ |

两个模块的 `AutoEncryptConfig` 同包同名（`com.github.acshmily.config.AutoEncryptConfig`），
不会同时出现在 classpath 上；即使误同时引入，Boot 2 只读 spring.factories、Boot 3 只读
imports 文件，也不会重复装配。

## 工作机制（拦截器）

`EncryptInterceptor`（core）是唯一切面：

- 切入点：`execution(public * org.springframework.data.repository.Repository+.*(..))`
  ——**全部**仓库 public 方法。相较 1.x 只拦截 `save`/`find*`，现覆盖 `saveAll`、
  `getBy/readBy/queryBy/countBy/existsBy/deleteBy` 派生查询与自定义 `@Query` 方法。
- `@Around` 流程：加密 `@EncryptParam` 参数（确定性模式）→ 加密实体参数字段（含
  `Iterable` 元素，即 saveAll）→ `proceed` → 解密返回值。
- 解密结果形状：`Iterable`（含 List/Page/Slice）、`Optional`、单对象；`Stream` 不支持。
- 反射用**直接字段访问**（`Field.setAccessible`），沿类层级扫描（覆盖 `@MappedSuperclass`）。
  不要改回 getter/setter 反射——直接访问不依赖 JavaBean 命名、能读父类字段、
  且不会触发 Hibernate 懒加载代理初始化（代理实例的字段为 null，天然跳过）。
- 缓存（`EncryptCacheContainer`）：`Class → List<EncryptField>` 与
  `Method → int[]`（@EncryptParam 参数下标），`computeIfAbsent` 原子写入，**空结果即负缓存**。
  修改时必须保持：缓存的是参数**下标**而不是注解数组（1.x 缓存注解数组导致下标错位，
  加密了错误参数，回归用例 `encryptParamNotAtFirstIndex`）。

## 密文格式规范（改动需慎重，涉及存量数据）

密钥派生（v1/v2 通用，勿改动，否则存量数据解不开）：
`encryptKey` 按 `String.format("%32s", key).replace(" ", "0")` 左补 '0' 至 32 字节 AES-256；
`encryptIv` 同理补至 16 字节。注意：**值内部的空格同样会被替换**——这是 1.x 遗留行为，已用测试锁定。

- **v2（当前写入格式）**：`"enc:v2:" + Base64(IV[16] + AES/CBC/PKCS5密文)`，IV 内嵌。
  - `@Encrypt`（默认）与 `@EncryptParam`：确定性模式（静态 IV）→ 相同明文=相同密文，
    **密文可直接用于 SQL 等值查询**（这是 `WHERE col = ?` 能命中的前提，不可改随机）。
  - `@Encrypt(random = true)`：随机 IV → 安全性最优，但**不可用于等值查询**。
- **v1（历史格式，仅解密）**：无前缀 `Base64(AES/CBC/PKCS5密文)` + 静态 IV。1.x 存量数据自动识别解密。
- 解密失败（存量明文、脏数据）：拦截器**保留原值**继续返回（debug 日志），查询不抛异常；
  `JpaEncryptService.deEncrypt` 则抛 `EncryptException`（2.0.0 行为，1.x 曾返回空串）。
- 兼容性测试向量（openssl AES-256-CBC，key=`0123456789abcdef0123456789abcdef`，
  iv=`1234567890123456`）：`legacy-phone-100` → `NMBhz7vDEGa2oojhBysCmq9Ip2JuSlju9jpE8oRaZjM=`。
  该向量在 core 与两个 starter 的测试中多处硬编码，改派生规则必先改向量。
- **已知限制**：CBC 无完整性保护——篡改密文尾部会在解密时暴露（padding 错误），
  篡改内嵌 IV 只会破坏第一个明文分组而不报错。

### 存量数据迁移（1.x → 2.0.0）

- 读：v1 密文与明文数据均兼容（自动识别/原样返回）。
- 写：新密文均为 v2。**确定性字段的等值查询将不再命中 v1 旧行**（v2 参数 ≠ v1 存量值）。
  迁移方式：遍历重新 save 全部实体，或用 `JpaEncryptService.deEncrypt` + `encrypt` 批量重写。
  单向不兼容：降回 1.1.3 无法读取 v2 数据。

## 盲索引（2.1.0 新增）：密文 LIKE 模糊查询

密文因雪崩效应无法直接 LIKE，方案是 **顺序 n-gram + 连续片段 HMAC 匹配**：

- **索引列格式**（两段，段内逗号分隔、首尾补逗号、段间分号）：
  `,H(g2_1),H(g2_2),...,;,H(g1_1),H(g1_2),...,`
  第一段为归一化（trim + 小写）原文的全部 2-gram（按 UTF-16 字符滑动），第二段为全部 1-gram。
  token = `HMAC-SHA256(indexKey, gram)` 前 8 字节的小写 hex（16 字符，64-bit）。
- **核心正确性**：关键词是原文连续子串时，其 gram 序列在索引串中必然连续出现——
  因此单参数 LIKE 即可统一支持前缀/中缀/后缀/整值匹配。`toQueryToken` 产出的片段
  （`,H(g1),H(g2),` 形态）交给 Spring Data `Containing` 包 `%` 即命中。等值也用 `Containing`
  （整值 token 序列即完整片段）；`Equal` 不可用（列内容含两段 ≠ 转换片段）。
- **无误配**：token 是定宽 hex（`[0-9a-f]`），分隔符 `,;` 不在其中。
- **用法**：实体源字段照常 `@Encrypt`（推荐 `random = true`——查询已由索引承担），
  影子字段标注 `@BlindIndex("源字段名")`（普通 String 列，切面自动填充，业务不读不写）；
  查询参数标注 `@BlindIndexParam`（不得与 `@EncryptParam` 同参数，否则首次调用 fail-fast）。
- **写入语义**：切面在加密**前**用明文建索引；源字段已是 v2 密文（重复 save）时跳过重算
  ——防止把密文 gram 写进索引；字段改为新明文后重 save 正常重建。
- **indexKey**：可选配置 `acshmily.encrypt.index-key`；未配置时派生自 encryptKey——
  `HMAC-SHA256(encryptKey 原始字节, "encrypt-jpa-blind-index-v1")` 全 32 字节。
  派生公式是兼容性契约（勿改动，更换会使存量索引全部失效）。

### 盲索引限制（改代码前先读）

- **存储放大**：每字符约 34 个 hex 字符。默认 varchar(255) 只够约 7 字符明文，
  影子列建议 `@Column(length = 4000)`（约 117 字符）或 TEXT。**长文本字段不要建盲索引**
  （几千字符文本的索引会超过 10 万字符，DB 列溢出直接报错——这是有意 fail-fast 而非截断，
  截断会静默破坏长关键词查询）。
- **性能**：`LIKE '%x%'` 全表扫描，适用中小数据量；等值高频场景仍用确定性加密 +
  `@EncryptParam`（可建 DB 索引）。
- **碰撞**：64-bit token，数百万行级才接近生日界；命中后可应用层复核兜底（实体已解密，`.filter` 即可）。
- **不支持**：无序多词（"三 张"）、正则、跨字段；`@BlindIndexParam` 转换不幂等
  （无前缀可辨），勿把已转换片段当参数回传。
- 归一化只做 trim + 小写；中文/emoji 按 UTF-16 字符切分（emoji 代理对会被拆开）。

## 已修复的 1.x 缺陷清单（回归测试已锁定）

1. `@EncryptParam` 缓存下标错位（非首位参数加密错误参数）
2. `args[0]` 为 null、字段值为 null 的 NPE
3. 1024 字节加密上限（commons-crypto 固定 buffer）
4. 同一实例重复 save 双重加密（现已跳过带 `enc:v2:` 前缀的值）
5. `findAll()` 等 Iterable 返回不解密
6. 父类（`@MappedSuperclass`）字段不加密
7. getter 名字节运算对非小写开头字段出错（直接字段访问根除）
8. 解密异常炸查询（现保留原值）
9. 缓存无负结果导致每次调用重复反射
10. key 缺失时静默用字面量 `"null"` 生成密钥（现启动 fail-fast）
11. `com.sun.istack.NotNull` 导致 JDK 11+ 编译失败（已删除）
12. saveAll/getBy*/existsBy*/countBy*/自定义 @Query 不被拦截（广覆盖切入点根除）

## 构建与测试

```bash
mvn clean verify                 # 全量构建 + 全部测试（日常，无需 GPG）
mvn -pl encrypt-jpa-core test    # 仅 core 单测
mvn -P release,gpg verify -DskipTests  # 本地验证发布物（sources/javadoc/GPG 签名）
```

- 本地 JDK 21 + Maven 3.10 验证通过；CI 矩阵 JDK 17/21。
- 测试规模：core 33 单测 + javax 集成 21 + jakarta 集成 7。测试约定：正常路径 +
  异常/边界（null/空串/超长/篡改/错误密钥/非法输入）+ 1.x 回归用例都要覆盖；
  新增行为必须同时进 AGENTS.md 与测试。
- 集成测试用 H2 内存库；javax 测试若遇 H2 保留字问题记得实体用 `@Table(name="users")`。
- 修改 `EncryptUtils`/`EncryptProperties` 的派生或格式逻辑前，先看「密文格式规范」一节。

## 配置参考

```yaml
acshmily:
  encrypt:
    enabled: true            # 开关，默认关闭；false 时自动配置整体退出
    encrypt-key: xxx         # 1~32 字符，左补 '0' 至 32 字节（启用时必填，超长启动失败）
    encrypt-iv: xxx          # 1~16 字符，左补 '0' 至 16 字节（启用时必填）
```

Bean 覆盖：`EncryptCacheContainer` / `JpaEncryptService` / `EncryptInterceptor` 均带
`@ConditionalOnMissingBean`，使用方可注册自己的实例替换。

## 版本与发布流程

版本号 `2.0.0-RELEASE`，tag 与 pom 一致（如 `2.0.0-RELEASE`）。发布步骤：

1. `mvn -P release,gpg deploy` —— GPG 签名（key `194068DD`，passphrase 在
   `~/.m2/settings.xml` 的 `gpg` profile）→ 上传 Sonatype Central Portal
   （`central-publishing-maven-plugin`，`publishingServerId=sonatype-central`，autoPublish=true）。
2. 前提：GPG 公钥已上传 `keyserver.ubuntu.com`（Central 校验签名要求）；
   settings.xml 中 `sonatype-central` token 可用；命名空间 `com.github.acshmily` 已验证。
3. 旧发布通道 oss.sonatype.org（OSSRH）**已停用**，勿在 pom 中恢复 `distributionManagement`
   或 nexus-staging 插件。
4. 发布后到 https://central.sonatype.com 验证可检索（同步有延迟）。

### 已知坑：central-publishing 插件 0.11.0 多模块失败（2.0.0 发布时踩过）

`mvn deploy` 时插件会把 `maven-metadata-local.xml` 与 `_remote.repositories*` 打进
bundle，Portal 校验报 `Bundle has content that does NOT have a .pom file: <group 目录>`
而失败（插件在各模块累积 staging，仅最后模块真正上传，所以失败只会发生在最后一个模块）。
**可用发布方式**：deploy 失败后，产物已签名在 `target/central-staging`，从中剔除上述元数据
文件打干净 zip，走 Portal REST API 上传：

```bash
# staging 目录在根 target/central-staging；剔除污染文件后打包
cd target/central-staging
find . -type f ! -name "_remote.repositories*" ! -name "maven-metadata-local.xml" \
  -exec rsync -R {} /tmp/clean-staging/ \; && cd /tmp/clean-staging && zip -r ../bundle.zip .
# 上传并自动发布（token 即 settings.xml 里 sonatype-central 的用户名/密码）
curl -X POST "https://central.sonatype.com/api/v1/publisher/upload?publish=true" \
  -u "$TOKEN_USER:$TOKEN_PASS" -F "bundle=@/tmp/bundle.zip"
# 轮询（注意 /publisher/status 端点可能 500，用列表端点代替）
curl -u "$TOKEN_USER:$TOKEN_PASS" \
  "https://central.sonatype.com/api/v1/publisher/deployments?pageSize=5"
```

状态机：PENDING → VALIDATING/FAILED → PUBLISHING → PUBLISHED；PUBLISHED 后 repo1.maven.org
同步最长约 30 分钟。已发布版本不可覆盖重传。

注意：本机 Maven 走 aliyun 镜像（`mirrorOf: *`），若插件解析异常需检查镜像是否放行。

## 其他约定

- 不要引入 commons-crypto / commons-lang3（已移除，无第三方加密依赖）。
- core 保持 Java 8 语法（无 var/record/switch 表达式）；jakarta 模块可用 Java 17。
- 对外 API 变更需升版本号并同步 README 变更记录与本文档。
