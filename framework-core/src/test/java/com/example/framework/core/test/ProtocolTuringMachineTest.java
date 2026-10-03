package com.example.framework.core.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import com.example.framework.core.Context;

/**
 * {@link ProtocolTuringMachine} 的单元测试：协议本身（AI/to 链）图灵完备的证据。
 */
class ProtocolTuringMachineTest {

    /** 忙碌海狸经由纯协议驱动：13 步停机，6 个 1。 */
    @Test
    void busyBeaverWritesSixOnes() throws Exception {
        ProtocolTuringMachine.Machine m = ProtocolTuringMachine.busyBeaver3();
        ProtocolTuringMachine.run(m);
        assertTrue(m.controller().halted());
        assertEquals(13L, m.controller().steps());
        assertEquals(6, m.head().countOnes());
        assertEquals("111111", m.head().tapeString());
    }

    /** 一元加一经由纯协议驱动：输入 3 个 1 → 输出 4 个 1。 */
    @Test
    void unaryIncrementAddsOne() throws Exception {
        ProtocolTuringMachine.Machine m = ProtocolTuringMachine.unaryIncrement(3);
        ProtocolTuringMachine.run(m);
        assertTrue(m.controller().halted());
        assertEquals(4L, m.controller().steps());
        assertEquals(4, m.head().countOnes());
    }

    /** 参与者都是世界线正式居民：控制端住 0 号、读写头住 1 号。 */
    @Test
    void allParticipantsLiveOnWorldLine() {
        ProtocolTuringMachine.Machine m = ProtocolTuringMachine.busyBeaver3();
        Context c0 = m.controller().worldLine().get(BigInteger.ZERO);
        Context c1 = m.controller().worldLine().get(BigInteger.ONE);
        assertSame(m.controller(), c0);
        assertSame(m.head(), c1);
    }

    /** 计算是协议驱动的：每一步至少经过控制端一次 AI+to（version ≥ 2 × 步数）。 */
    @Test
    void computationIsProtocolDriven() throws Exception {
        ProtocolTuringMachine.Machine m = ProtocolTuringMachine.busyBeaver3();
        ProtocolTuringMachine.run(m);
        // 每个成功转移必然包含 c.AI(DECIDE) 与 c.to()（各 version+1）→ version ≥ 2×步数
        assertTrue(m.controller().version()
                .compareTo(BigInteger.valueOf(2 * m.controller().steps())) >= 0);
    }

    /** 自举：BOOTSTRAP 协议回合消除不可达规则，锚点不变、代数 +1。 */
    @Test
    void bootstrapRemovesDeadRulesAndKeepsAnchor() throws Exception {
        ProtocolTuringMachine.Machine m = ProtocolTuringMachine.unaryIncrement(3);
        assertEquals(3, m.controller().delta().size()); // 含不可达状态 9 的冗余规则

        ProtocolTuringMachine.bootstrap(m);

        assertEquals(2, m.controller().delta().size()); // 状态 9 被消除
        assertEquals("external", m.controller().anchor()); // 锚点永不自造
        assertEquals(1, m.controller().generation());      // 代数 +1
    }

    /** 自举后行为不变：仍是一元加一（4 步、4 个 1）。 */
    @Test
    void bootstrapKeepsBehavior() throws Exception {
        ProtocolTuringMachine.Machine m = ProtocolTuringMachine.unaryIncrement(3);
        ProtocolTuringMachine.bootstrap(m);
        ProtocolTuringMachine.run(m);
        assertTrue(m.controller().halted());
        assertEquals(4L, m.controller().steps());
        assertEquals(4, m.head().countOnes());
    }

    /** 自我复制：δ 等价、锚点标记为副本、代数继承。 */
    @Test
    void replicateProducesEquivalentDelta() {
        ProtocolTuringMachine.Machine m = ProtocolTuringMachine.busyBeaver3();
        ProtocolTuringMachine.Machine copy = m.controller().replicate();
        assertEquals(m.controller().delta(), copy.controller().delta());
        assertEquals("replica:external", copy.controller().anchor());
        assertEquals(0, copy.controller().generation());
    }

    /** 等价校验的负例：行为不同的 δ 必须被检测出来（Rice：部分验证的底线）。 */
    @Test
    void equivalenceCheckDetectsBehaviorChange() {
        Delta original = new Delta().put(0, 1, 1, +1, 0).put(0, 0, 1, 0, 1);
        Delta broken = new Delta().put(0, 1, 1, -1, 0).put(0, 0, 1, 0, 1); // 移动方向相反
        int[][] tapes = { { 1 }, { 0, 1, 0, 1 } };
        assertFalse(ProtocolTuringMachine.equivalent(original, broken, tapes, 100));
    }

    /** 双向无限磁带：负地址直接可用（协议版）。 */
    @Test
    void negativeTapePositionsWork() {
        ProtocolTuringMachine.Machine m = ProtocolTuringMachine.busyBeaver3();
        m.head().write(-3, 1);
        m.head().write(-2, 1);
        m.head().write(-1, 1);
        assertEquals(3, m.head().countOnes());
    }

    /** 预算运行：每次调用独立计数，可观察非停机程序的中间状态。 */
    @Test
    void boundedRunIsPerCall() throws Exception {
        ProtocolTuringMachine.Machine m = ProtocolTuringMachine.moveRightForever();
        ProtocolTuringMachine.run(m, 5);
        assertFalse(m.controller().halted());
        assertEquals(5L, m.controller().steps());
        ProtocolTuringMachine.run(m, 5);
        assertEquals(10L, m.controller().steps());
    }

    /** 自举后重跑：程序换新、数据保留，执行状态复位（与 BootstrapMachine 同语义）。 */
    @Test
    void bootstrapAfterHaltResetsExecutionState() throws Exception {
        ProtocolTuringMachine.Machine m = ProtocolTuringMachine.unaryIncrement(3);
        ProtocolTuringMachine.run(m); // 4 步停机，磁带 1111
        assertEquals(4, m.head().countOnes());

        ProtocolTuringMachine.bootstrap(m); // 自举：程序换新，数据保留

        assertEquals(0L, m.controller().steps()); // 步数清零
        assertFalse(m.controller().halted());     // 可再次运行
        ProtocolTuringMachine.run(m);             // 从头重跑：磁带数据保留，再 +1
        assertTrue(m.controller().halted());
        assertEquals(5, m.head().countOnes());
    }

    /** 连续自举：代数单调递增，锚点永不自造。 */
    @Test
    void bootstrapTwiceIncrementsGeneration() throws Exception {
        ProtocolTuringMachine.Machine m = ProtocolTuringMachine.unaryIncrement(3);
        ProtocolTuringMachine.bootstrap(m);
        ProtocolTuringMachine.bootstrap(m);
        assertEquals(2, m.controller().generation());
        assertEquals("external", m.controller().anchor());
    }

    /** 自我复制：副本不仅能描述等价，还能实际计算（replicate 只复制程序，磁带需自备输入）。 */
    @Test
    void replicateRunsEquivalently() throws Exception {
        ProtocolTuringMachine.Machine m = ProtocolTuringMachine.unaryIncrement(3);
        ProtocolTuringMachine.Machine copy = m.controller().replicate();
        for (int i = 0; i < 3; i++) {
            copy.head().write(i, 1); // 副本磁带自备输入
        }
        ProtocolTuringMachine.run(copy);
        assertTrue(copy.controller().halted());
        assertEquals(4L, copy.controller().steps());
        assertEquals(4, copy.head().countOnes());
    }

    /** 让位语义：磁带格写入 0 号后，控制端的住所登记被数据格占用。 */
    @Test
    void residentsYieldToTapeCellsWhenWritten() throws Exception {
        ProtocolTuringMachine.Machine m = ProtocolTuringMachine.unaryIncrement(1);
        ProtocolTuringMachine.run(m);
        Context c0 = m.controller().worldLine().get(BigInteger.ZERO);
        assertTrue(c0 instanceof TapeCell);
    }

    /** 边界输入：n=0 → 输出 1（空白上直接补 1）。 */
    @Test
    void unaryIncrementZeroInput() throws Exception {
        ProtocolTuringMachine.Machine m = ProtocolTuringMachine.unaryIncrement(0);
        ProtocolTuringMachine.run(m);
        assertTrue(m.controller().halted());
        assertEquals(1L, m.controller().steps());
        assertEquals(1, m.head().countOnes());
    }

    /** 等价校验的预算模式：两个非停机程序在预算内分歧，必须被检测出来。 */
    @Test
    void equivalenceCheckDetectsDivergenceWithinBudget() {
        Delta writeOnes = new Delta().put(0, 0, 1, +1, 0);                            // 永远写 1 右移
        Delta writeZeros = new Delta().put(0, 0, 1, +1, 1).put(1, 0, 0, +1, 1);      // 先写 1，之后永远写 0
        int[][] tapes = { {} };
        assertFalse(ProtocolTuringMachine.equivalent(writeOnes, writeZeros, tapes, 3));
    }

    /** 运行中自举：未停机时也允许 BOOTSTRAP——执行状态复位，从头重跑。 */
    @Test
    void bootstrapMidRunResetsExecutionState() throws Exception {
        ProtocolTuringMachine.Machine m = ProtocolTuringMachine.unaryIncrement(3);
        ProtocolTuringMachine.run(m, 2); // 只跑 2 步
        assertFalse(m.controller().halted());
        assertEquals(2L, m.controller().steps());

        ProtocolTuringMachine.bootstrap(m); // 运行中自举：换程序、复位执行状态

        assertEquals(0L, m.controller().steps());
        assertFalse(m.controller().halted());
        ProtocolTuringMachine.run(m);       // 从头重跑：输入保留（0..2 仍是 1）
        assertEquals(4, m.head().countOnes());
    }

    /** 停机后再运行是空操作：步数不变（to 对停机机器直接返回）。 */
    @Test
    void runAfterHaltIsNoop() throws Exception {
        ProtocolTuringMachine.Machine m = ProtocolTuringMachine.unaryIncrement(2);
        ProtocolTuringMachine.run(m);
        long steps = m.controller().steps();
        ProtocolTuringMachine.run(m); // 再次运行
        assertEquals(steps, m.controller().steps());
    }
}
