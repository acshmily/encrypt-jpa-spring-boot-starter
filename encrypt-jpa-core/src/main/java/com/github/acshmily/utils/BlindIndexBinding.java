package com.github.acshmily.utils;

import java.lang.reflect.Field;

/**
 * 盲索引绑定缓存条目：源字段（明文 -> 密文）与影子索引字段的配对。
 *
 * @author Huanghz
 */
public final class BlindIndexBinding {

    private final Field sourceField;
    private final Field indexField;

    BlindIndexBinding(Field sourceField, Field indexField) {
        this.sourceField = sourceField;
        this.indexField = indexField;
    }

    public Field getSourceField() {
        return sourceField;
    }

    public Field getIndexField() {
        return indexField;
    }
}
