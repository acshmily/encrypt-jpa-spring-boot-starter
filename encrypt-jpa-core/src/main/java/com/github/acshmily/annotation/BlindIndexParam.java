package com.github.acshmily.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在仓库方法参数上（2.1.0 新增）：执行查询前，该 String 参数被自动转换为
 * 盲索引 token 片段，配合 Spring Data 的 {@code Containing} 派生查询实现密文 LIKE。
 *
 * <pre>{@code
 * // WHERE phone_bidx LIKE '%<token片段>%'
 * List<User> findByPhoneBidxContaining(@BlindIndexParam String keyword);
 * }</pre>
 *
 * <p>关键词按与写入侧相同的规则归一化并切 gram，因此支持原文任意位置的连续子串
 * （前缀/中缀/后缀/整值）。等值语义也用 {@code Containing}（整值 token 序列即完整片段）。</p>
 *
 * <p>不得与 {@link EncryptParam} 标注在同一参数上（启动后首次调用即 fail-fast）。
 * 转换不幂等：勿把已转换的索引片段当作参数回传。</p>
 *
 * @author Huanghz
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface BlindIndexParam {
}
