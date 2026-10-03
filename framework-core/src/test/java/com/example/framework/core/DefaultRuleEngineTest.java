package com.example.framework.core;

import static com.example.framework.core.RuleEngineTestSupport.context;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * {@link DefaultRuleEngine} 的单元测试：顺序、终结短路、跳过、动作执行、动态动作。
 */
class DefaultRuleEngineTest {

    private final Context running = context("RUNNING", "clusterA", "groupX", Map.of("age", 18));

    @Test
    void firesMatchingRulesInOrder() {
        Action a1 = new Action("log", Map.of("msg", "one"));
        Action a2 = new Action("log", Map.of("msg", "two"));

        DefaultRuleEngine engine = DefaultRuleEngine.builder()
                .rule(2, Condition.state("RUNNING"), a1)
                .rule(1, Condition.state("RUNNING"), a2)
                .build();

        List<Action> fired = engine.fire(running, new To());
        assertEquals(List.of(a2, a1), fired); // order=1 先于 order=2
    }

    @Test
    void terminalRuleShortCircuits() {
        Action first = new Action("log", Map.of());
        Action second = new Action("log", Map.of());
        Action never = new Action("log", Map.of());

        DefaultRuleEngine engine = DefaultRuleEngine.builder()
                .terminal(0, Condition.state("RUNNING"), first)
                .rule(1, Condition.always(), second)
                .rule(2, Condition.always(), never)
                .build();

        List<Action> fired = engine.fire(running, new To());
        assertEquals(List.of(first), fired); // terminal 后短路
    }

    @Test
    void nonMatchingRulesAreSkipped() {
        Action hit = new Action("log", Map.of());
        Action miss = new Action("log", Map.of());

        DefaultRuleEngine engine = DefaultRuleEngine.builder()
                .rule(0, Condition.state("RUNNING"), hit)
                .rule(1, Condition.state("DESTROYING"), miss)
                .build();

        assertEquals(List.of(hit), engine.fire(running, new To()));
    }

    @Test
    void actionsAreExecutedViaStrategyFactory() {
        List<Action> executed = new ArrayList<>();
        ActionStrategyFactory factory = action -> (ctx, act) -> executed.add(act);

        DefaultRuleEngine engine = DefaultRuleEngine.builder()
                .strategy(factory)
                .rule(0, Condition.always(), new Action("log", Map.of()))
                .rule(1, Condition.always(), new Action("notify", Map.of()))
                .build();

        engine.fire(running, new To());
        assertEquals(2, executed.size());
    }

    @Test
    void dynamicActionsFromContext() {
        DefaultRuleEngine engine = DefaultRuleEngine.builder()
                .rule(new SimpleRule(0, false, Condition.hasParam("age"),
                        (ctx, evt) -> List.of(new Action("echo", Map.of("age", ctx.params().get("age"))))))
                .build();

        List<Action> fired = engine.fire(running, new To());
        assertEquals(1, fired.size());
        assertEquals(18, fired.get(0).attributes().get("age"));
    }

    @Test
    void customConditionLambda() {
        DefaultRuleEngine engine = DefaultRuleEngine.builder()
                .rule(0, (ctx, evt) -> ctx.params().getOrDefault("age", 0) instanceof Integer i && i >= 18,
                        new Action("adult", Map.of()))
                .build();

        assertEquals(1, engine.fire(running, new To()).size());
        assertTrue(engine.fire(context("RUNNING", "c", "g", Map.of("age", 10)), new To()).isEmpty());
    }
}
