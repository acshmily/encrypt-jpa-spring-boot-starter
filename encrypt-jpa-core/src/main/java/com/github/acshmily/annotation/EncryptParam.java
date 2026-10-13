package com.github.acshmily.annotation;

import java.lang.annotation.*;

/**
 * @author: Huanghz
 * description: 字段加密
 * date:Created in 10:10 上午 2020/9/9
 * modifyBy:
 **/
@Documented
@Target(value = {ElementType.PARAMETER})
@Retention(value = RetentionPolicy.RUNTIME)
public @interface EncryptParam {
}
