package com.example.framework.core;

@FunctionalInterface
public interface ActionStrategyFactory {
    ActionStrategy create(Action action);
}
