package com.example.framework.core;

import java.util.Map;

public record Action(String type, Map<String, Object> attributes) {
    public Action {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}