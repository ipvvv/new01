package com.example.framework.core;

import java.math.BigInteger;
import java.util.Map;

/**
* now cannot be null
*/
public interface Context {

    default int cap() {
        return 1024;
    }

    default String key() {
        return cluster() + group() + address().toString();
    };

    String state();

    String cluster();

    String group();

    BigInteger address();

    BigInteger version();

    Context pre();

    Context next();

    Context skip(BigInteger interval);

    Map<String, Object> params();

    Context ctx();

    Context to(MinUnit now) throws Exception;

    Context AI(MinUnit now, String flag);

    void doit(MinUnit now);

    void pausing(Context now);

    void success(Context now);

    void refreshContext(Context now);

    void replaying(Context past, Context now);

    void findNewPerson(Context now);

    void contextNotMatch(Context now);

    default boolean samePeople(Context now) {
        return now != null && this.cluster().equals(now.cluster()) && this.group().equals(now.group());
    }

    void boom(Context now);

    default boolean limit() {
        return true;
    }

    void stateTransition(Context now);

    void causalChain(Context now);

    void refreshIndex(Context now);

    default BigInteger moveForward(BigInteger time, BigInteger step) {
        return time.add(step);
    }

    void callback(Context now);

    void pleaseReflect(Context now);

    BigIndexArrayList<Context> worldLine();

    BigInteger cursor();

    /**
     * 状态转移决策点：给定 flag，返回应落入的状态。
     * 默认映射：PAUSE→PAUSING、DESTROY→DESTROYING、RUNNING→RUNNING、其余→OTHER。
     * 子类可覆盖以自定义（如 TestUnit 打点），也可以直接继承默认实现
     * （如 UniversalMachine/BootstrapMachine 不再需要样板代码）。
     */
    default String say(Context now, String flag) {
        if (State.PAUSE.equals(flag)) {
            return State.PAUSING;
        } else if (State.DESTROY.equals(flag)) {
            return State.DESTROYING;
        } else if (State.RUNNING.equals(flag)) {
            return State.RUNNING;
        }
        return State.OTHER;
    }

    void initHook(Context ctx);

    default Context zip(Context ctx) {
        return null;
    }

    default Context safe(Context ctx) {
        return null;
    }

    Context search(BigInteger address);

    void cursor(BigInteger cursor);
}