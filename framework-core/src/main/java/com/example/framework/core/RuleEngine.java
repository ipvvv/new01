package com.example.framework.core;

import java.util.List;

public interface RuleEngine {
    List<Action> fire(Context context, Event event);
}