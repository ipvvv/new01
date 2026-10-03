package com.example.framework.core.test;

import java.math.BigInteger;

import com.example.framework.core.Context;
import com.example.framework.core.MinUnit;
import com.example.framework.core.State;

/**
 * 一个磁带格：继承 MinUnit 的 Context，住在世界线上，地址即磁带位置。
 * 符号存在 {@code params()} 里（键 {@link #SYMBOL}）；它是纯数据居民：
 * 不覆盖 {@code to()} 的行为，只被读写头的 setSymbol/readSymbol 摆弄。
 *
 * <p>该类为 {@link UniversalMachine} 与 {@link ProtocolTuringMachine} 共用的
 * 独立顶层类（历史版本曾作为 UniversalMachine 的内部类存在）。
 */
public final class TapeCell extends MinUnit {

    /** params 的键名：本格存取的磁带符号（Integer，缺失视为空白 0）。 */
    private static final String SYMBOL = "symbol";

    /**
     * 在绝对地址 address 创建一格空白磁带（符号 0）。
     * 构造即 {@code init()} 入住世界线——每个磁带格都是正式居民。
     */
    public TapeCell(BigInteger address) {
        super("turing", "tape", address);
        symbol(0);
        init(); // 每个磁带格都是世界线的正式居民
    }

    /** 读取本格符号（缺失视为空白 0）。 */
    public int symbol() {
        Object v = params().get(SYMBOL);
        return v == null ? 0 : ((Number) v).intValue();
    }

    /** 写入本格符号。 */
    public void symbol(int value) {
        params().put(SYMBOL, value);
    }

    // ---- MinUnit 抽象钩子 ----

    /** 磁带格永远“活着”：纯数据居民不参与执行，故恒为 RUNNING。 */
    @Override
    public String state() {
        return State.RUNNING;
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

    /** 入住世界线的钩子：格子无需额外初始化动作。 */
    @Override
    public void initHook(Context ctx) {
    }
}
