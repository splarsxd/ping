package com.ping;

import net.minecraft.server.Packet106Transaction;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.packet.PacketListener;
import org.bukkit.event.packet.PacketReceivedEvent;

public class PoseidonHook implements Hook {
    public void enable(final ping plugin) {
        PacketListener packets = new PacketListener() {
            public void onPacketReceived(PacketReceivedEvent event) {
                if (!(event.getPacket() instanceof Packet106Transaction)) return;
                Player p = event.getPlayer();
                if (p == null) return;
                Packet106Transaction t = (Packet106Transaction) event.getPacket();
                plugin.handleReply(p.getName(), t.a, t.b, t.c, t.timestamp);
            }
        };
        plugin.getServer().getPluginManager().registerEvent(Event.Type.PACKET_RECEIVED, packets, Event.Priority.Monitor, plugin);
    }

    public void disable() {
    }

    public String label() {
        return "Poseidon 1060";
    }
}
