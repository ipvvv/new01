package com.example.framework.core.test;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Delta —— 把“转移函数 δ”做成数据（代码即数据）。
 *
 * <p>自举的前提：δ 不能只是编译期的 Java 代码，而必须是世界线上
 * 可读写、可复制、可优化的数据。于是“自己编译自己”才成为可能：
 * 机器读自己的 δ → 生成等价的 δ' → 安装 δ'。
 */
public class Delta implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 规则表：key = "state:symbol"，value = "write,move,next"。 */
    private final Map<String, String> rules = new LinkedHashMap<>();

    public Delta put(int state, int symbol, int write, int move, int next) {
        rules.put(state + ":" + symbol, write + "," + move + "," + next);
        return this;
    }

    /** 查转移：state 读到 symbol 时返回 [write, move, next]；无规则返回 null。 */
    public int[] lookup(int state, int symbol) {
        String v = rules.get(state + ":" + symbol);
        if (v == null) {
            return null;
        }
        String[] parts = v.split(",");
        return new int[] { Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]) };
    }

    /** 代码即数据：把 δ 序列化为可存储、可传输、可复制的文本。 */
    public String toData() {
        StringBuilder sb = new StringBuilder();
        rules.forEach((k, v) -> sb.append(k).append('=').append(v).append(';'));
        return sb.toString();
    }

    /** 数据即代码：从文本反序列化出 δ。 */
    public static Delta fromData(String data) {
        Delta delta = new Delta();
        if (data == null || data.isBlank()) {
            return delta;
        }
        for (String entry : data.split(";")) {
            if (entry.isBlank()) {
                continue;
            }
            String[] kv = entry.split("=");
            String[] k = kv[0].split(":");
            String[] v = kv[1].split(",");
            delta.put(Integer.parseInt(k[0]), Integer.parseInt(k[1]),
                    Integer.parseInt(v[0]), Integer.parseInt(v[1]), Integer.parseInt(v[2]));
        }
        return delta;
    }

    /**
     * 编译优化：消除“不可达状态”的规则。
     * 语义等价（不可达的规则永远不会被触发），但规则更少——δ 更短。
     * 起始状态固定为 0。
     */
    public Delta optimize() {
        Set<Integer> reachable = new HashSet<>();
        Deque<Integer> queue = new ArrayDeque<>();
        queue.add(0);
        while (!queue.isEmpty()) {
            int s = queue.remove();
            if (!reachable.add(s)) {
                continue;
            }
            for (Map.Entry<String, String> e : rules.entrySet()) {
                String[] k = e.getKey().split(":");
                if (Integer.parseInt(k[0]) == s) {
                    queue.add(Integer.parseInt(e.getValue().split(",")[2]));
                }
            }
        }
        Delta out = new Delta();
        rules.forEach((key, value) -> {
            int s = Integer.parseInt(key.split(":")[0]);
            if (reachable.contains(s)) {
                out.rules.put(key, value);
            }
        });
        return out;
    }

    public int size() {
        return rules.size();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Delta other && rules.equals(other.rules);
    }

    @Override
    public int hashCode() {
        return rules.hashCode();
    }

    @Override
    public String toString() {
        return toData();
    }
}
