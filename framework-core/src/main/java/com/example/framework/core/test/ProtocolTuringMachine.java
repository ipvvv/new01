package com.example.framework.core.test;

import java.math.BigInteger;

import com.example.framework.core.BigIndexArrayList;
import com.example.framework.core.Context;
import com.example.framework.core.MinUnit;
import com.example.framework.core.State;

/**
 * 协议版图灵机 —— 用 core 的 AI/to 协议本身驱动计算（不覆盖 to()/AI()）。
 *
 * <p>这是对"上下文宇宙协议 = 图灵完备"的证明：磁带的每一格（{@link TapeCell}）、
 * 读写头（{@link ProtocolHead}）、有限控制（{@link ProtocolController}）都是世界线的正式居民，
 * 而每一个图灵机步都拆成三次协议往返——每一次都是标准的 {@code AI(...)} 下指令、
 * {@code to(...)} 走框架生命周期（causalChain 记录、version 推进、refreshIndex 刷新、success 收尾）：
 *
 * <pre>
 *   ① c.AI(h, READ).to(h)     读写头：读当前格（doit 执行）
 *   ② c.AI(c, DECIDE).to(h)   控制端：查 δ、下指令（doit 执行）
 *   ③ c.AI(h, WRITE).to(h)    读写头：写回并移动（doit 执行）
 * </pre>
 *
 * <p>与覆盖式图灵机（覆盖 to() = "一步转移"）不同：本实现绕过不了框架协议——
 * 这里 to() 是框架原版，计算全部发生在协议回合的 doit() 里。
 *
 * <p><b>图灵完备性</b>：任意有限转移表（δ 存于控制端 params）+ 无界世界线（BigInteger 下标，
 * 支持负数——磁带是<b>双向无限</b>的整数轴）+ 显式停机，足以模拟任何图灵机；
 * 0/1 号兼作控制端与读写头的住所（读到自身视为空白，被写到时让位给磁带格）。
 * 实际能力仅受内存约束。
 *
 * <p><b>实现要点</b>：框架的 {@code to()} 每轮会把 {@code cursor} 清零（回放游标语义），
 * 因此读写头的位置必须存进 params 而不能用 cursor——这是"纯协议实现"的关键约束。
 *
 * <p><b>自举</b>：控制端支持 BOOTSTRAP 指令——一次协议往返完成"读自己的 δ → 优化编译 →
 * 本地等价校验（Rice：部分验证，不触碰世界线）→ 安装"，并配套锚点（anchor）与代数（generation）
 * 账本；全程走协议回合，不覆盖 to()/AI()。
 */
public class ProtocolTuringMachine {

    /** 一台装配好的协议图灵机：控制端 + 读写头。 */
    public record Machine(ProtocolController controller, ProtocolHead head) {
    }

    /**
     * 一直运行到停机。
     * 注意：非停机程序会永久运行——停机问题不可判定；受限预算请用 {@link #run(Machine, long)}。
     */
    public static void run(Machine machine) throws Exception {
        run(machine, Long.MAX_VALUE);
    }

    /**
     * 有限步运行：最多 maxSteps 轮（每轮 = 读+决策+写三次协议往返；预算是每次调用独立计数，非累计）。
     * 停机检测发生在下一轮的 DECIDE 回合，因此预算耗尽时 halted 可能仍为 false。
     */
    public static void run(Machine machine, long maxSteps) throws Exception {
        ProtocolController c = machine.controller();
        ProtocolHead h = machine.head();
        long budget = maxSteps;
        while (!c.halted() && budget > 0) {
            //获取 h 上下文符号
            h.AI(h, ProtocolHead.READ).to(h);          // ① 读写头：读当前格
            c.AI(c, ProtocolController.DECIDE).to(h);  // ② 控制端：查 δ、下指令
            if (!c.halted()) {
                h.AI(h, ProtocolHead.WRITE).to(h);     // ③ 读写头：写回并移动
            }
            budget--;
        }
    }

    /**
     * 自举：一次 BOOTSTRAP 协议往返（读自己 → 编译 → 等价校验 → 安装），
     * 然后复位读写头的执行状态（位置回原点、窗口归零；磁带数据保留）。
     */
    public static void bootstrap(Machine machine) throws Exception {
        ProtocolController c = machine.controller();
        c.AI(c, ProtocolController.BOOTSTRAP).to(c);
        machine.head().reset();
    }

    /**
     * 等价校验：在给定测试用例上比较旧 δ 与新 δ 的行为。
     * 本地模拟（不创建居民、不触碰世界线），用"停机 + 步数 + 磁带快照"作行为指纹。
     * 注意：这是<b>部分验证</b>（测试采样），不是完备证明——
     * Rice 定理：程序的非平凡语义性质不可判定，等价性永远无法被完整证明。
     */
    static boolean equivalent(Delta before, Delta after, int[][] testTapes, long maxSteps) {
        for (int[] tape : testTapes) {
            Result r1 = simulate(before, tape, maxSteps);
            Result r2 = simulate(after, tape, maxSteps);
            if (r1.halted() != r2.halted() || r1.steps() != r2.steps()) {
                return false;
            }
            // 用两台访问范围的并集作共同快照窗口，避免位移掩盖差异
            BigInteger lo = r1.lo().min(r2.lo());
            BigInteger hi = r1.hi().max(r2.hi());
            if (!snapshot(r1.tape(), lo, hi).equals(snapshot(r2.tape(), lo, hi))) {
                return false;
            }
        }
        return true;
    }

    /** 一次运行的行为记录：磁带、是否停机、步数、访问窗口（供并集快照比较）。 */
    private record Result(BigIndexArrayList<Integer> tape, boolean halted, long steps, BigInteger lo, BigInteger hi) {
    }

    /** 在本地磁带上模拟一台协议图灵机（不创建任何居民、不触碰共享世界线）。 */
    private static Result simulate(Delta delta, int[] tape, long maxSteps) {
        BigIndexArrayList<Integer> t = new BigIndexArrayList<>();
        for (int i = 0; i < tape.length; i++) {
            t.set(BigInteger.valueOf(i), tape[i]);
        }
        int state = 0;
        BigInteger pos = BigInteger.ZERO;
        BigInteger minSeen = BigInteger.ZERO;
        BigInteger maxSeen = BigInteger.ZERO;
        long steps = 0;
        while (steps < maxSteps) {
            int symbol = read(t, pos);
            int[] rule = delta.lookup(state, symbol);
            if (rule == null) {
                return new Result(t, true, steps, minSeen, maxSeen);
            }
            t.set(pos, rule[0]);
            pos = pos.add(BigInteger.valueOf(rule[1]));
            minSeen = minSeen.min(pos);
            maxSeen = maxSeen.max(pos);
            state = rule[2];
            steps++;
        }
        return new Result(t, false, steps, minSeen, maxSeen);
    }

    private static int read(BigIndexArrayList<Integer> tape, BigInteger addr) {
        if (addr.compareTo(tape.bigSize()) >= 0) {
            return 0;
        }
        Integer v = tape.get(addr);
        return v == null ? 0 : v;
    }

    private static String snapshot(BigIndexArrayList<Integer> tape, BigInteger lo, BigInteger hi) {
        StringBuilder sb = new StringBuilder();
        for (BigInteger i = lo; i.compareTo(hi) <= 0; i = i.add(BigInteger.ONE)) {
            sb.append(read(tape, i));
        }
        return sb.toString();
    }

    /** 3 态忙碌海狸：13 步停机，磁带上恰好 6 个 1。 */
    public static Machine busyBeaver3() {
        Delta delta = new Delta()
                .put(0, 0, 1, +1, 1) // A0: 1 R B
                .put(0, 1, 1, -1, 2) // A1: 1 L C
                .put(1, 0, 1, -1, 0) // B0: 1 L A
                .put(1, 1, 1, +1, 1) // B1: 1 R B
                .put(2, 0, 1, -1, 1) // C0: 1 L B
                .put(2, 1, 1, 0, 3); // C1: 1 N → 状态 3 无规则 = 停机
        return new Machine(new ProtocolController(delta), new ProtocolHead());
    }

    /** 永不停止：任何符号都写 1、右移、保持状态 0（用于测试预算运行与停机不可判定）。 */
    public static Machine moveRightForever() {
        Delta delta = new Delta()
                .put(0, 0, 1, +1, 0)
                .put(0, 1, 1, +1, 0);
        return new Machine(new ProtocolController(delta), new ProtocolHead());
    }

    /** 一元加一：输入 n 个 1，输出 n+1 个 1。 */
    public static Machine unaryIncrement(int n) {
        Delta delta = new Delta()
                .put(0, 1, 1, +1, 0) // 扫描 1：写 1、右移
                .put(0, 0, 1, 0, 1)  // 遇空白：写 1、进状态 1（无规则 = 停机）
                .put(9, 0, 0, 0, 9); // 状态 9 不可达：冗余规则，会被 optimize 消掉
        ProtocolController controller = new ProtocolController(delta); // 清场 + 入住 0 号
        ProtocolHead head = new ProtocolHead();                          // 入住 1 号
        // 顺序关键：先让两个居民入住（0/1 号），再布置初始磁带。
        // 若先写磁带后建控制端，控制端 init() 入住 0 号会覆盖 0 号上的输入格（数据丢失）。
        for (int i = 0; i < n; i++) {
            head.write(i, 1); // 布置初始磁带（写入 0/1 号时居民让位给数据格）
        }
        return new Machine(controller, head);
    }

    public static void main(String[] args) throws Exception {
        Machine bb = busyBeaver3();
        boolean cHome = bb.controller().worldLine().get(BigInteger.ZERO) == bb.controller();
        boolean hHome = bb.controller().worldLine().get(BigInteger.ONE) == bb.head();
        run(bb);
        System.out.println("=== 协议版图灵机（AI/to 驱动，不覆盖 to()）===");
        System.out.println("[忙碌海狸] 停机=" + bb.controller().halted()
                + " 步数=" + bb.controller().steps()
                + " 磁带=" + bb.head().tapeString()
                + " 1的个数=" + bb.head().countOnes());
        System.out.println("[世界线居民] 控制端住0号=" + cHome + " 读写头住1号=" + hHome
                + "（运行前检查；运行中磁带格可占用 0/1 号，居民让位）");
        System.out.println("[协议回合] 控制端 version=" + bb.controller().version()
                + "（每次 AI/to 都是协议交互，故远大于步数）");

        // 自举：BOOTSTRAP 协议往返（读自己 → 优化编译 → 等价校验 → 安装）
        Machine inc = unaryIncrement(3);
        System.out.println("[自举前] δ 规则数=" + inc.controller().delta().size()
                + " 锚点=" + inc.controller().anchor()
                + " 代数=" + inc.controller().generation());
        bootstrap(inc);
        System.out.println("[自举后] δ 规则数=" + inc.controller().delta().size()
                + " 锚点=" + inc.controller().anchor()
                + " 代数=" + inc.controller().generation()
                + "（等价校验通过才安装；不可达状态 9 的规则被消除）");
        run(inc);
        System.out.println("[一元加一] 输入=111 输出1的个数=" + inc.head().countOnes()
                + " 磁带=" + inc.head().tapeString());

        // 自我复制：用 self() 的文本造一台 δ 等价的机器
        Machine copy = inc.controller().replicate();
        System.out.println("[自我复制] δ 等价=" + copy.controller().delta().equals(inc.controller().delta())
                + " 锚点=" + copy.controller().anchor()
                + " 代数=" + copy.controller().generation());
    }
}

/**
 * 控制端（有限控制，协议版）：住在世界线 0 号。
 * 全部状态（δ、当前状态、步数）都放在 {@code params()} 里；
 * 不覆盖 {@code to()}——每个"决策"回合都是被 {@code AI(DECIDE)} 命令、
 * 在框架 {@code to()} 生命周期里由 {@link #doit(MinUnit)} 执行一次。
 */
class ProtocolController extends MinUnit {

    /** 自定义指令：查 δ 并决策 / 自举（say 把它们映射成 RUNNING 以进入 to() 生命周期）。 */
    static final String DECIDE = "DECIDE";
    static final String BOOTSTRAP = "BOOTSTRAP";

    private static final String DELTA = "delta";
    private static final String STATE = "state";
    private static final String STEPS = "steps";
    private static final String LAST_COMMAND = "lastCommand";
    private static final String ANCHOR = "anchor";
    private static final String GENERATION = "generation";
    private static final String TEST_TAPES = "testTapes";

    ProtocolController(Delta delta) {
        super("turing", "protocol", BigInteger.ZERO); // 0 号：控制端的住所
        worldLine().clear(); // 先清场（演示用）：世界线是静态共享的，避免与其它机器/测试互相污染
        params().put(DELTA, delta);
        params().put(STATE, 0);
        params().put(STEPS, 0);
        params().put(ANCHOR, "external"); // 初始锚点：第一个 δ 必须来自系统之外
        params().put(GENERATION, 0);      // 自举代数：0 = 外部锚点
        params().put(TEST_TAPES, new int[][] { {}, { 0, 1 }, { 0, 1, 0, 1 } }); // 等价校验的测试带
        init();
    }

    /** 当前安装的 δ（程序 = 数据）。 */
    Delta delta() {
        return (Delta) params().get(DELTA);
    }

    /** 初始锚点：δ 的来源（外部）。 */
    public String anchor() {
        return (String) params().get(ANCHOR);
    }

    /** 自举代数：0 = 外部锚点（系统之外给定），n = 第 n 次自举后的 δ。 */
    public int generation() {
        return (Integer) params().get(GENERATION);
    }

    /** 自我描述：输出自己的 δ（quine 的雏形）。 */
    public String self() {
        return delta().toData();
    }

    /** 等价校验的测试带（默认三组小输入）。 */
    private int[][] testTapes() {
        return (int[][]) params().get(TEST_TAPES);
    }

    /** 当前有限状态（存于 params，缺失视为起始态 0）。 */
    int currentState() {
        Object v = params().get(STATE);
        return v == null ? 0 : (Integer) v;
    }

    private void currentState(int s) {
        params().put(STATE, s);
    }

    /** 已执行步数：成功查表（成功转移）的次数，如忙碌海狸 = 13。 */
    public long steps() {
        Object v = params().get(STEPS);
        return v == null ? 0 : ((Number) v).longValue();
    }

    /** 是否已停机 = flag 是否为 DESTROYING（查不到转移规则时自我毁灭）。 */
    public boolean halted() {
        return State.DESTROYING.equals(flag);
    }

    /**
     * 自举回合：读自己的 δ → 编译（等价优化）→ 本地模拟做等价校验 → 通过才安装。
     * 校验在本地模拟完成，不触碰共享世界线（否则会污染当前机器的磁带）。
     */
    private void bootstrap() {
        Delta before = delta();
        Delta improved = before.optimize();
        if (!ProtocolTuringMachine.equivalent(before, improved, testTapes(), 100)) {
            throw new IllegalStateException("等价校验失败：拒绝安装新 δ（部分验证未通过；Rice 定理：无法完备证明）");
        }
        install(improved);
    }

    /** 安装新 δ：程序换新，数据保留；执行状态复位，锚点不变，代数 +1。 */
    private void install(Delta improved) {
        params().put(DELTA, improved);
        params().put(GENERATION, generation() + 1); // 新 δ 是自举产物，锚点仍在系统之外
        params().put(STATE, 0);
        params().put(STEPS, 0);
        flag(say(this, State.RUNNING)); // 复活 = 恢复运行指令
    }

    /** 自我复制：用 self() 的文本造一台 δ 等价的机器（锚点标记为"副本"，代数继承）。 */
    public ProtocolTuringMachine.Machine replicate() {
        ProtocolController copy = new ProtocolController(Delta.fromData(self())); // 清场 + 入住 0 号
        ProtocolHead head = new ProtocolHead();                                  // 入住 1 号
        copy.params().put(ANCHOR, "replica:" + anchor());
        copy.params().put(GENERATION, generation());
        return new ProtocolTuringMachine.Machine(copy, head);
    }

    /** 关闭宇宙回收：演示把世界线当磁带用，不允许 boom 把居民记录 poll 掉。 */
    @Override
    public int cap() {
        return Integer.MAX_VALUE;
    }

    /** DECIDE/BOOTSTRAP 指令 → RUNNING（进入 to() 生命周期）；其余指令走默认映射。 */
    @Override
    public String say(Context now, String flag) {
        if (DECIDE.equals(flag) || BOOTSTRAP.equals(flag)) {
            params().put(LAST_COMMAND, flag); // 记住指令，doit 据此执行
            return State.RUNNING;
        }
        return super.say(now, flag);
    }

    /**
     * 决策回合：读读写头刚读到的符号 → 查 δ → 向读写头下指令；
     * 查不到规则 = 停机（自我毁灭，状态转移走 say）。
     */
    @Override
    public void doit(MinUnit now) {
        if (BOOTSTRAP.equals(params().get(LAST_COMMAND))) {
            bootstrap(); // 自举回合
            return;
        }
        ProtocolHead head = (ProtocolHead) now;
        int state = currentState();
        //上下文
        int symbol = head.lastRead();
        int[] t = delta().lookup(state, symbol);
        if (t == null) {
            flag(say(this, State.DESTROY));
            System.out.printf("  [HALT ] δ(%d, %d) 无规则，停机%n", state, symbol);
            return;
        }
        head.command(t[0], t[1]); // 下指令：写 t[0]、移动 t[1]
        currentState(t[2]);
        params().put(STEPS, steps() + 1);
        System.out.printf("  [DECIDE] δ(%d, %d) = 写%d 移%+d → 状态 %d%n", state, symbol, t[0], t[1], t[2]);
    }

    // ---- MinUnit 抽象钩子 ----

    /** 状态报告：直接返回 flag（单一事实来源）。 */
    @Override
    public String state() {
        return State.DESTROYING.equals(flag) ? State.DESTROYING : State.RUNNING;
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

    /** 运行期间不反复搬家：init() 已登记 0 号，反复 addIndex 会覆盖磁带格上的数据。 */
    @Override
    public void refreshIndex(Context now) {
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
}

/**
 * 上下文读写器
 * 读写头（协议版）：住在世界线 1 号（0 号是控制端住所；运行期间若被磁带格占用则让位）。
 *
 * <p>位置存在 {@code params()}（键 POSITION）——框架的 {@code to()} 每轮会把 {@code cursor}
 * 清零（回放游标语义），不能用作持久位置；这是"用纯协议实现计算"的关键约束。
 * 每个回合都是被 {@code AI(READ/WRITE)} 命令、在框架 {@code to()} 生命周期里由 doit 执行。
 */
class ProtocolHead extends MinUnit {

    /** 自定义指令：读当前格 / 写回并移动（say 映射成 RUNNING 执行）。 */
    static final String READ = "READ";
    static final String WRITE = "WRITE";

    private static final String LAST_COMMAND = "lastCommand";
    private static final String LAST_READ = "lastRead";
    private static final String PENDING_WRITE = "pendingWrite";
    private static final String PENDING_MOVE = "pendingMove";
    private static final String POSITION = "position";

    ProtocolHead() {
        super("turing", "protocol", BigInteger.ONE); // 1 号：读写头的住所（0 号是控制端）
        init();
        position(BigInteger.ZERO); // 注意力起点：磁带原点（双向磁带，地址可为负数）
        minSeen = BigInteger.ZERO; // 可见窗口初始只含原点
        maxSeen = BigInteger.ZERO;
    }

    /** 关闭宇宙回收（同控制端）。 */
    @Override
    public int cap() {
        return Integer.MAX_VALUE;
    }

    /** 记住自定义指令（READ/WRITE）并返回 RUNNING 以进入 to() 生命周期。 */
    @Override
    public String say(Context now, String flag) {
        if (READ.equals(flag) || WRITE.equals(flag)) {
            params().put(LAST_COMMAND, flag);
            return State.RUNNING;
        }
        return super.say(now, flag);
    }

    /** 回合动作：按 say 记下的指令执行"读"或"写+移动"。 */
    @Override
    public void doit(MinUnit now) {
        String cmd = (String) params().get(LAST_COMMAND);
        if (READ.equals(cmd)) {
            readCell();
        } else if (WRITE.equals(cmd)) {
            writeAndMove();
        }
    }

    /** 最近一次读到的符号（供控制端决策）。 */
    int lastRead() {
        Object v = params().get(LAST_READ);
        return v == null ? 0 : (Integer) v;
    }

    /** 接收控制端的指令：暂存"写什么、往哪走"。 */
    void command(int write, int move) {
        params().put(PENDING_WRITE, write);
        params().put(PENDING_MOVE, move);
    }

    /** 复位执行状态（自举后重跑）：位置回原点、可见窗口归零；磁带数据保留。 */
    void reset() {
        position(BigInteger.ZERO);
        minSeen = BigInteger.ZERO;
        maxSeen = BigInteger.ZERO;
        flag(say(this, State.RUNNING)); // 复活 = 恢复运行指令
    }

    /** 当前注意力位置（世界线绝对地址）。 */
    BigInteger position() {
        return (BigInteger) params().get(POSITION);
    }

    private void position(BigInteger p) {
        params().put(POSITION, p);
    }

    /** 读当前格（缺失/非磁带格 = 空白 0），并扩张可见窗口。 */
    private void readCell() {
        int s = readCellAt(position());
        params().put(LAST_READ, s);
        minSeen = minSeen.min(position());
        maxSeen = maxSeen.max(position());
        System.out.printf("  [READ ] 地址 %s → 符号 %d%n", position(), s);
    }

    /** 写回当前格并移动注意力（住所 1 号不动，动的是 params 里的位置；双向磁带无边界）。 */
    private void writeAndMove() {
        int write = (Integer) params().get(PENDING_WRITE);
        int move = (Integer) params().get(PENDING_MOVE);
        setCell(position(), write);
        position(position().add(BigInteger.valueOf(move)));
        minSeen = minSeen.min(position());
        maxSeen = maxSeen.max(position());
        System.out.printf("  [WRITE] 写 %d 移动 %+d → 地址 %s%n", write, move, position());
    }

    /** 初始磁带：在地址 pos 写符号（运行前布置输入；双向磁带，pos 可为负数）。 */
    public void write(int pos, int symbol) {
        BigInteger addr = BigInteger.valueOf(pos);
        setCell(addr, symbol);
        minSeen = minSeen.min(addr);
        maxSeen = maxSeen.max(addr);
    }

    /** 读绝对地址 addr 的符号：缺失/非磁带格 = 空白 0（地址可为负数）。 */
    private int readCellAt(BigInteger addr) {
        if (addr.compareTo(worldLine().bigSize()) >= 0) {
            return 0;
        }
        Context c = worldLine().get(addr);
        return c instanceof TapeCell cell ? cell.symbol() : 0;
    }

    /** 写绝对地址 addr 的符号：按需创建磁带格（覆盖该地址的非磁带格记录；地址可为负数）。 */
    private void setCell(BigInteger addr, int symbol) {
        if (addr.compareTo(worldLine().bigSize()) < 0) {
            Context c = worldLine().get(addr);
            if (c instanceof TapeCell cell) {
                cell.symbol(symbol);
                return;
            }
        }
        TapeCell cell = new TapeCell(addr);
        worldLine().addIndex(addr, cell);
        cell.symbol(symbol);
    }

    /** 统计磁带上 1 的个数（窗口 = 写过的格 ∪ 访问过的格）。 */
    public int countOnes() {
        int count = 0;
        for (BigInteger i = minSeen; i.compareTo(maxSeen) <= 0; i = i.add(BigInteger.ONE)) {
            if (readCellAt(i) == 1) {
                count++;
            }
        }
        return count;
    }

    /** 打印磁带可见窗口（1 → '1'，空白 → '_'）。 */
    public String tapeString() {
        StringBuilder sb = new StringBuilder();
        for (BigInteger i = minSeen; i.compareTo(maxSeen) <= 0; i = i.add(BigInteger.ONE)) {
            sb.append(readCellAt(i) == 1 ? '1' : '_');
        }
        return sb.toString();
    }

    // ---- MinUnit 抽象钩子 ----

    /** 状态报告：直接返回 flag。 */
    @Override
    public String state() {
        return State.DESTROYING.equals(flag) ? State.DESTROYING : State.RUNNING;
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

    /** 运行期间不反复搬家：init() 已登记 1 号，反复 addIndex 会覆盖磁带格上的数据。 */
    @Override
    public void refreshIndex(Context now) {
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
}
