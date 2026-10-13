package com.github.acshmily.utils;

import java.lang.reflect.Field;

/**
 * 带加密元数据的字段缓存条目：反射 {@link Field} 与其 {@code @Encrypt(random)} 标志，
 * 避免拦截热路径上重复读取注解。
 *
 * @author Huanghz
 */
public final class EncryptField {

    private final Field field;
    private final boolean random;

    EncryptField(Field field, boolean random) {
        this.field = field;
        this.random = random;
    }

    public Field getField() {
        return field;
    }

    /** 是否随机 IV 加密（true 时该字段不可用于等值查询） */
    public boolean isRandom() {
        return random;
    }
}
