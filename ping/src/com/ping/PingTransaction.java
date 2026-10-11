package com.ping;

import net.minecraft.server.NetHandler;
import net.minecraft.server.NetServerHandler;
import net.minecraft.server.Packet106Transaction;

public class PingTransaction extends Packet106Transaction {
    static volatile ping owner;

    public PingTransaction() {
        super();
    }

    public void a(NetHandler handler) {
        try {
            ping p = owner;
            if (p != null && handler instanceof NetServerHandler) {
                NetServerHandler h = (NetServerHandler) handler;
                p.handleReply(h.player.name, this.a, this.b, this.c, this.timestamp);
            }
        } catch (Throwable ignored) {
        }
        super.a(handler);
    }
}
