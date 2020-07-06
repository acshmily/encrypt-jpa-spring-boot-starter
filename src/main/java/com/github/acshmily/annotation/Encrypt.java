package com.github.acshmily.annotation;

import java.lang.annotation.*;

/**
 * @author: Huanghz
 * description: 注解声明
 * date:Created in 4:08 下午 2020/7/2
 * modifyBy:
 **/
@Documented
@Target(value = {ElementType.FIELD})
@Retention(value = RetentionPolicy.RUNTIME)
public @interface Encrypt {
}
