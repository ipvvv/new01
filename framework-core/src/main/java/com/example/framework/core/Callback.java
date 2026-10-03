package com.example.framework.core;

@FunctionalInterface
public interface Callback {
    public abstract void run(Context now, String flag);
}
