# 基于世界线与因果链的上下文驱动计算框架：图灵完备性与自举的构造、实现与验证

王鹏

（南京中医药大学，南京 210023）

**摘要**：本文提出并实现了一个以"上下文驱动"为组织原则的最小计算框架 new01，并给出其图灵完备性与自举能力的构造性证明。框架以最小单元（MinUnit）为基本实体，以静态共享的世界线（world line）与因果链（causalChain）为存储与交互介质，将程序（转移函数 δ）建模为世界线内可读写、可优化、可序列化的数据。本文给出协议原生构造 ProtocolTuringMachine：不覆盖框架原语 to()/AI()，以协议往返驱动图灵机（磁带格为世界线居民，磁带为支持负下标的双向无限整数轴），并在协议回合内完成"读自己—编译—等价校验—安装"的完整自举，证明框架交互协议本身同时具备图灵完备性与自举能力。世界线存储采用三段式稀疏分段结构（2^32 × 2^16 × 2^16），get/set 为 O(1)。全量 57 项测试验证了忙碌海狸 BB(3)（13 步、6 个 1）、一元加一、负地址读写、预算执行、协议驱动不变量（version ≥ 2×步数）与自举全流程。文中还讨论了框架与分布式系统模型（Lamport 时钟、因果一致性）、数字物理、统计学习的结构同构，并明确其理论边界：Rice 定理下的部分等价验证、Gödel 意义下的外部锚点，以及停机不可判定性下的预算执行。

**关键词**：图灵完备；自举；上下文驱动；世界线；因果链；稀疏分段存储；协议驱动计算

---

## 1 引言

计算理论自 Turing[1] 与 Church[2] 奠基以来，一个核心问题是：给定一个计算系统，如何构造性地判定它能够计算全部可计算函数。判定图灵完备性的标准方法是构造对已知普适系统的模拟；而对系统的自举能力（系统能否读取、编译并安装自身的程序），则需要代码即数据（code as data）、初始锚点与等价校验三项先决条件。

本文报告的 new01 框架（代码仓库 new01/framework-core，Java 17 实现）从"上下文驱动"这一组织原则出发，把计算重新表述为世界线上的演化：每一个计算实体是一个最小单元（MinUnit），拥有身份（cluster/group/address）、描述（desc）、世代（version）、游标（cursor）、状态旗标（flag）与参数袋（params）；所有单元共享一条静态世界线（causalChain），通过协议原语 to() 与 AI() 交互与演化。该框架的主要贡献如下：

1. **双向无限磁带的世界线存储**：提出支持负 BigInteger 下标的三段式稀疏分段存储结构，使世界线成为整数全集上的可寻址介质，构成真正的双向无限磁带，取代了此前以固定偏移（OFFSET）实现的单端平移方案。
2. **纯协议图灵机**：以不覆盖框架原语 to()/AI() 的 ProtocolTuringMachine 构造并验证图灵完备性——证明框架交互协议本身（而非某个绕过协议的实现）具备图灵完备性；自举机制（读自己—编译—等价校验—安装）整合于其中。
3. **自举的完整实现**：将转移函数 δ 实现为世界线内的数据（可序列化、可等价优化），实现"读自己—编译—等价校验—安装"的自举回路与自我复制；并实现纯协议版自举指令 BOOTSTRAP，使自举本身也是一次协议交互。
4. **可测不变量与验证**：给出"协议承载计算"的可测不变量（控制端 version ≥ 2×步数），并以 57 项自动化测试覆盖图灵完备与自举的正例、负例、边界与不变量。
5. **理论边界的明确化**：明确 Rice 定理、Gödel 不完备定理与停机不可判定性在框架中的具体落点。

本文其余部分组织如下：第 2 节综述相关工作；第 3 节给出系统模型；第 4 节给出图灵完备性的构造与验证；第 5 节给出自举机制；第 6 节讨论框架与分布式系统、数字物理、统计学习的结构对应及局限性；第 7 节总结。

## 2 相关工作

**图灵完备性与普适系统**。Turing[1] 定义了通用图灵机并证明停机问题不可判定；Church[2] 独立给出 λ-演算的可计算性刻画。构造普适系统的经典路线包括：Minsky 双计数器机（仅 INC 与 DECZ 两条指令即图灵完备）[7]、Rule 110 初等元胞自动机[20]、以及繁忙海狸（busy beaver）函数 BB(n) 的不可计算性[3,19]。本文的验证程序采用 Radó 的三态忙碌海狸冠军程序（13 步停机、输出 6 个 1）[3,19]。

**计算系统的自举**。自举（bootstrap）指编译器用自身语言编译自身：①用语言 X 实现编译器 C；②以 C 编译 C 得到 C'；③此后以 C' 编译 C。Kleene 递归定理[6]保证任何图灵完备系统都存在 quine（输出自身的程序），从而"复制自己"在计算层必然可行；但"生成自己的新规则"还要求程序与数据同构（homoiconicity，Lisp 传统）与等价校验。Thompson[17] 指出编译器的信任回归问题：被下毒的编译器在重新编译后仍不可检测，信任链必须终止于系统之外。

**等价验证的不可判定性**。Rice[4] 证明程序的任何非平凡语义性质不可判定，因此自举中"新旧 δ 等价"的验证只能是部分验证（测试采样）。Gödel[5] 的不完备性进一步界定了任何形式系统自证其一致性的不可能性。

**分布式系统与逻辑时钟**。Lamport[15] 提出逻辑时钟与 happened-before 关系，为无全局时钟的事件排序提供基础；CAP 定理[16] 与因果一致性模型界定了分区场景下的一致性取舍。本文框架的 version/causalChain 原语与之结构同构（第 6.1 节）。

**数字物理与计算热力学**。Zuse[8]、Fredkin[9]、Wolfram[10] 提出并发展了"宇宙是一台计算机"的数字物理纲领；物理 Church–Turing 论题声称一切物理过程均可被图灵机模拟。Landauer[11] 证明信息擦除的最小能耗，为"内存即能量"提供物理依据；Bostrom[12] 的模拟假说论证了"可计算宇宙"的认识论后果。这些工作构成本框架隐喻的理论谱系。

## 3 系统模型

### 3.1 最小单元与上下文协议

框架的核心接口 `Context` 定义一组协议原语，其抽象实现为 `MinUnit`。每个最小单元持有：

- **身份**：`cluster`（簇）、`group`（组）、`address`（世界线地址，可为负整数）；
- **时间与位置**：`version`（世代，每次交互 +1）、`cursor`（世界线扫描游标）；
- **状态**：`flag`（生命周期旗标）、`params`（参数袋，任意随行数据）；
- **关系**：`master`（恒久主体）、`slave`（当下化身）、`slave0`、`next`；
- **可见窗口**：`minSeen/maxSeen`（接触过的最小/最大世界线地址）。

协议由两条核心原语构成：

**AI(now, flag)**：指令分发。调用方向 now 下达指令 flag；flag 经 `say(now, flag)` 映射为具体状态并施加于 now；调用方 version +1；绑定 master/slave 关系；返回 now（执行权移交被指令者）。`say` 提供默认映射：PAUSE→PAUSING、DESTROY→DESTROYING、RUNNING→RUNNING、其余→OTHER，子类可覆盖以自定义状态转移决策。

**to(now)**：演化算子。执行一帧框架生命周期：同类判定 `samePeople(now)`（cluster/group 不一致则触发 `findNewPerson`，即"发现他者"）→ 初始化检查 → version +1 → 按 flag 分发（PAUSING/DESTROYING/RUNNING/其他，分别走 pausing、pleaseReflect、doit+stateTransition、callback 钩子）→ `causalChain` 记录 → `refreshIndex` 刷新 → `success` 收尾 → 返回 master（执行权交还）。其中 `boom()` 在因果链规模达到容量阈值 `cap()` 时回收队首（世界线回收）。

生命周期为 INITING → RUNNING → PAUSING/DESTROYING → REPLAYING，由 `State` 接口定义常量。

### 3.2 世界线与稀疏分段存储

`BigIndexArrayList<E>` 是框架的存储基座：以 BigInteger 为下标、稀疏分配、按需扩容的列表结构，同时支持 get/set（O(1)）与 offer/poll（队列入队/出队）。其寻址采用三段式固定分段：

$$superSegment = index \gg 32,\quad mid = (index \gg 16) \wedge 0xFFFF,\quad offset = index \wedge 0xFFFF$$

其中 $\gg$ 为算术右移。对二的幂，算术右移构成 floor 除法、位与构成 floor 模，因此该寻址对负下标天然成立：负下标落入独立的负超段，无需任何特判。逻辑长度 size 定义为最大已写下标 +1，负数写入不改变之。

该结构使世界线成为**整数全集上的可寻址介质**。在图灵机语境下，世界线即双向无限磁带：地址即磁带位置，左、右均无界。此前的实现以固定偏移 OFFSET=10^6 将双向磁带平移折叠进非负地址空间（向左最多 OFFSET 格），负下标支持实现后该平移方案已退役。

### 3.3 居民与让位语义

框架约定：世界线中的每个地址至多承载一个居民记录（Context）。本文的机器实现采用如下约定：

- 机器的控制部件入住 0 号（或分配器分配的专属地址）；
- 读写头入住 1 号（或专属地址）；
- 磁带格入住整数轴其余位置。

当读写头到达控制部件/读写头的住所时：读到自身视为空白（符号 0）；写入时创建磁带格覆盖该记录，居民"让位"给数据格；自举后读写头复位（位置回原点、可见窗口归零，磁带数据保留）。该语义统一了"机器"与"数据"在世界线上的共存方式。

## 4 图灵完备性构造

### 4.1 ProtocolTuringMachine：协议驱动图灵机

ProtocolTuringMachine 以**框架原版** to()/AI() 驱动全部计算：

```
循环（每步 = 三次协议往返）：
  ① head.AI(READ).to()     读写头：读当前格（doit 执行）
  ② ctrl.AI(DECIDE).to()   控制端：查 δ、下指令（doit 执行）
  ③ head.AI(WRITE).to()    读写头：写回并移动（doit 执行）
```

参与者均为世界线正式居民：控制端（ProtocolController）住 0 号，持有 δ、当前状态、步数、锚点与代数（均存于 params）；读写头（ProtocolHead）住 1 号；磁带格为 TapeCell（MinUnit 子类，符号存于 params，构造即入住世界线）。每步计算都完整经历框架生命周期（causalChain 记录、version 推进、refreshIndex 刷新、success 收尾）。

两个实现要点：

1. **位置存于 params**：框架 to() 每轮将 cursor 清零（回放游标语义），故读写头的持久位置必须存于参数袋而非 cursor。
2. **世界线清场约定**：世界线为静态共享介质，控制端构造时执行 worldLine().clear()，避免跨机器/跨测试的磁带污染（该约定由一次全量测试失败暴露并修复）。

**可测不变量**：每个成功转移必然经历控制端一次 AI 与一次 to（各使 version +1），故控制端 version ≥ 2 × 步数恒成立；测试以该不等式作为"计算由协议承载"的验证依据。

### 4.2 完备性论证与验证

**论证**：任意有限转移表 δ 可在 params 中编码；世界线为双向无限整数轴（3.2 节）；停机显式可判定。故框架可模拟任意图灵机，即图灵完备。实际能力受物理内存约束——一切物理实现皆然。

**实验验证**（JUnit 5，共 57 项测试全部通过，0 失败）：

| 测试类 | 用例数 | 覆盖内容 |
|---|---|---|
| BigIndexArrayListTest | 29 | 负下标回环、跨段边界、稀疏查找、队列语义、序列化 |
| ProtocolTuringMachineTest | 18 | BB(3)、一元加一（含 n=0 边界）、负地址、预算运行、协议不变量、自举全流程、复制、负例校验 |
| DefaultRuleEngineTest | 6 | 规则引擎条件分支 |
| ConditionTest | 4 | 条件判定 |

关键数值：忙碌海狸 BB(3) 程序 13 步停机、输出 6 个 1（Radó 冠军程序[3,19]）；一元加一程序 n 个 1 输入输出 n+1 个 1；非停机程序在预算执行下可观测中间状态（预算为每次调用独立计数）。

## 5 自举机制

### 5.1 代码即数据：Delta

自举的前提是程序与数据同构。框架以 `Delta` 类将转移函数 δ 实现为 params 中的数据：规则表为有序映射，`toData()`/`fromData()` 实现文本序列化与反序列化，δ 由此成为世界线内可存储、可传输、可复制的对象。`optimize()` 为等价优化：自起始状态 0 出发做可达性分析（BFS），消除不可达状态的冗余规则——语义等价但描述更短。

### 5.2 初始锚点与代数

自举不能无中生有：第一个 δ 必须来自系统之外。框架以 `anchor` 字段显式记录初始锚点（构造时为 "external"），以 `generation` 记录自举代数（0 = 外部锚点，每次自举 +1）。自举只替换 δ 并使代数递增，锚点永不自造——这是 Gödel[5] 边界在系统内的一等公民化：信任回归必然终止于系统之外。

`replicate()` 实现自我复制：以 self() 的序列化文本构造新机器，副本锚点标记为 "replica:原锚点"、代数继承。

### 5.3 等价校验与 Rice 边界

`equivalent(before, after, testTapes, maxSteps)` 在测试带上比对新旧 δ 的行为指纹：是否停机、步数、磁带快照。校验在**本地模拟**（私有磁带）上完成，不触碰共享世界线——否则校验过程本身会污染被校验机器的磁带。快照窗口取两台模拟访问范围的**并集**：若各自取访问范围，平移差异会被掩盖（原机在 [0,1] 写 1 与坏机在 [-1,0] 写 1 会得到相同的窗口内指纹），该缺陷由负例测试暴露并修复。

Rice 定理[4] 决定等价校验只能是部分验证：负例测试保证校验器能"挡错"（行为不同的 δ 必须判不等，含停机差异与预算内分歧两类），正例测试保证优化"不改行为"。此外，由于 optimize 语义等价，"拒绝安装"路径无法以测试直接触发，其正确性由负例与门控逻辑间接保证——这是结构边界而非测试缺口。

### 5.4 纯协议自举：BOOTSTRAP 指令

ProtocolTuringMachine 将自举实现为一条协议指令。控制端的 `say` 将自定义指令 BOOTSTRAP 映射为 RUNNING，一次协议往返完成自举：

```java
c.AI(c, BOOTSTRAP).to(c);
// doit 收到 BOOTSTRAP：读自己的 δ → optimize() 编译
// → equivalent() 等价校验（本地模拟）→ 通过才 install()
```

install() 执行：替换 δ、代数 +1、当前状态与步数复位、flag 复活（经 say 映射，与 AI 状态转移路径一致）；随后读写头复位（位置回原点、可见窗口归零）。自举后重跑遵循"程序换新、数据保留"语义：磁带（数据）不动，执行状态复位。该语义适用于停机后自举与运行中自举两种场景（均有测试覆盖）。

## 6 讨论

### 6.1 与分布式系统模型的对应

框架原语与分布式系统理论存在系统性的结构同构：version 为 Lamport 逻辑时钟[15]（每次交互 +1）；causalChain 为 happened-before 关系的物化；samePeople/findNewPerson 对应信任域判定与陌生分区接触；master/slave 对应主从复制；boom()+limit() 对应日志回收/TTL。框架的一致性模型为因果一致：每个上下文仅保证自身世界线前后一致，不承诺全局同时一致——这是分区容错下的可守秩序，与 CRDT、因果日志的现实选择一致。

### 6.2 与数字物理、统计学习的关系

框架与数字物理纲领[8,9,10]同构：INITING 即奇点、init() 即创世、to() 即时间之矢、boom() 即世界线回收；物理 Church–Turing 论题与 Landauer 原理[11]分别为"宇宙可计算"与"内存即能量"提供物理依据。统计学习层面：params 的持续更新对应贝叶斯更新[14]；findNewPerson 对应分布外（out-of-distribution）检测；世界模型即因果链的压缩（Kolmogorov 复杂度意义下）[13]。值得指出的是，自举实现后 δ 已可被替换，但"如何优化"（optimize 本身）仍是编译期代码——元学习（学会如何学）仍是开放缺口。

### 6.3 局限性与未来工作

1. **物理内存有限**：无限磁带受限于实际内存，与一切物理实现同构。
2. **停机不可判定**：无预算的 run() 对非停机程序永久执行；框架以预算执行作为工程缓解。
3. **等价验证不完备**：Rice 边界决定了自举校验的采样性质，完备证明不可达。
4. **共享世界线的清场约定**：静态世界线以"构造即清场"规避跨宇宙污染，代价是并发多宇宙共存受限；未来工作可研究分区/命名空间机制。
5. **协议粒度**：每步三次往返的粒度是示范性的，协议驱动的计算密度优化（批量读写、单回合多操作）有待研究。
6. **基于因果时间的密码学构造**：version 的串行推进天然构成可验证延迟函数的骨架，基于其时间锁构造（如 Rivest 时间锁[18]）是潜在应用方向。

## 7 结论

本文提出了以"上下文驱动"为组织原则的计算框架 new01，并给出图灵完备性与自举的构造性证明。框架以最小单元为基本实体、以世界线为双向无限存储介质（负 BigInteger 下标、三段式稀疏分段、O(1) 寻址）、以 AI/to 协议为交互原语。纯协议图灵机的构造证明：框架不仅能算，且其交互协议本身图灵完备；自举机制整合于协议形态之内，能在一次协议往返中完成"读自己—编译—等价校验—安装"的完整自举，并配套锚点、代数与自我复制机制。57 项自动化测试覆盖了正例、负例、边界与不变量。

框架的理论姿态可概括为：把计算从"函数"升级为"世界线的演化"。其边界由三条定理精确刻画：Rice 定理划定了等价校验的部分性，Gödel 不完备性划定了外部锚点的必然性，停机不可判定性划定了预算执行的工程必要性。三者共同表明：图灵完备给出能力的上限，而上下文驱动给出计算的形态——计算发生在观察者的世界线上，验证终止于系统之外的锚点，而能力永远受制于熵与内存。

## 参考文献

[1] Turing A M. On Computable Numbers, with an Application to the Entscheidungsproblem[J]. Proceedings of the London Mathematical Society, 1936, s2-42(1): 230–265.

[2] Church A. An Unsolvable Problem of Elementary Number Theory[J]. American Journal of Mathematics, 1936, 58(2): 345–363.

[3] Radó T. On Non-Computable Functions[J]. Bell System Technical Journal, 1962, 41(3): 877–884.

[4] Rice H G. Classes of Recursively Enumerable Sets and Their Decision Problems[J]. Transactions of the American Mathematical Society, 1953, 74(2): 358–366.

[5] Gödel K. Über formal unentscheidbare Sätze der Principia Mathematica und verwandter Systeme I[J]. Monatshefte für Mathematik und Physik, 1931, 38: 173–198.

[6] Kleene S C. Introduction to Metamathematics[M]. Amsterdam: North-Holland, 1952.

[7] Minsky M L. Computation: Finite and Infinite Machines[M]. Englewood Cliffs: Prentice-Hall, 1967.

[8] Zuse K. Rechnender Raum[M]. Braunschweig: Vieweg, 1969.

[9] Fredkin E. Digital Mechanics: An Informational Process Based on Reversible Universal Cellular Automata[J]. Physica D, 1990, 45(1–3): 254–270.

[10] Wolfram S. A New Kind of Science[M]. Champaign: Wolfram Media, 2002.

[11] Landauer R. Irreversibility and Heat Generation in the Computing Process[J]. IBM Journal of Research and Development, 1961, 5(3): 183–191.

[12] Bostrom N. Are You Living in a Computer Simulation?[J]. The Philosophical Quarterly, 2003, 53(211): 243–255.

[13] Kolmogorov A N. Three Approaches to the Quantitative Definition of Information[J]. Problems of Information Transmission, 1965, 1(1): 1–7.

[14] Vapnik V N. The Nature of Statistical Learning Theory[M]. New York: Springer, 1995.

[15] Lamport L. Time, Clocks, and the Ordering of Events in a Distributed System[J]. Communications of the ACM, 1978, 21(7): 558–565.

[16] Brewer E A. Towards Robust Distributed Systems[C]//Proceedings of the 19th ACM Symposium on Principles of Distributed Computing (PODC). 2000: 7.

[17] Thompson K. Reflections on Trusting Trust[J]. Communications of the ACM, 1984, 27(8): 761–763.

[18] Rivest R L, Shamir A, Wagner D A. Time-lock Puzzles and Timed-release Crypto[R]. MIT LCS Technical Report, 1996.

[19] Lin S, Radó T. Computer Studies of Turing Machine Problems[J]. Journal of the ACM, 1965, 12(2): 196–212.

[20] Cook M. Universality in Elementary Cellular Automata[J]. Complex Systems, 2004, 15(1): 1–40.

---

*本文的技术内容基于 new01 仓库（framework-core，Java 17）的实际代码与测试（57 项，全部通过）。历史实现（Rule 110 元胞自动机、Minsky 计数器机、BootstrapMachine、UniversalMachine）已从仓库移除，文中以现有实现为准：ProtocolTuringMachine（纯协议，含自举；磁带格 TapeCell 为独立顶层类）。*
