package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Component;

@Component
public class test {
    public <T> void fanxing(T t){
        Class<?> aClass = t.getClass();
        String s = aClass.toString();
        System.out.println(s);
    }
}
