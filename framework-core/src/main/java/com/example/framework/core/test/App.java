package com.example.framework.core.test;

import java.math.BigInteger;

import com.example.framework.core.State;

public class App {
    public static void main(String[] args) throws Exception {
        TestUnit master = new TestUnit("default", "default", BigInteger.ZERO, BigInteger.ZERO, "master");
        TestUnit slave1 = new TestUnit("default", "default", BigInteger.ZERO, BigInteger.ZERO, "slave1");
        master.init();
        slave1.init();

        master
                .AI(slave1, State.RUNNING)
                .to(slave1)
                .AI(slave1, State.PAUSE)
                .to(slave1)
                .AI(slave1, State.RUNNING)
                .to(slave1)
                .AI(slave1, State.PAUSE)
                .to(slave1)
                .AI(slave1, State.DESTROY)
                .to(slave1)
                .AI(slave1, State.REPLAY)
                .to(slave1)
                ;
    }
}
