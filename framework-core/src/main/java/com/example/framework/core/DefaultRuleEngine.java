package com.example.framework.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 默认规则引擎：{@link RuleEngine} 的具体实现。
 *
 * <p>执行流程：
 * <ol>
 *   <li>规则按 {@link Rule#order()} 升序排序；</li>
 *   <li>逐条 {@link Rule#matches(Context, Event)} 匹配，命中则收集
 *       {@link Rule#apply(Context, Event)} 产生的 {@link Action}；</li>
 *   <li>命中 {@link Rule#terminal()} 为真的规则后短路终止；</li>
 *   <li>可选：通过 {@link ActionStrategyFactory} 执行每个动作。</li>
 * </ol>
 *
 * <p>这就是“条件分支”的执行器：分支 = 规则匹配，动作 = 分支的执行体，
 * 顺序/终结 = 分支的优先级与短路语义。
 */
public class DefaultRuleEngine implements RuleEngine {

    private final List<Rule> rules;
    private final ActionStrategyFactory strategyFactory;

    public DefaultRuleEngine(List<Rule> rules) {
        this(rules, null);
    }

    public DefaultRuleEngine(List<Rule> rules, ActionStrategyFactory strategyFactory) {
        Objects.requireNonNull(rules);
        List<Rule> sorted = new ArrayList<>(rules);
        sorted.sort(Comparator.comparingInt(Rule::order));
        this.rules = List.copyOf(sorted);
        this.strategyFactory = strategyFactory;
    }

    @Override
    public List<Action> fire(Context context, Event event) {
        List<Action> fired = new ArrayList<>();
        for (Rule rule : rules) {
            if (!rule.matches(context, event)) {
                continue;
            }
            List<Action> actions = rule.apply(context, event);
            if (actions != null) {
                for (Action action : actions) {
                    fired.add(action);
                    execute(context, action);
                }
            }
            if (rule.terminal()) {
                break;
            }
        }
        return List.copyOf(fired);
    }

    /** 已按顺序排序的规则（不可变）。 */
    public List<Rule> rules() {
        return rules;
    }

    private void execute(Context context, Action action) {
        if (strategyFactory == null) {
            return;
        }
        ActionStrategy strategy = strategyFactory.create(action);
        if (strategy != null) {
            strategy.run(context, action);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /** 流式构建器：可扩展地装配规则与动作执行策略。 */
    public static final class Builder {
        private final List<Rule> rules = new ArrayList<>();
        private ActionStrategyFactory strategyFactory;

        public Builder rule(Rule rule) {
            rules.add(rule);
            return this;
        }

        public Builder rule(Condition condition, Action... actions) {
            rules.add(new SimpleRule(0, false, condition, List.of(actions)));
            return this;
        }

        public Builder rule(int order, Condition condition, Action... actions) {
            rules.add(new SimpleRule(order, false, condition, List.of(actions)));
            return this;
        }

        public Builder terminal(int order, Condition condition, Action... actions) {
            rules.add(new SimpleRule(order, true, condition, List.of(actions)));
            return this;
        }

        public Builder strategy(ActionStrategyFactory strategyFactory) {
            this.strategyFactory = strategyFactory;
            return this;
        }

        public DefaultRuleEngine build() {
            return new DefaultRuleEngine(rules, strategyFactory);
        }
    }
}
