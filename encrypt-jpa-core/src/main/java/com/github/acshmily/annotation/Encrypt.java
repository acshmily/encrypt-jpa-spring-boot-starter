package com.github.acshmily.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注需要加密的 String 实体字段。
 *
 * <p>2.0.0 新增 {@link #random()} 属性：</p>
 * <ul>
 *   <li>{@code random = false}（默认，确定性加密）：相同明文产生相同密文，
 *       该字段可继续配合 {@link EncryptParam} 做等值查询；安全性等同 1.x（存在模式泄露）。</li>
 *   <li>{@code random = true}（随机 IV）：每次加密生成随机 IV，相同明文产生不同密文，
 *       安全性最优，但该字段<b>无法</b>用于等值查询（{@code WHERE col = ?} 永远不命中）。</li>
 * </ul>
 *
 * @author Huanghz
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Encrypt {

    /**
     * 是否使用随机 IV 加密。默认 false（确定性加密，支持等值查询）。
     */
    boolean random() default false;
}
