package com.github.acshmily.interceptor;

import com.github.acshmily.annotation.Encrypt;
import com.github.acshmily.config.EncryptProperties;
import com.github.acshmily.utils.EncryptUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.domain.Page;

import javax.annotation.Resource;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.ShortBufferException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * @Author: Huanghz
 * @Description: Aop拦截
 * @Date:Created in 4:30 下午 2020/7/2
 * @ModifyBy:
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

    /**
     * 保存前处理
     * @return
     */
    @Before(value = "save()")
    public void doEncrypt(JoinPoint jp) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, BadPaddingException, InvalidKeyException, IOException, ShortBufferException, IllegalBlockSizeException, InvalidAlgorithmParameterException {
        // 1.判断该字段是否有注解
        Field[] fields = jp.getArgs()[0].getClass().getDeclaredFields();
        for(Field field : fields){
            if(field.getAnnotation(Encrypt.class) != null){
                // 2.加密字段所在值,并且值对String属性生效
                if (field.getGenericType().toString().equals("class java.lang.String")) {
                    Method get = (Method) jp.getArgs()[0].getClass().getMethod("get"+ getMethodName(field.getName()));
                    String val = (String) get.invoke(jp.getArgs()[0]);
                    // 2.1 加密
                    Method set = (Method) jp.getArgs()[0].getClass().getMethod("set"+ getMethodName(field.getName()),String.class);
                    set.invoke(jp.getArgs()[0], EncryptUtils.encrypt(val,encryptProperties.getKey(),encryptProperties.getIv()));
                }
            }

        }

    }

    /**
     * 查询出来将注解解密
     * @param jp
     * @param result
     * @throws NoSuchMethodException
     * @throws InvocationTargetException
     * @throws IllegalAccessException
     * @throws BadPaddingException
     * @throws InvalidKeyException
     * @throws IOException
     * @throws ShortBufferException
     * @throws IllegalBlockSizeException
     * @throws InvalidAlgorithmParameterException
     */
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

    /**
     * 解密封装方法
     * @param obj
     * @throws NoSuchMethodException
     * @throws InvocationTargetException
     * @throws IllegalAccessException
     * @throws BadPaddingException
     * @throws InvalidKeyException
     * @throws IOException
     * @throws ShortBufferException
     * @throws IllegalBlockSizeException
     * @throws InvalidAlgorithmParameterException
     */

    private  void deEncrypt(Object obj) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, BadPaddingException, InvalidKeyException, IOException, ShortBufferException, IllegalBlockSizeException, InvalidAlgorithmParameterException {
        Field[] fields = obj.getClass().getDeclaredFields();
        for(Field field : fields){
            if(field.getAnnotation(Encrypt.class) != null){
                // 2.加密字段所在值,并且值对String属性生效
                if (field.getGenericType().toString().equals("class java.lang.String")) {
                    Method get = (Method) obj.getClass().getMethod("get"+ getMethodName(field.getName()));

                    String val = (String) get.invoke(obj);
                    // 2.1 加密
                    Method set = (Method) obj.getClass().getMethod("set"+ getMethodName(field.getName()),String.class);
                    set.invoke(obj,EncryptUtils.deEncrypt(val,encryptProperties.getKey(),encryptProperties.getIv()));
                }
            }

        }
    }


    /**
     * 获取Get方法名
     * @param fieldName
     * @return
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
