package com.example.framework.core;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

public abstract class MinUnit implements Context {

    // 无穷世界线
    public static BigIndexArrayList<Context> causalChain;

    /** 状态旗标：INITING/RUNNING/PAUSING/DESTROYING/REPLAYING（见 {@link State}）。 */
    protected String flag = State.INITING;

    /** 描述：{@code toString()} 的第一段，标识这个上下文是谁。 */
    protected String desc;
    /** 集群：身份三元组之一（同一集群才谈“自己人”）。 */
    protected String cluster;
    /** 群组：身份三元组之一（同一群组才谈“自己人”）。 */
    protected String group;
    /** 世界线地址：身份三元组之一，也是入住世界线的位置（允许负数——世界线支持负下标）。 */
    protected BigInteger address;

    /** 版本：因果时间（每次 to/AI 向前 +1），子类可复用为“步数”。 */
    protected BigInteger version;
    /** 游标：世界线扫描位置，子类可复用为“读写头”。 */
    protected BigInteger cursor;

    /**
     * 可见窗口 [minSeen, maxSeen]：本单元接触过（访问/写入）的最小/最大世界线地址。
     * 框架在 {@link #skip}/{@link #refreshIndex} 时自动维护；子类可复用为磁带行为窗口
     * （如 UniversalMachine/BootstrapMachine：写过的格 ∪ 访问过的格）。
     */
    protected BigInteger minSeen;
    protected BigInteger maxSeen;
    
    public Context slave;
    public Context master;
    public Context slave0;

    public Context next;

    static {
        causalChain = new BigIndexArrayList<>();
    }

    /** 参数袋：一切随行数据（子类可在此存 δ、符号、当前状态等）。 */
    protected Map<String, Object> params = new HashMap<>();

    public MinUnit() {
        this("default");
    }

    public MinUnit(String group) {
        this("default", group);
    }

    public MinUnit(String cluster, String group) {
        this(cluster, group, BigInteger.ZERO);
    }

    public MinUnit(String cluster, String group, BigInteger index) {
        this(cluster, group, index, BigInteger.ZERO);
    }

    public MinUnit(String cluster, String group, BigInteger index, BigInteger version) {
        this(cluster, group, index, version, "null");
    }

    public MinUnit(String cluster, String group, BigInteger index, BigInteger version, String desc) {
        if (cluster == null || cluster.isBlank() || group == null || group.isBlank() || index == null
                || version == null || version.signum() < 0 || desc == null || desc.isBlank()) {
            throw new IllegalArgumentException(
                    "cluster or group is blank or index is null or version is null or < 0 or desc is blank");
        }
        this.cluster = cluster;
        this.group = group;
        this.address = index;
        this.version = version;
        this.cursor = BigInteger.ZERO;
        this.minSeen = index;
        this.maxSeen = index;
        this.slave0 = this;
        this.desc = desc;
    }

    public void init() {
        if (!State.INITING.equals(this.flag)) {
            throw new IllegalStateException("min unit has already been initialized");
        }
        this.flag = State.RUNNING;
        this.master = this;
        causalChain.addIndex(this.address, this);
        initHook(this);
    }

    @Override
    public Context pre() {
        return skip(BigInteger.ONE.negate());
    }

    @Override
    public Context next() {
        return skip(BigInteger.ONE);
    }

    @Override
    public Context skip(BigInteger interval) {
        if (interval == null) {
            return this;
        }

        BigInteger newIndex = moveForward(this.address, interval);
        minSeen = minSeen.min(newIndex);
        maxSeen = maxSeen.max(newIndex);
        Context ctx = causalChain.get(newIndex);
        if (ctx == null) {
            return this;
        }

        return ctx;
    }

    @Override
    public String cluster() {
        return this.cluster;
    }

    @Override
    public String group() {
        return this.group;
    }

    @Override
    public BigInteger address() {
        return this.address;
    }

    @Override
    public Map<String, Object> params() {
        return this.params;
    }

    @Override
    public Context AI(MinUnit now, String flag) {
        this.cursor = BigInteger.ZERO;

        if (State.REPLAY.equals(flag)) {
            this.flag(State.REPLAYING); // 回放是主机自身的状态：不进 say、不推进版本
        } else {
            now.flag(say(now, flag)); // 状态转移统一交给 say（模板方法），AI 不再硬编码各分支
            this.version = moveForward(this.version, BigInteger.ONE);
        }

        now.master = this;
        this.slave = now;

        return now;
    }

    @Override
    public Context to(MinUnit now) throws Exception {
        this.cursor = BigInteger.ZERO;
        if (State.REPLAYING.equals(flag)) {
            // 回放
            while (this.master.cursor().compareTo(this.master.version()) <= 0) {
                replaying(this.search(this.master.cursor()), now);
                this.master.cursor(moveForward(this.master.cursor(), BigInteger.ONE));
                continue;
            }
        }

        // 校验 ctx 字段元素
        if (!samePeople(now)) {
            // 发现外星人
            findNewPerson(now);
        }

        if (State.INITING.equals(this.flag)) {
            throw new IllegalStateException("please call init method");
        }

        next(now);

        if (State.PAUSING.equals(this.flag)) {
            pausing(now);
            refreshIndex(now);
            return master;
        }

        if (State.DESTROYING.equals(this.flag)) {
            pleaseReflect(now);
            refreshIndex(now);
            return master;
        } else if (State.RUNNING.equals(this.flag)) {
            // 上下文切换
            if (now == null || now.getClass() != this.getClass()) {
                refreshContext(now);
            }

            // 因果链
            causalChain(now);

            doit(now);

            stateTransition(now);

            if (causalChain.size() % cap() == 0) {
                boom(now);
            }

            refreshIndex(now);

            success(now);

            return master;
        } else {
            callback(now);

            refreshIndex(now);
            return master;
        }
    }

    @Override
    public void stateTransition(Context now) {
        this.slave = now;
    }

    @Override
    public void causalChain(Context now) {
        causalChain.offer(now);
    }

    @Override
    public void refreshIndex(Context now) {
        // 可能地址相同，但不是一个cluster或者group
        causalChain.addIndex(now.address(), now.ctx());
        minSeen = minSeen.min(now.address());
        maxSeen = maxSeen.max(now.address());
    }

    @Override
    public void boom(Context now) {
        alarm(now);
        if (limit()) {
            causalChain.poll();
        }
    }

    protected abstract void alarm(Context now);

    @Override
    public BigInteger version() {
        return this.version;
    }

    @Override
    public Context ctx() {
        return this.slave;
    }

    @Override
    public BigIndexArrayList<Context> worldLine() {
        return causalChain;
    }

    @Override
    public BigInteger cursor() {
        return this.cursor;
    }

    protected void flag(String flag) {
        this.flag = flag;
    }

    private void next(MinUnit now) {
        this.slave = now;
        this.version = moveForward(this.version, BigInteger.ONE);
    }

    @Override
    public Context search(BigInteger address) {
        return causalChain.get(address);
    }

    @Override
    public void cursor(BigInteger cursor) {
        this.cursor = cursor;
    }

    /**
     * 状态转移决策：继承 Context 的默认映射（PAUSE→PAUSING、DESTROY→DESTROYING、
     * RUNNING→RUNNING、其余→OTHER）。子类可覆盖（如 TestUnit），也可直接复用。
     */
    @Override
    public String say(Context now, String flag) {
        return Context.super.say(now, flag);
    }

    @Override
    public String toString() {
        return String.format("[%s][%s][%s][%s][%s][%s][%s]", desc, cluster, group, address, version, cursor, flag);
    }
}
