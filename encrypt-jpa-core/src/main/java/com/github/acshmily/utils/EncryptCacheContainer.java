package com.github.acshmily.utils;

import com.github.acshmily.annotation.Encrypt;
import com.github.acshmily.annotation.EncryptParam;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 反射扫描结果缓存（2.0.0 重写）。
 *
 * <p>修复 1.x 的两类缓存缺陷：</p>
 * <ul>
 *   <li><b>不缓存负结果</b>——没有加密注解的类/方法每次仓库调用都重复反射扫描；现在空结果同样缓存，
 *       热路径开销降为一次 {@link ConcurrentHashMap} 查找。</li>
 *   <li><b>参数注解缓存错位</b>——1.x 只缓存带 {@code @EncryptParam} 的参数的注解数组，
 *       二次调用时注解所在参数下标错乱，导致加密了错误的参数；现在缓存的是注解参数的<b>下标</b>。</li>
 * </ul>
 *
 * <p>字段扫描沿类层级向上遍历（覆盖 {@code @MappedSuperclass} 父类字段，1.x 只扫
 * {@code getDeclaredFields} 会漏掉），并过滤静态/合成字段；{@code Field#setAccessible}
 * 只在首次扫描时执行一次。</p>
 *
 * @author Huanghz
 */
public class EncryptCacheContainer {

    /** 空列表即负缓存：该类没有需要加密的 String 字段 */
    private static final List<EncryptField> NO_FIELDS = Collections.emptyList();

    /** 空数组即负缓存：该方法没有 @EncryptParam 参数 */
    private static final int[] NO_INDEXES = new int[0];

    private final ConcurrentHashMap<Class<?>, List<EncryptField>> classEncryptFields = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Method, int[]> methodEncryptParamIndexes = new ConcurrentHashMap<>();

    /**
     * 获取类（含父类层级）中带 {@code @Encrypt} 注解的 String 字段；首次调用扫描并缓存。
     */
    public List<EncryptField> encryptFieldsOf(Class<?> clazz) {
        List<EncryptField> cached = classEncryptFields.get(clazz);
        if (cached != null) {
            return cached;
        }
        return classEncryptFields.computeIfAbsent(clazz, EncryptCacheContainer::scanEncryptFields);
    }

    /**
     * 获取方法上带 {@code @EncryptParam} 注解的参数下标数组；首次调用扫描并缓存。
     */
    public int[] encryptParamIndexesOf(Method method) {
        int[] cached = methodEncryptParamIndexes.get(method);
        if (cached != null) {
            return cached;
        }
        return methodEncryptParamIndexes.computeIfAbsent(method, EncryptCacheContainer::scanEncryptParamIndexes);
    }

    private static List<EncryptField> scanEncryptFields(Class<?> clazz) {
        List<EncryptField> result = null;
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            // JDK 类没有业务字段
            if (c.getName().startsWith("java.")) {
                break;
            }
            for (Field field : c.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) || field.isSynthetic()) {
                    continue;
                }
                if (field.getType() != String.class) {
                    continue;
                }
                Encrypt encrypt = field.getAnnotation(Encrypt.class);
                if (encrypt == null) {
                    continue;
                }
                field.setAccessible(true);
                if (result == null) {
                    result = new ArrayList<>(4);
                }
                result.add(new EncryptField(field, encrypt.random()));
            }
        }
        return result == null ? NO_FIELDS : result;
    }

    private static int[] scanEncryptParamIndexes(Method method) {
        Annotation[][] parameterAnnotations = method.getParameterAnnotations();
        if (parameterAnnotations.length == 0) {
            return NO_INDEXES;
        }
        int[] indexes = new int[parameterAnnotations.length];
        int count = 0;
        for (int i = 0; i < parameterAnnotations.length; i++) {
            for (Annotation annotation : parameterAnnotations[i]) {
                if (annotation instanceof EncryptParam) {
                    indexes[count++] = i;
                    break;
                }
            }
        }
        return count == indexes.length ? indexes : Arrays.copyOf(indexes, count);
    }
}
