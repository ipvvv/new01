package com.example.framework.core;

import java.math.BigInteger;
import java.util.Map;

/**
 * 测试用的最小 MinUnit 上下文工厂：把抽象钩子补成空实现，
 * 让规则引擎测试可以聚焦在 state/cluster/group/params 上。
 */
final class RuleEngineTestSupport {

    private RuleEngineTestSupport() {
    }

    static Context context(String stateValue) {
        return context(stateValue, "cluster", "group", Map.of());
    }

    static Context context(String stateValue, String cluster, String group, Map<String, Object> params) {
        MinUnit unit = new MinUnit(cluster, group) {
            @Override
            public String state() {
                return stateValue;
            }

            @Override
            public void doit(MinUnit now) {
            }

            @Override
            public void pausing(Context now) {
            }

            @Override
            public void success(Context now) {
            }

            @Override
            public void refreshContext(Context now) {
            }

            @Override
            public void replaying(Context past, Context now) {
            }

            @Override
            public void findNewPerson(Context now) {
            }

            @Override
            public void contextNotMatch(Context now) {
            }

            @Override
            public void callback(Context now) {
            }

            @Override
            public void pleaseReflect(Context now) {
            }

            @Override
            protected void alarm(Context now) {
            }

            @Override
            public void initHook(Context ctx) {
            }

            @Override
            public Context search(BigInteger address) {
                return null;
            }
        };
        if (params != null) {
            unit.params().putAll(params);
        }
        return unit;
    }
}
