package com.example.framework.core;

import java.util.Objects;

/**
 * 规则引擎的“条件分支”抽象 —— 对 (上下文, 事件) 的判定：
 * {@code (Context, Event) -> boolean}。
 *
 * <p>设计要点：
 * <ul>
 *   <li><b>可组合</b>：{@link #and(Condition)} / {@link #or(Condition)} / {@link #negate()}；</li>
 *   <li><b>可扩展</b>：函数式接口，任意 lambda 或实现类都可作为条件；</li>
 *   <li><b>MinUnit 感知</b>：内置基于 {@link Context} 的 {@code state/cluster/group/params} 判定，
 *       直接读取上下文驱动设计中的“上下文”。</li>
 * </ul>
 */
@FunctionalInterface
public interface Condition {

    /** 判定上下文与事件是否满足条件。 */
    boolean test(Context context, Event event);

    /** 逻辑与。 */
    default Condition and(Condition other) {
        Objects.requireNonNull(other);
        return (ctx, evt) -> test(ctx, evt) && other.test(ctx, evt);
    }

    /** 逻辑或。 */
    default Condition or(Condition other) {
        Objects.requireNonNull(other);
        return (ctx, evt) -> test(ctx, evt) || other.test(ctx, evt);
    }

    /** 逻辑非。 */
    default Condition negate() {
        return (ctx, evt) -> !test(ctx, evt);
    }

    /** 恒真。 */
    static Condition always() {
        return (ctx, evt) -> true;
    }

    /** 恒假。 */
    static Condition never() {
        return (ctx, evt) -> false;
    }

    /** 上下文的 state() 等于指定值。 */
    static Condition state(String expected) {
        return (ctx, evt) -> ctx != null && Objects.equals(expected, ctx.state());
    }

    /** 上下文的 cluster() 等于指定值。 */
    static Condition cluster(String expected) {
        return (ctx, evt) -> ctx != null && Objects.equals(expected, ctx.cluster());
    }

    /** 上下文的 group() 等于指定值。 */
    static Condition group(String expected) {
        return (ctx, evt) -> ctx != null && Objects.equals(expected, ctx.group());
    }

    /** 上下文的 params 中指定键等于指定值。 */
    static Condition param(String key, Object value) {
        return (ctx, evt) -> ctx != null && ctx.params() != null
                && Objects.equals(value, ctx.params().get(key));
    }

    /** 上下文的 params 中存在指定键。 */
    static Condition hasParam(String key) {
        return (ctx, evt) -> ctx != null && ctx.params() != null
                && ctx.params().containsKey(key);
    }

    /** 事件类型匹配（{@link Event} 是标记接口，{@link To} 是其实现之一）。 */
    static Condition eventType(Class<? extends Event> type) {
        return (ctx, evt) -> evt != null && type.isInstance(evt);
    }
}
