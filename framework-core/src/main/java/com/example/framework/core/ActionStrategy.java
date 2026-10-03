package com.example.framework.core;

@FunctionalInterface
public interface ActionStrategy {
    void run(Context context, Action action);
}
