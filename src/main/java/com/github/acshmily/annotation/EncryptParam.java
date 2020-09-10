package com.github.acshmily.annotation;

import java.lang.annotation.*;

/**
 * @Author: Huanghz
 * @Description: 字段加密
 * @Date:Created in 10:10 上午 2020/9/9
 * @ModifyBy:
 **/
@Documented
@Target(value = {ElementType.PARAMETER})
@Retention(value = RetentionPolicy.RUNTIME)
public @interface EncryptParam {
}
