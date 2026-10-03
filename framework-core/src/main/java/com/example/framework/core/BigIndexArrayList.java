package com.example.framework.core;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.AbstractList;
import java.util.Map;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.TreeMap;

/**
 * 以 {@link BigInteger} 为下标、按需扩容的稀疏 ArrayList。
 *
 * <p>设计要点（对标 {@link ArrayList} 的「数组 + 下标直达」）：
 * <ul>
 *   <li>下标使用 {@link BigInteger}，可超过 {@link Integer#MAX_VALUE}，也支持负数
 *       ——下标轴是整数全集（负数同样按三段式分段寻址）；</li>
 *   <li>底层为<b>三段式固定分段</b>（每段 2^16 = 65536），通过<b>移位与掩码</b>（而非
 *       除法/取模）把下标拆成「超段号 / 中段号 / 段内偏移」，
 *       {@link #get(BigInteger)} 与 {@link #set(BigInteger, Object)} 均为 O(1)；</li>
 *   <li><b>稀疏分配</b>：从未写入的位置不占内存，读取返回 {@code null}；
 *       逻辑长度 {@link #size()} 等于「最大已写下标 + 1」（负数写入不影响逻辑长度）；</li>
 *   <li>支持任意大的 {@link BigInteger} 下标（含负数，稀疏分配，无固定上限）。</li>
 * </ul>
 *
 * <p>注意事项：与 {@link ArrayList} 一样<b>非线程安全</b>；稀疏语义下
 * {@code null} 元素与「未写入」不可区分（{@code contains(null)} 等语义受影响）。
 *
 * @param <E> 元素类型
 */
public class BigIndexArrayList<E> extends AbstractList<E> implements RandomAccess, Serializable {

    private static final long serialVersionUID = 1L;

    /** 每段元素个数的位数：2^16 = 65536。 */
    private static final int SEG_BITS = 16;
    private static final int SEG_SIZE = 1 << SEG_BITS;
    private static final int SEG_MASK = SEG_SIZE - 1;

    private static final BigInteger MAX_INT = BigInteger.valueOf(Integer.MAX_VALUE);

    /** 顶层超段表（BigInteger 下标、稀疏有序）；每个超段是 65536 个中段，每个中段是 65536 个元素的 {@code Object[]}。 */
    private final TreeMap<BigInteger, Object[][]> superSegments = new TreeMap<>();

    /** 逻辑长度 = 最大已写下标 + 1（负数写入不改变它）。 */
    private BigInteger size = BigInteger.ZERO;

    /** 队首游标（出队时前进、不搬移元素；仅供 offer/poll/peek 使用）。 */
    private BigInteger head = BigInteger.ZERO;

    public BigIndexArrayList() {
    }

    // ---------------- BigInteger 下标核心方法 ----------------

    /** 返回下标 {@code index} 处的元素；未写入的位置返回 {@code null}。O(1)。 */
    @SuppressWarnings("unchecked")
    public E get(BigInteger index) {
        checkIndexForGet(index);
        Object[][] superSeg = superSegments.get(index.shiftRight(32));
        if (superSeg == null) {
            return null;
        }
        Object[] seg = superSeg[index.shiftRight(SEG_BITS).intValue() & SEG_MASK];
        if (seg == null) {
            return null;
        }
        return (E) seg[index.intValue() & SEG_MASK];
    }

    /** 在下标 {@code index} 处写入 {@code element}，按需扩容，返回旧值。O(1)。支持负数下标。 */
    @SuppressWarnings("unchecked")
    public E set(BigInteger index, E element) {
        BigInteger superIdx = index.shiftRight(32);
        Object[][] superSeg = superSegments.get(superIdx);
        if (superSeg == null) {
            superSeg = new Object[SEG_SIZE][];
            superSegments.put(superIdx, superSeg);
        }
        int mid = index.shiftRight(SEG_BITS).intValue() & SEG_MASK;
        Object[] seg = superSeg[mid];
        if (seg == null) {
            seg = new Object[SEG_SIZE];
            superSeg[mid] = seg;
        }
        int off = index.intValue() & SEG_MASK;
        E old = (E) seg[off];
        seg[off] = element;
        BigInteger next = index.add(BigInteger.ONE);
        if (next.compareTo(size) > 0) {
            size = next;
        }
        modCount++;
        return old;
    }

    /** 扩容：使逻辑长度至少达到 {@code index + 1}（不分配数据段）；负数下标是 no-op。 */
    public void ensureIndex(BigInteger index) {
        BigInteger next = index.add(BigInteger.ONE);
        if (next.compareTo(size) > 0) {
            size = next;
        }
        modCount++;
    }

    /** 逻辑长度（BigInteger 版）。 */
    public BigInteger bigSize() {
        return size;
    }

    // ---------------- java.util.List 兼容层 ----------------

    @Override
    public int size() {
        return size.min(MAX_INT).intValue();
    }

    @Override
    public E get(int index) {
        return get(BigInteger.valueOf(index));
    }

    @Override
    public E set(int index, E element) {
        return set(BigInteger.valueOf(index), element);
    }

    /** 追加元素到末尾。 */
    @Override
    public boolean add(E element) {
        set(size, element);
        return true;
    }

    /** 稀疏语义：等价于在下标 {@code index} 写入（不整体位移）。 */
    @Override
    public void add(int index, E element) {
        set(BigInteger.valueOf(index), element);
    }

    /** 在 BigInteger 下标 {@code index} 处添加元素（稀疏语义：写入、不整体位移、按需扩容）。 */
    public void addIndex(BigInteger index, E element) {
        set(index, element);
    }

    // ---------------- 队列语义（O(1) 入队/出队，游标前进、不搬移） ----------------

    /** 入队：在队尾追加元素，等价于 {@link #add(Object)}。O(1)。 */
    public boolean offer(E element) {
        add(element);
        return true;
    }

    /** 查看队首元素但不移除；空队列返回 {@code null}。O(1)。 */
    public E peek() {
        return head.compareTo(size) >= 0 ? null : get(head);
    }

    /** 出队：移除并返回队首元素；空队列返回 {@code null}。O(1)。 */
    public E poll() {
        if (head.compareTo(size) >= 0) {
            return null;
        }
        E element = get(head);
        set(head, null);
        head = head.add(BigInteger.ONE);
        return element;
    }

    @Override
    public E remove(int index) {
        return remove(BigInteger.valueOf(index));
    }

    /** 移除下标处元素（稀疏语义：置空、不位移）；若移除的是尾部元素则收缩逻辑长度。 */
    public E remove(BigInteger index) {
        checkIndexForGet(index);
        E old = get(index);
        set(index, null);
        if (index.add(BigInteger.ONE).equals(size)) {
            BigInteger last = lastSetIndex();
            size = last == null ? BigInteger.ZERO : last.add(BigInteger.ONE);
        }
        modCount++;
        return old;
    }

    @Override
    public void clear() {
        superSegments.clear();
        size = BigInteger.ZERO;
        head = BigInteger.ZERO;
        modCount++;
    }

    @Override
    public boolean contains(Object o) {
        return bigIndexOf(o) != null;
    }

    @Override
    public int indexOf(Object o) {
        BigInteger i = bigIndexOf(o);
        return i == null || i.compareTo(MAX_INT) > 0 ? -1 : i.intValue();
    }

    @Override
    public int lastIndexOf(Object o) {
        BigInteger i = bigLastIndexOf(o);
        return i == null || i.compareTo(MAX_INT) > 0 ? -1 : i.intValue();
    }

    /** 稀疏扫描：只遍历已分配的段，返回首个相等元素下标；未找到返回 {@code null}。 */
    public BigInteger bigIndexOf(Object o) {
        for (Map.Entry<BigInteger, Object[][]> entry : superSegments.entrySet()) {
            BigInteger superBase = entry.getKey().shiftLeft(32);
            Object[][] superSeg = entry.getValue();
            for (int m = 0; m < SEG_SIZE; m++) {
                Object[] seg = superSeg[m];
                if (seg == null) {
                    continue;
                }
                BigInteger midBase = superBase.add(BigInteger.valueOf((long) m * SEG_SIZE));
                for (int off = 0; off < SEG_SIZE; off++) {
                    if (Objects.equals(seg[off], o)) {
                        return midBase.add(BigInteger.valueOf(off));
                    }
                }
            }
        }
        return null;
    }

    /** 稀疏扫描：从后往前，返回最后一个相等元素下标；未找到返回 {@code null}。 */
    public BigInteger bigLastIndexOf(Object o) {
        for (Map.Entry<BigInteger, Object[][]> entry : superSegments.descendingMap().entrySet()) {
            BigInteger superBase = entry.getKey().shiftLeft(32);
            Object[][] superSeg = entry.getValue();
            for (int m = SEG_SIZE - 1; m >= 0; m--) {
                Object[] seg = superSeg[m];
                if (seg == null) {
                    continue;
                }
                BigInteger midBase = superBase.add(BigInteger.valueOf((long) m * SEG_SIZE));
                for (int off = SEG_SIZE - 1; off >= 0; off--) {
                    if (Objects.equals(seg[off], o)) {
                        return midBase.add(BigInteger.valueOf(off));
                    }
                }
            }
        }
        return null;
    }

    // ---------------- 内部工具 ----------------

    /** 从后往前扫描已分配段，返回最后一个非空元素下标；无元素返回 null。 */
    private BigInteger lastSetIndex() {
        for (Map.Entry<BigInteger, Object[][]> entry : superSegments.descendingMap().entrySet()) {
            BigInteger superBase = entry.getKey().shiftLeft(32);
            Object[][] superSeg = entry.getValue();
            for (int m = SEG_SIZE - 1; m >= 0; m--) {
                Object[] seg = superSeg[m];
                if (seg == null) {
                    continue;
                }
                BigInteger midBase = superBase.add(BigInteger.valueOf((long) m * SEG_SIZE));
                for (int off = SEG_SIZE - 1; off >= 0; off--) {
                    if (seg[off] != null) {
                        return midBase.add(BigInteger.valueOf(off));
                    }
                }
            }
        }
        return null;
    }

    /** 越界检查：只拦 {@code index >= size}（逻辑长度之外）；负数下标合法（稀疏读写）。 */
    private void checkIndexForGet(BigInteger index) {
        if (index.compareTo(size) >= 0) {
            throw new IndexOutOfBoundsException("index=" + index + " size=" + size);
        }
    }
}
