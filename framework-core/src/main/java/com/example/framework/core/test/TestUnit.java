package com.example.framework.core.test;

import java.math.BigInteger;

import com.example.framework.core.Context;
import com.example.framework.core.MinUnit;

public class TestUnit extends MinUnit {

    public TestUnit(String cluster, String group, BigInteger index, BigInteger version, String desc) {
        super(cluster, group, index, version, desc);
    }

    @Override 
    public void initHook(Context ctx) {
        System.out.println(String.format("[init][%s]", ctx));
    }

    @Override
    public String state() {
        return null;
    }

    @Override
    public void pausing(Context now) {
        System.out.println(String.format("⏸ [%s]", now));
    }

    @Override
    public void success(Context now) {
        System.out.println(String.format("-> [%s]", now));
    }

    @Override
    public void refreshContext(Context now) {
        System.out.println(String.format("⭕ [%s]", now));
    }

    @Override
    public void replaying(Context past, Context now) {
        System.out.println(String.format("# [%s]", past));
    }

    @Override
    public void findNewPerson(Context now) {
        System.out.println(String.format("👽 [%s]", now));
    }

    @Override
    public void contextNotMatch(Context now) {
        System.out.println(String.format("❌ [%s]", now));
    }

    @Override
    public String say(Context now, String flag) {
        System.out.println(String.format("💡 [%s]", now));

        return super.say(now, flag); // 复用 MinUnit 继承的默认映射：标准 flag 标准态，未知 flag 落 OTHER
    }

    @Override
    public void pleaseReflect(Context now) {
        System.out.println(String.format("💥 [%s]", now));
    }

    @Override
    protected void alarm(Context now) {
        System.out.println(String.format("⚠️ [%s]", now));
    }

    @Override
    public void callback(Context now) {
        
    }

    @Override
    public void doit(MinUnit now) {
        
    }
}
