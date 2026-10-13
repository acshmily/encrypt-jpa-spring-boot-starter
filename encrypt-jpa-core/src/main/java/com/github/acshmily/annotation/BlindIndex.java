package com.github.acshmily.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在<b>影子索引字段</b>上（2.1.0 新增），为 {@link Encrypt} 源字段建立 HMAC 盲索引，
 * 使密文字段支持 LIKE 模糊查询。
 *
 * <p>用法：在实体上声明一个普通 String 字段承接索引，本库在加密时自动填充，
 * 业务代码不读不写：</p>
 *
 * <pre>{@code
 * @Encrypt(random = true)                       // 有盲索引后建议随机 IV（安全性最优）
 * private String phone;
 *
 * @BlindIndex("phone")                          // value = 源字段名（必填）
 * @Column(name = "phone_bidx", length = 4000)   // 影子列务必加长，见下
 * private String phoneBidx;
 * }</pre>
 *
 * <p>查询侧配合 {@link BlindIndexParam}：</p>
 * <pre>{@code
 * List<User> findByPhoneBidxContaining(@BlindIndexParam String keyword); // 模糊 LIKE
 * }</pre>
 *
 * <p>索引列格式（顺序 n-gram + 连续片段匹配，详见 AGENTS.md「盲索引」章节）：
 * 归一化（trim + 小写）后按 UTF-16 字符滑动切分 2-gram 与 1-gram，每个 gram 计算
 * HMAC-SHA256 截断 8 字节（16 个 hex 字符），两段各自按原文顺序逗号拼接。</p>
 *
 * <p><b>存储放大</b>：每字符约 34 个 hex 字符，默认 varchar(255) 只够约 7 个字符的明文，
 * 影子列建议 {@code length >= 4000} 或 TEXT 类型。</p>
 *
 * <p>限制：LIKE 为全表扫描（适用中小数据量）；等值高频场景仍建议确定性加密 +
 * {@code @EncryptParam}（可建 DB 索引）；不支持无序多词与正则；64-bit token 碰撞
 * 概率在数百万行级别才接近生日界，命中后可应用层复核兜底。</p>
 *
 * @author Huanghz
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface BlindIndex {

    /**
     * 建立索引的源字段名（须为带 {@link Encrypt} 的 String 字段，沿类层级查找）。
     */
    String value();
}
