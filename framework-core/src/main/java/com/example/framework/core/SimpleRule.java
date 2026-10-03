package com.example.framework.core;

import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * 简单的规则实现：条件 + 动作产生器 + 顺序 + 是否终结。
 *
 * <p>把“条件分支”落到具体规则：{@link #matches(Context, Event)} 用 {@link Condition}
 * 判定，{@link #apply(Context, Event)} 用动作产生器给出 {@link Action} 列表。
 * 动作可以静态给定，也可以根据上下文动态生成。
 */
public class SimpleRule implements Rule {

    private final int order;
    private final boolean terminal;
    private final Condition condition;
    private final BiFunction<Context, Event, List<Action>> producer;

    public SimpleRule(Condition condition, List<Action> actions) {
        this(0, false, condition, (ctx, evt) -> actions);
    }

    public SimpleRule(int order, boolean terminal, Condition condition, List<Action> actions) {
        this(order, terminal, condition, (ctx, evt) -> actions);
    }

    public SimpleRule(int order, boolean terminal, Condition condition,
            BiFunction<Context, Event, List<Action>> producer) {
        this.order = order;
        this.terminal = terminal;
        this.condition = Objects.requireNonNull(condition);
        this.producer = Objects.requireNonNull(producer);
    }

    @Override
    public int order() {
        return order;
    }

    @Override
    public boolean matches(Context context, Event event) {
        return condition.test(context, event);
    }

    @Override
    public List<Action> apply(Context context, Event event) {
        return producer.apply(context, event);
    }

    @Override
    public boolean terminal() {
        return terminal;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int order;
        private boolean terminal;
        private Condition condition = Condition.always();
        private BiFunction<Context, Event, List<Action>> producer = (ctx, evt) -> List.of();

        public Builder order(int order) {
            this.order = order;
            return this;
        }

        public Builder terminal(boolean terminal) {
            this.terminal = terminal;
            return this;
        }

        public Builder when(Condition condition) {
            this.condition = condition;
            return this;
        }

        public Builder then(List<Action> actions) {
            this.producer = (ctx, evt) -> actions;
            return this;
        }

        public Builder then(BiFunction<Context, Event, List<Action>> producer) {
            this.producer = producer;
            return this;
        }

        public SimpleRule build() {
            return new SimpleRule(order, terminal, condition, producer);
        }
    }
}
