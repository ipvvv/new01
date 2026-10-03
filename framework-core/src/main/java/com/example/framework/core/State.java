package com.example.framework.core;

public interface State {

    String PAUSE = "PAUSE";
    String DESTROY = "DESTROY";
    String REPLAY = "REPLAY";

    String INITING = "INITING";
    String PAUSING = "PAUSING";
    String RUNNING = "RUNNING";
    String DESTROYING = "DESTROYING";
    String REPLAYING = "REPLAYING";

    String OTHER = "OTHER";
}
