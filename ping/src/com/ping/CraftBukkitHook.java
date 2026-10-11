package com.ping;

import java.lang.reflect.Field;
import java.util.Map;

import net.minecraft.server.Packet;
import net.minecraft.server.Packet106Transaction;

@SuppressWarnings({"unchecked", "rawtypes"})
public class CraftBukkitHook implements Hook {
    private static final int ID = 106;
    private Map idToClass;
    private Map classToId;

    public void enable(ping plugin) throws Exception {
        Field fa = Packet.class.getDeclaredField("a");
        Field fb = Packet.class.getDeclaredField("b");
        fa.setAccessible(true);
        fb.setAccessible(true);
        idToClass = (Map) fa.get(null);
        classToId = (Map) fb.get(null);

        Object current = idToClass.get(Integer.valueOf(ID));
        if (current != Packet106Transaction.class) {
            throw new IllegalStateException("Packet 106 is already replaced by " + current);
        }

        PingTransaction.class.newInstance();

        PingTransaction.owner = plugin;
        classToId.put(PingTransaction.class, Integer.valueOf(ID));
        idToClass.put(Integer.valueOf(ID), PingTransaction.class);
    }

    public String label() {
        return "Craftbukkit 1060";
    }

    public void disable() {
        PingTransaction.owner = null;
        try {
            if (idToClass != null) idToClass.put(Integer.valueOf(ID), Packet106Transaction.class);
            if (classToId != null) classToId.remove(PingTransaction.class);
        } catch (Throwable ignored) {
        }
    }
}
