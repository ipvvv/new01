package com.example.framework.core;

import java.util.List;

public interface Rule{

    default int order() {
        return 0;
    }

    boolean matches(Context context, Event event);

    List<Action> apply(Context context, Event event);

    default boolean terminal() {
        return false;
    }
}