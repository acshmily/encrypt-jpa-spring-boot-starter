package com.github.acshmily.utils;


import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author: Huanghz
 * description: 缓存容器
 * date:Created in 8:54 上午 2020/9/14
 * modifyBy:
 **/
public class EncryptCacheContainer {
    /**
     * 添加缓存
     * @param clazz
     * @param fields
     */
    public void addClassFields(Class clazz,Field[] fields){
        cacheClassFields.put(clazz,fields);
    }

    /**
     * 获取缓存
     * @param clazz
     * @return
     */
    public Field[] getClassFields(Class clazz){
        return cacheClassFields.get(clazz);
    }

    /**
     * 判断缓存是否命中
     * @param clazz
     * @return
     */
    public boolean existClassFields(Class clazz){
        return cacheClassFields.containsKey(clazz);
    }

    /**
     * 添加方法缓存
     * @param methodSignature
     * @param annotations
     */
    public void addMethodSignature(Method methodSignature,Annotation[][] annotations){
        cacheMethodSignatures.put(methodSignature,annotations);
    }
    /**
     * 判断缓存是否命中
     * @param methodSignature
     * @return
     */
    public boolean existMethodSignature(Method methodSignature){
        return cacheMethodSignatures.containsKey(methodSignature);
    }

    /**
     * 获取方法缓存
     * @param methodSignature
     * @return
     */
    public Annotation[][] getMethodSignature(Method methodSignature){
        return cacheMethodSignatures.get(methodSignature);
    }

    private ConcurrentHashMap<Class, Field[]> cacheClassFields = new ConcurrentHashMap<>();

    private ConcurrentHashMap<Method, Annotation[][]> cacheMethodSignatures = new ConcurrentHashMap<>();

}
