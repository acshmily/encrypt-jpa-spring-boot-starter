package com.github.acshmily.interceptor;

import com.github.acshmily.annotation.Encrypt;

import com.github.acshmily.annotation.EncryptParam;
import com.github.acshmily.config.EncryptProperties;
import com.github.acshmily.utils.EncryptUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.data.domain.Page;


import javax.annotation.Resource;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.ShortBufferException;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * @author: Huanghz
 * description: Aop拦截
 * date:Created in 4:30 下午 2020/7/2
 * modifyBy:
 **/
@Aspect
public class EncryptInterceptor{

    /**
     * 保存切入点
     */
    @Pointcut("execution(public * org.springframework.data.jpa.repository.JpaRepository+.save(..))")
    public void save(){ }
    /**
     * 查询切入点
     */
    @Pointcut("execution(public * org.springframework.data.jpa.repository.JpaRepository+.find*(..))")
    public void find(){ }


    @Around(value = "save() || find()")
    public Object doEncrypt(ProceedingJoinPoint jp) throws Throwable {
        Object[] args = jp.getArgs();
        if(args.length <= 0){
            return jp.proceed();
        }
        MethodSignature signature= (MethodSignature) jp.getSignature();
        Annotation[][] parameterAnnotations= signature.getMethod().getParameterAnnotations();

        for (Annotation[] parameterAnnotation: parameterAnnotations) {
            int paramIndex = ArrayUtils.indexOf(parameterAnnotations, parameterAnnotation);
            for (Annotation annotation: parameterAnnotation) {
                if (annotation instanceof EncryptParam){
                    // 获取有该注解的值
                    Object paramValue = args[paramIndex];
                    log.debug("该注解的参数值为:{}",paramValue);
                    if(args[paramIndex] instanceof String){
                        String encryptString = EncryptUtils.encrypt((String)args[paramIndex],encryptProperties.getKey(),encryptProperties.getIv());
                        args[paramIndex] = encryptString;

                    }
                }
            }
        }
        // ######对象字段加密判断
        // 1.判断该字段是否有注解
        Field[] fields = args[0].getClass().getDeclaredFields();
        for(Field field : fields){
            if(field.getAnnotation(Encrypt.class) != null){
                // 2.加密字段所在值,并且值对String属性生效
                if (field.getGenericType().toString().equals("class java.lang.String")) {
                    Method get = jp.getArgs()[0].getClass().getMethod("get"+ getMethodName(field.getName()));
                    String val = (String) get.invoke(jp.getArgs()[0]);
                    // 2.1 加密
                    Method set = jp.getArgs()[0].getClass().getMethod("set"+ getMethodName(field.getName()),String.class);
                    set.invoke(jp.getArgs()[0], EncryptUtils.encrypt(val,encryptProperties.getKey(),encryptProperties.getIv()));
                }
            }

        }
        return jp.proceed(args);
    }

    @AfterReturning(value = "find()",returning = "result")
    public void doDeEncrypt(JoinPoint jp,Object result) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, BadPaddingException, InvalidKeyException, IOException, ShortBufferException, IllegalBlockSizeException, InvalidAlgorithmParameterException {
        // if result is list
        if(result instanceof List){
            List temp1 = (List) result;
            for (Object obj:temp1){
                deEncrypt(obj);
            }
            // if Result is Page of Jpa
        }else if(result instanceof Page){
            Page page = (Page) result;
            List content = page.getContent();
            for (Object obj:content){
                deEncrypt(obj);
            }
        } else if(result instanceof Optional){
            Optional optional = (Optional) result;
            if(optional.isPresent()){
                deEncrypt(optional.get());
            }
        }else {
            deEncrypt(result);
        }
    }


    private  void deEncrypt(Object obj) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, BadPaddingException, InvalidKeyException, IOException, ShortBufferException, IllegalBlockSizeException, InvalidAlgorithmParameterException {
        if(Objects.nonNull(obj)){
            Field[] fields = obj.getClass().getDeclaredFields();
            for(Field field : fields){
                if(field.getAnnotation(Encrypt.class) != null){
                    // 2.加密字段所在值,并且值对String属性生效
                    if (field.getGenericType().toString().equals("class java.lang.String")) {
                        Method get = obj.getClass().getMethod("get"+ getMethodName(field.getName()));

                        String val = (String) get.invoke(obj);
                        // 2.1 加密
                        Method set = obj.getClass().getMethod("set"+ getMethodName(field.getName()),String.class);
                        set.invoke(obj,EncryptUtils.deEncrypt(val,encryptProperties.getKey(),encryptProperties.getIv()));
                    }
                }

            }
        }
    }


    /**
     * 获取Get方法名
     * @param fieldName  fieldName
     * @return string
     */
    private static String getMethodName(String fieldName) {
        byte[] items = fieldName.getBytes();
        items[0] = (byte) ((char) items[0] - 'a' + 'A');
        return new String(items);
    }

    private static final Logger log = LoggerFactory.getLogger(EncryptInterceptor.class);
    @Resource
    private EncryptProperties encryptProperties;
}
