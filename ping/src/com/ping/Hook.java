package com.ping;

public interface Hook {
    void enable(ping plugin) throws Exception;
    void disable();

    String label();
}
