package com.example.framework.core;

import static com.example.framework.core.RuleEngineTestSupport.context;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * {@link Condition} 的单元测试：组合、内置判定、事件类型、lambda 扩展。
 */
class ConditionTest {

    private final Context running = context("RUNNING", "clusterA", "groupX",
            Map.of("age", 18, "vip", true));

    @Test
    void combinators() {
        Condition c = Condition.state("RUNNING").and(Condition.param("age", 18));
        assertTrue(c.test(running, null));
        assertFalse(c.test(context("PAUSING"), null));

        assertTrue(c.or(Condition.state("PAUSING")).test(context("PAUSING"), null));
        assertFalse(Condition.state("RUNNING").negate().test(running, null));
    }

    @Test
    void builtInConditions() {
        assertTrue(Condition.cluster("clusterA").test(running, null));
        assertTrue(Condition.group("groupX").test(running, null));
        assertTrue(Condition.hasParam("vip").test(running, null));
        assertFalse(Condition.param("age", 20).test(running, null));
        assertTrue(Condition.always().test(null, null));
        assertFalse(Condition.never().test(null, null));
    }

    @Test
    void eventType() {
        assertTrue(Condition.eventType(To.class).test(running, new To()));
        assertFalse(Condition.eventType(To.class).test(running, null));
    }

    @Test
    void extensibleLambda() {
        Condition custom = (ctx, evt) -> ctx != null
                && Integer.valueOf(18).equals(ctx.params().get("age"));
        assertTrue(custom.test(running, null));
    }
}
