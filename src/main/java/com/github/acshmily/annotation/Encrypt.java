package com.github.acshmily.annotation;

import java.lang.annotation.*;

/**
 * @Author: Huanghz
 * @Description: 注解声明
 * @Date:Created in 4:08 下午 2020/7/2
 * @ModifyBy:
 **/
@Documented
@Target(value = {ElementType.FIELD})
@Retention(value = RetentionPolicy.RUNTIME)
public @interface Encrypt {
}
