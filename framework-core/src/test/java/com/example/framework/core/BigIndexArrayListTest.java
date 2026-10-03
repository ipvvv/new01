package com.example.framework.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.time.DayOfWeek;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

class BigIndexArrayListTest {

    private static final BigInteger INT_MAX_PLUS = BigInteger.valueOf(Integer.MAX_VALUE).add(BigInteger.TEN);

    // ---- 基础读写与长度 ----

    @Test
    void setGetRoundTripAndOldValue() {
        BigIndexArrayList<String> list = new BigIndexArrayList<>();
        assertNull(list.set(BigInteger.valueOf(3), "a"));
        assertEquals("a", list.get(BigInteger.valueOf(3)));
        assertEquals("a", list.set(BigInteger.valueOf(3), "b")); // 返回旧值
        assertEquals("b", list.get(BigInteger.valueOf(3)));
        assertEquals(4, list.size());
        assertEquals(BigInteger.valueOf(4), list.bigSize());
    }

    @Test
    void unsetPositionWithinSizeReturnsNull() {
        BigIndexArrayList<String> list = new BigIndexArrayList<>();
        list.set(BigInteger.valueOf(10), "ten");
        assertNull(list.get(BigInteger.valueOf(5))); // 空洞
        assertNull(list.get(BigInteger.valueOf(9)));
        assertEquals(BigInteger.valueOf(11), list.bigSize());
    }

    @Test
    void getBeyondSizeThrows() {
        BigIndexArrayList<String> list = new BigIndexArrayList<>();
        list.set(BigInteger.valueOf(2), "x");
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(BigInteger.valueOf(3)));
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(BigInteger.valueOf(100)));
    }

    @Test
    void negativeIndexRoundTrip() {
        BigIndexArrayList<String> list = new BigIndexArrayList<>();
        assertNull(list.get(BigInteger.valueOf(-1))); // 未写入的负下标 → null
        list.set(BigInteger.valueOf(-1), "negOne");
        list.set(BigInteger.valueOf(-65536), "negSeg");   // 跨段负下标
        list.set(BigInteger.valueOf(-65537), "negSeg2");  // 相邻负段
        assertEquals("negOne", list.get(BigInteger.valueOf(-1)));
        assertEquals("negSeg", list.get(BigInteger.valueOf(-65536)));
        assertEquals("negSeg2", list.get(BigInteger.valueOf(-65537)));
        assertEquals(BigInteger.ZERO, list.bigSize()); // 负数写入不影响逻辑长度
        list.set(BigInteger.valueOf(2), "two");
        assertEquals(BigInteger.valueOf(3), list.bigSize()); // 正数写入照常扩容
        assertEquals("negOne", list.get(BigInteger.valueOf(-1))); // 正负互不干扰
        assertEquals("two", list.get(BigInteger.valueOf(2)));
    }

    // ---- 分段边界与树增长 ----

    @Test
    void segmentBoundaryRoundTrip() {
        BigIndexArrayList<String> list = new BigIndexArrayList<>();
        list.set(BigInteger.valueOf(65535), "lastOfSeg0");
        list.set(BigInteger.valueOf(65536), "firstOfSeg1");
        list.set(BigInteger.valueOf(131072), "firstOfSeg2");
        assertEquals("lastOfSeg0", list.get(BigInteger.valueOf(65535)));
        assertEquals("firstOfSeg1", list.get(BigInteger.valueOf(65536)));
        assertEquals("firstOfSeg2", list.get(BigInteger.valueOf(131072)));
        assertNull(list.get(BigInteger.valueOf(65537)));
    }

    @Test
    void beyondIntMaxIndex() {
        BigIndexArrayList<String> list = new BigIndexArrayList<>();
        list.set(INT_MAX_PLUS, "big");
        assertEquals("big", list.get(INT_MAX_PLUS));
        assertEquals(INT_MAX_PLUS.add(BigInteger.ONE), list.bigSize());
    }

    @Test
    void multiLevelTreeIndex() {
        BigIndexArrayList<String> list = new BigIndexArrayList<>();
        BigInteger idx = BigInteger.ONE.shiftLeft(36); // segIdx = 2^20，树高 3 层
        list.set(idx, "x");
        assertEquals("x", list.get(idx));
        assertNull(list.get(idx.subtract(BigInteger.ONE)));
        assertEquals(idx.add(BigInteger.ONE), list.bigSize());
    }

    @Test
    void treeGrowthPreservesExistingData() {
        BigIndexArrayList<String> list = new BigIndexArrayList<>();
        list.set(BigInteger.valueOf(3), "three");
        BigInteger huge = BigInteger.ONE.shiftLeft(64); // 强制树向上生长多层
        list.set(huge, "huge");
        assertEquals("three", list.get(BigInteger.valueOf(3))); // 旧数据不受影响
        assertEquals("huge", list.get(huge));
        assertEquals(huge.add(BigInteger.ONE), list.bigSize());
    }

    @Test
    void arbitrarilyLargeIndexWorks() {
        BigIndexArrayList<String> list = new BigIndexArrayList<>();
        BigInteger huge = BigInteger.ONE.shiftLeft(100);
        list.set(huge, "infinity");
        assertEquals("infinity", list.get(huge));
        assertNull(list.get(huge.subtract(BigInteger.ONE)));
        assertEquals(huge.add(BigInteger.ONE), list.bigSize());
    }

    // ---- java.util.List 兼容层 ----

    @Test
    void jdkListApi() {
        BigIndexArrayList<String> list = new BigIndexArrayList<>();
        list.add("a");
        list.add("b");
        list.add(5, "f");
        assertEquals(6, list.size());
        assertEquals("a", list.get(0));
        assertEquals("f", list.get(5));
        assertEquals("b", list.set(1, "B"));
        assertEquals("B", list.get(1));
        assertTrue(list.contains("B"));
        assertEquals(1, list.indexOf("B"));
        assertEquals(1, list.lastIndexOf("B"));
    }

    @Test
    void iteratorFromAbstractList() {
        BigIndexArrayList<String> list = new BigIndexArrayList<>();
        list.add("a");
        list.add("b");
        list.add("c");
        Iterator<String> it = list.iterator();
        assertEquals("a", it.next());
        assertEquals("b", it.next());
        assertEquals("c", it.next());
        assertFalse(it.hasNext());
    }

    // ---- 稀疏查找 ----

    @Test
    void indexOfAcrossSegments() {
        BigIndexArrayList<String> list = new BigIndexArrayList<>();
        BigInteger b = BigInteger.ONE.shiftLeft(36);
        BigInteger c = BigInteger.ONE.shiftLeft(40);
        list.set(BigInteger.valueOf(5), "a");
        list.set(b, "b");
        list.set(c, "c");
        assertTrue(list.contains("b"));
        assertEquals(BigInteger.valueOf(5), list.bigIndexOf("a"));
        assertEquals(b, list.bigIndexOf("b"));
        assertEquals(c, list.bigLastIndexOf("c"));
        assertEquals(5, list.indexOf("a"));
        assertEquals(-1, list.indexOf("b")); // b 下标超过 int，int 版 indexOf 返回 -1
    }

    // ---- 移除与扩容 ----

    @Test
    void removeTailShrinksSize() {
        BigIndexArrayList<String> list = new BigIndexArrayList<>();
        list.add("a");
        list.add("b");
        assertEquals("b", list.remove(1));
        assertEquals(1, list.size());
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(BigInteger.ONE));
    }

    @Test
    void removeMiddleLeavesHole() {
        BigIndexArrayList<String> list = new BigIndexArrayList<>();
        list.add("a");
        list.add("b");
        list.add("c");
        list.remove(1);
        assertEquals(3, list.size()); // 中间移除不收缩
        assertNull(list.get(BigInteger.ONE));
        assertEquals("c", list.get(BigInteger.valueOf(2)));
    }

    @Test
    void removeSparseTailShrinksToPreviousMax() {
        BigIndexArrayList<String> list = new BigIndexArrayList<>();
        list.set(BigInteger.valueOf(5), "y");
        BigInteger far = BigInteger.ONE.shiftLeft(36);
        list.set(far, "x");
        assertEquals(far.add(BigInteger.ONE), list.bigSize());
        assertEquals("x", list.remove(far));
        assertEquals(BigInteger.valueOf(6), list.bigSize()); // 收缩到上一个最大下标 5 + 1
        assertEquals("y", list.get(BigInteger.valueOf(5)));
    }

    @Test
    void ensureIndexExtendsSizeWithoutAllocating() {
        BigIndexArrayList<String> list = new BigIndexArrayList<>();
        BigInteger idx = BigInteger.ONE.shiftLeft(120);
        list.ensureIndex(idx);
        assertEquals(idx.add(BigInteger.ONE), list.bigSize());
        assertNull(list.get(idx));
    }

    @Test
    void clearResets() {
        BigIndexArrayList<String> list = new BigIndexArrayList<>();
        list.set(BigInteger.ONE.shiftLeft(50), "x");
        list.clear();
        assertEquals(BigInteger.ZERO, list.bigSize());
        assertEquals(0, list.size());
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(BigInteger.ZERO));
        list.add("new");
        assertEquals("new", list.get(BigInteger.ZERO));
    }

    // =================================================================
    // 获取 Java 运行时对象的各种方式（不做断言，只打印 name / hashCode 等）
    // =================================================================

    /** 统一的打印工具：输出对象的运行时类型、身份哈希、toString。 */
    private static void dump(String tag, Object obj) {
        if (obj == null) {
            System.out.printf("%-38s | null%n", tag);
            return;
        }
        // System.out.printf("%-38s | class=%-42s | identityHash=%-12s | hashCode=%-12d | value=%s%n",
        //         tag,
        //         obj.getClass().getName(),
        //         Integer.toHexString(System.identityHashCode(obj)),
        //         obj.hashCode(),
        //         obj);
        System.out.println("-----"+System.identityHashCode(obj));
    }

    // ---- 一、new 关键字 ---- 

    @Test
    void obtain_byNewKeyword() {
        BigIndexArrayList<String> list = new BigIndexArrayList<>();
        dump("1.1 new 关键字", list);
        // dump("1.2 new 带初始化", new BigIndexArrayList<Integer>());
    }

    // ---- 二、Class 对象的获取途径 ----

    @Test
    void obtain_classObject() throws Exception {
        dump("2.1 类字面量 Xxx.class", BigIndexArrayList.class);
        dump("2.2 obj.getClass()", new BigIndexArrayList<>().getClass());
        dump("2.3 Class.forName(全限定名)", Class.forName("com.example.framework.core.BigIndexArrayList"));
        dump("2.4 当前线程上下文类加载器", Thread.currentThread().getContextClassLoader()
                .loadClass("com.example.framework.core.BigIndexArrayList"));
        dump("2.5 系统类加载器", ClassLoader.getSystemClassLoader()
                .loadClass("com.example.framework.core.BigIndexArrayList"));
        dump("2.6 类自身类加载器", BigIndexArrayList.class.getClassLoader());
        dump("2.7 基本类型 class", int.class);
        dump("2.8 包装类 TYPE", Integer.TYPE);
        dump("2.9 数组 class", String[].class);
        dump("2.10 二维数组 class", int[][].class);
        dump("2.11 接口 class", java.util.List.class);
        dump("2.12 枚举 class", java.time.DayOfWeek.class);
        dump("2.13 注解 class", Deprecated.class);
        dump("2.14 父类", BigIndexArrayList.class.getSuperclass());
        dump("2.15 超类链", BigIndexArrayList.class.getSuperclass().getSuperclass());
    }

    // ---- 三、反射创建新对象 ----

    @Test
    void obtain_byReflectionConstructors() throws Exception {
        Class<?> clazz = BigIndexArrayList.class;

        // 3.1 getDeclaredConstructor().newInstance()（推荐）
        dump("3.1 Constructor.newInstance()",
                clazz.getDeclaredConstructor().newInstance());

        // 3.2 public 构造器
        dump("3.2 getConstructor().newInstance()",
                clazz.getConstructor().newInstance());

        // 3.3 遍历全部构造器
        for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {
            ctor.setAccessible(true);
            dump("3.3 构造器 " + ctor.toGenericString(), ctor.newInstance());
        }

        // 3.4 newInstance()（已废弃，仅演示）
        @SuppressWarnings("deprecation")
        Object legacy = clazz.newInstance();
        dump("3.4 Class.newInstance()(已废弃)", legacy);
    }

    // ---- 四、MethodHandle 创建对象 ----

    @Test
    void obtain_byMethodHandle() throws Throwable {
        Class<?> clazz = BigIndexArrayList.class;
        MethodHandles.Lookup lookup = MethodHandles.lookup();

        // 4.1 unreflectConstructor
        MethodHandle ctorHandle = lookup.unreflectConstructor(clazz.getDeclaredConstructor());
        dump("4.1 MethodHandle 构造器", ctorHandle.invoke());

        // 4.2 findConstructor
        MethodHandle ctorHandle2 = lookup.findConstructor(clazz, MethodType.methodType(void.class));
        dump("4.2 findConstructor", ctorHandle2.invoke());

        // 4.3 字段 getter 句柄
        BigIndexArrayList<String> list = new BigIndexArrayList<>();
        MethodHandles.Lookup privateLookup = MethodHandles.privateLookupIn(BigIndexArrayList.class, lookup);
        MethodHandle sizeGetter = privateLookup.findGetter(BigIndexArrayList.class, "size", BigInteger.class);
        dump("4.3 MethodHandle 读字段 size", sizeGetter.invoke(list));

        // 4.4 方法句柄
        MethodHandle bigSize = lookup.findVirtual(BigIndexArrayList.class, "bigSize",
                MethodType.methodType(BigInteger.class));
        dump("4.4 MethodHandle 调方法 bigSize()", bigSize.invoke(list));
    }

    // ---- 五、Unsafe 绕过构造器 ----

    @Test
    void obtain_byUnsafe() throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        Object unsafe = theUnsafe.get(null);

        Method allocateInstance = unsafeClass.getMethod("allocateInstance", Class.class);
        Object obj = allocateInstance.invoke(unsafe, BigIndexArrayList.class);
        dump("5.1 Unsafe.allocateInstance(不调构造器)", obj);

        // 通过 Unsafe 读字段偏移
        Method objectFieldOffset = unsafeClass.getMethod("objectFieldOffset", Field.class);
        Field sizeField = BigIndexArrayList.class.getDeclaredField("size");
        long offset = (long) objectFieldOffset.invoke(unsafe, sizeField);
        System.out.printf("%-38s | fieldOffest=%d%n", "5.2 Unsafe 字段偏移 size", offset);
    }

    // ---- 六、获取已有对象（静态字段 / 单例） ----

    @Test
    void obtain_existingStaticReferences() throws Exception {
        // 6.1 静态字段引用
        dump("6.1 静态常量 BigInteger.ZERO", BigInteger.ZERO);
        dump("6.2 枚举常量 DayOfWeek.MONDAY", DayOfWeek.MONDAY);
        dump("6.3 系统单例 Runtime", Runtime.getRuntime());
        dump("6.4 System.in", System.in);
        dump("6.5 环境变量 Map", System.getenv());
        dump("6.6 系统属性 Properties", System.getProperties());

        // 6.7 反射读静态字段
        Field zero = BigInteger.class.getField("ZERO");
        dump("6.7 反射读静态字段 BigInteger.ZERO", zero.get(null));

        // 6.8 反射枚举常量
        dump("6.8 Enum.valueOf", Enum.valueOf(DayOfWeek.class, "FRIDAY"));
    }

    // ---- 七、按名称获取已有对象（注册表） ----

    private static final Map<String, Object> REGISTRY = new ConcurrentHashMap<>();

    @Test
    void obtain_byNameRegistry() {
        BigIndexArrayList<String> list = new BigIndexArrayList<>();
        list.add("registered");

        // 7.1 注册对象
        REGISTRY.put("my.list", list);
        REGISTRY.putIfAbsent("my.singleton", new Object());

        // 7.2 按名称取回（同一实例）
        Object same = REGISTRY.get("my.list");
        dump("7.1 注册表按名称获取", same);
        System.out.printf("%-38s | sameInstance=%s%n", "7.2 是否同一实例", same == list);

        // 7.3 按名称 + 类型
        dump("7.3 按名称获取并转型",
                BigIndexArrayList.class.cast(REGISTRY.get("my.list")));

        // 7.4 遍历注册表
        REGISTRY.forEach((k, v) -> dump("7.4 注册表项 key=" + k, v));

        // 7.5 按对象自身名称注册/获取
        Named named = new Named("named.box", new BigIndexArrayList<>());
        REGISTRY.put(named.name(), named);
        dump("7.5 按对象自身名称获取", REGISTRY.get(named.name()));
    }

    /** 自带运行时名称的对象。 */
    private record Named(String name, Object payload) {
    }

    // ---- 八、动态代理 ----

    @Test
    void obtain_byDynamicProxy() {
        // 8.1 JDK 动态代理（基于接口）
        Object proxy = Proxy.newProxyInstance(
                BigIndexArrayListTest.class.getClassLoader(),
                new Class<?>[]{Runnable.class, Serializable.class},
                (p, method, args) -> {
                    System.out.printf("%-38s | proxy invoked method=%s%n",
                            "8.1 代理内部", method.getName());
                    return null;
                });
        dump("8.1 JDK 动态代理", proxy);
        dump("8.1 代理类名", proxy.getClass().getName());
        dump("8.1 代理父类", proxy.getClass().getSuperclass());
        for (Class<?> itf : proxy.getClass().getInterfaces()) {
            dump("8.1 代理接口", itf);
        }

        // 8.2 Proxy 类工厂
        dump("8.2 Proxy.getProxyClass", Proxy.getProxyClass(
                BigIndexArrayListTest.class.getClassLoader(), Runnable.class));

        // 8.3 代理调用（会触发 InvocationHandler）
        ((Runnable) proxy).run();
    }

    // ---- 九、数组 / 枚举 / 克隆 / 反序列化 ----

    @Test
    void obtain_byArrayEnumCloneSerialize() throws Exception {
        // 9.1 反射创建数组
        dump("9.1 Array.newInstance(一维)", Array.newInstance(int.class, 5));
        dump("9.2 Array.newInstance(二维)", Array.newInstance(String.class, 2, 3));
        dump("9.3 数组组件类型", Array.newInstance(int.class, 5).getClass().getComponentType());

        // 9.4 克隆
        BigIndexArrayList<String> origin = new BigIndexArrayList<>();
        origin.add("cloned");
        // dump("9.4 克隆对象", origin.clone());

        // 9.5 反序列化
        BigIndexArrayList<String> serial = new BigIndexArrayList<>();
        serial.add("serialized");
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(serial);
        }
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()))) {
            dump("9.5 反序列化还原对象", ois.readObject());
        }
    }

    // ---- 十、反射工厂 / Lambda 元工厂 / 反射成员对象 ----

    @Test
    void obtain_byFactoriesAndMemberObjects() throws Throwable {
        // 10.1 ReflectionFactory（JDK 内部反序列化路径，绕过构造器）
        try {
            Class<?> rfClass = Class.forName("sun.reflect.ReflectionFactory");
            Method getFactory = rfClass.getMethod("getReflectionFactory");
            Object factory = getFactory.invoke(null);
            Method newCtor = rfClass.getMethod("newConstructorForSerialization",
                    Class.class, Constructor.class);
            Constructor<?> fake = (Constructor<?>) newCtor.invoke(factory,
                    BigIndexArrayList.class, Object.class.getDeclaredConstructor());
            fake.setAccessible(true);
            dump("10.1 ReflectionFactory 造对象", fake.newInstance());
        } catch (ReflectiveOperationException e) {
            System.out.printf("%-38s | skipped=%s%n", "10.1 ReflectionFactory", e.getMessage());
        }

        // 10.2 LambdaMetafactory 生成函数式对象
        CallSite site = LambdaMetafactory.metafactory(
                MethodHandles.lookup(),
                "get",
                MethodType.methodType(Supplier.class),
                MethodType.methodType(Object.class),
                MethodHandles.lookup().findStatic(BigIndexArrayListTest.class, "makeList",
                        MethodType.methodType(Object.class)),
                MethodType.methodType(Object.class));
        Supplier<?> supplier = (Supplier<?>) site.getTarget().invokeExact();
        dump("10.2 LambdaMetafactory 生成对象", supplier.get());

        // 10.3 反射获取字段对象
        Field f = BigIndexArrayList.class.getDeclaredField("size");
        f.setAccessible(true);
        BigIndexArrayList<String> list = new BigIndexArrayList<>();
        list.add("x");
        dump("10.3 反射读字段 size", f.get(list));

        // 10.4 反射获取方法对象
        Method m = BigIndexArrayList.class.getDeclaredMethod("bigSize");
        dump("10.4 反射调用方法 bigSize()", m.invoke(list));

        // 10.5 反射获取注解对象
        dump("10.5 反射读注解 @Deprecated", Deprecated.class.getAnnotation(Deprecated.class));

        // 10.6 反射获取数组元素
        Object arr = Array.newInstance(String.class, 3);
        Array.set(arr, 0, "elem0");
        dump("10.6 反射读数组元素", Array.get(arr, 0));
    }

    private static Object makeList() {
        return new BigIndexArrayList<>();
    }

    // ---- 十一、ThreadLocal / 上下文对象 ----

    private static final ThreadLocal<Object> LOCAL = ThreadLocal.withInitial(BigIndexArrayList::new);

    @Test
    void obtain_byThreadLocalAndContext() {
        // 11.1 当前线程
        dump("11.1 Thread.currentThread()", Thread.currentThread());
        // 11.2 ThreadLocal
        dump("11.2 ThreadLocal.get()", LOCAL.get());
        // 11.3 StackWalker 当前栈帧的类
        StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames -> frames
                .map(StackWalker.StackFrame::getDeclaringClass)
                .findFirst()
                .map(c -> {
                    dump("11.3 StackWalker 当前类", c);
                    return c;
                })
                .orElse(null));
        // 11.4 MDC / 上下文类加载器
        dump("11.4 上下文类加载器", Thread.currentThread().getContextClassLoader());
    }

    // ---- 十二、序列化代理（readResolve）----

    @Test
    void obtain_byReadResolve() {
        // 12.1 @FunctionalInterface 等单例接口
        dump("12.1 Comparator.naturalOrder()", Comparator.naturalOrder());
        // 12.2 不可变空集合单例
        dump("12.2 Collections.emptyList()", Collections.emptyList());
        dump("12.3 List.of() 单例", List.of());
        // 12.4 Optional 单例
        dump("12.4 Optional.empty()", Optional.empty());
        // 12.5 缓存单例
        dump("12.5 Integer.valueOf(127) 缓存", Integer.valueOf(127));
        dump("12.6 Boolean.TRUE", Boolean.TRUE);
    }
}
