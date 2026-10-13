package com.github.acshmily.interceptor;

import com.github.acshmily.config.EncryptProperties;
import com.github.acshmily.exception.EncryptException;
import com.github.acshmily.utils.EncryptCacheContainer;
import com.github.acshmily.utils.EncryptField;
import com.github.acshmily.utils.EncryptUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data Repository 切面拦截器（2.0.0 重写）。
 *
 * <p>切入点为 {@code Repository+} 的<b>全部</b> public 方法，相较 1.x 只拦截
 * {@code save}/{@code find*}，现覆盖 {@code saveAll}、{@code getBy/readBy/queryBy/countBy/existsBy/deleteBy}
 * 派生查询与自定义 {@code @Query} 方法；未命中加密条件的类/方法命中负缓存后开销接近零。</p>
 *
 * <p>加密模式：{@code @EncryptParam} 参数恒为确定性加密（等值查询需要密文相等才能命中）；
 * 实体字段按 {@code @Encrypt(random)} 选择——默认确定性（可查询），{@code random = true}
 * 时随机 IV（不可查询，安全性最优）。</p>
 *
 * <p>相对 1.x 修复的缺陷：</p>
 * <ul>
 *   <li>参数注解缓存下标错位（非首位的 {@code @EncryptParam} 参数加密了错误参数）；</li>
 *   <li>{@code args[0]} 为 null、字段值为 null 时的 NPE；</li>
 *   <li>getter/setter 反射调用改为<b>直接字段访问</b>：不再依赖 JavaBean 命名（1.x 对
 *       非小写开头字段名生成的访问器名是错的），覆盖父类字段，且不会触发 Hibernate 懒加载代理；</li>
 *   <li>同一实例重复 {@code save} 双重加密——已带 v2 前缀的值跳过加密；</li>
 *   <li>{@code findAll()} 等返回 {@code Iterable} 的结果不解密——现支持 {@code Iterable}
 *       （含 List/Page/Slice）、{@code Optional} 与单对象；</li>
 *   <li>存量明文/旧数据导致解密异常炸掉查询——解密失败保留原值并记录日志。</li>
 * </ul>
 *
 * <p>已知设计约定（与 1.x 一致，文档化）：save 后实体内存中的字段保持<b>密文</b>；
 * 查询返回的实体字段被替换为<b>明文</b>，若外层事务非只读需注意脏检查回写风险。</p>
 *
 * @author Huanghz
 */
@Aspect
public class EncryptInterceptor {

    private static final Logger log = LoggerFactory.getLogger(EncryptInterceptor.class);

    private final EncryptProperties encryptProperties;
    private final EncryptCacheContainer cacheContainer;

    public EncryptInterceptor(EncryptProperties encryptProperties, EncryptCacheContainer cacheContainer) {
        this.encryptProperties = encryptProperties;
        this.cacheContainer = cacheContainer;
    }

    /**
     * 拦截所有 Spring Data 仓库的 public 方法（Repository+ 覆盖
     * JpaRepository/CrudRepository/PagingAndSortingRepository 及自定义片段接口）。
     */
    @Pointcut("execution(public * org.springframework.data.repository.Repository+.*(..))")
    public void anyRepositoryMethod() {
    }

    @Around("anyRepositoryMethod()")
    public Object aroundRepositoryMethod(ProceedingJoinPoint jp) throws Throwable {
        Object[] args = jp.getArgs();
        Method method = ((MethodSignature) jp.getSignature()).getMethod();

        encryptParameters(method, args);
        encryptEntityArguments(args);

        Object result = jp.proceed(args);

        return decryptResult(result);
    }

    /** 加密带 @EncryptParam 的 String 参数（确定性模式；下标来自缓存，1.x 下标错位缺陷已修复）。已是 v2 密文的值跳过，防止二次加密。 */
    private void encryptParameters(Method method, Object[] args) {
        int[] indexes = cacheContainer.encryptParamIndexesOf(method);
        if (indexes.length == 0) {
            return;
        }
        for (int index : indexes) {
            if (index < args.length && args[index] instanceof String) {
                String value = (String) args[index];
                if (value.startsWith(EncryptUtils.V2_PREFIX)) {
                    continue;
                }
                args[index] = EncryptUtils.encryptDeterministic(value,
                        encryptProperties.getKey(), encryptProperties.getIv());
            }
        }
    }

    /**
     * 加密实体参数：覆盖 save、saveAll(Iterable) 等。已带 v2 前缀的值视为已加密直接跳过，
     * 防止同一实例重复保存时双重加密；null 字段跳过。
     */
    private void encryptEntityArguments(Object[] args) {
        for (Object arg : args) {
            if (arg == null) {
                continue;
            }
            if (arg instanceof Iterable) {
                for (Object element : (Iterable<?>) arg) {
                    encryptEntity(element);
                }
            } else {
                encryptEntity(arg);
            }
        }
    }

    private void encryptEntity(Object entity) {
        if (entity == null) {
            return;
        }
        List<EncryptField> fields = cacheContainer.encryptFieldsOf(entity.getClass());
        if (fields.isEmpty()) {
            return;
        }
        try {
            for (EncryptField encryptField : fields) {
                Object raw = encryptField.getField().get(entity);
                if (!(raw instanceof String)) {
                    continue;
                }
                String value = (String) raw;
                if (value.isEmpty() || value.startsWith(EncryptUtils.V2_PREFIX)) {
                    continue;
                }
                String cipherText = encryptField.isRandom()
                        ? EncryptUtils.encryptRandom(value, encryptProperties.getKey())
                        : EncryptUtils.encryptDeterministic(value, encryptProperties.getKey(), encryptProperties.getIv());
                encryptField.getField().set(entity, cipherText);
            }
        } catch (IllegalAccessException e) {
            throw new EncryptException("加密实体字段失败: " + entity.getClass().getName(), e);
        }
    }

    /** 解密返回结果：Iterable（含 List/Page/Slice/findAll）、Optional、单对象 */
    private Object decryptResult(Object result) {
        if (result == null) {
            return null;
        }
        if (result instanceof Optional) {
            ((Optional<?>) result).ifPresent(this::decryptEntity);
            return result;
        }
        if (result instanceof Iterable) {
            for (Object element : (Iterable<?>) result) {
                decryptEntity(element);
            }
            return result;
        }
        decryptEntity(result);
        return result;
    }

    /**
     * 逐字段解密。解密失败（存量明文、脏数据、密钥变更后的旧密文）保留原值，
     * 不让查询抛异常——1.x 在此直接把 BadPaddingException 抛给了调用方。
     */
    private void decryptEntity(Object entity) {
        if (entity == null) {
            return;
        }
        List<EncryptField> fields = cacheContainer.encryptFieldsOf(entity.getClass());
        if (fields.isEmpty()) {
            return;
        }
        try {
            for (EncryptField encryptField : fields) {
                Field field = encryptField.getField();
                Object raw = field.get(entity);
                if (!(raw instanceof String) || ((String) raw).isEmpty()) {
                    continue;
                }
                try {
                    field.set(entity, EncryptUtils.decrypt((String) raw,
                            encryptProperties.getKey(), encryptProperties.getIv()));
                } catch (EncryptException e) {
                    log.debug("字段 [{}.{}] 不是可解密的密文，按原值返回",
                            entity.getClass().getSimpleName(), field.getName(), e);
                }
            }
        } catch (IllegalAccessException e) {
            throw new EncryptException("解密实体字段失败: " + entity.getClass().getName(), e);
        }
    }
}
