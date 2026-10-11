package com.ping;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import net.minecraft.server.NetServerHandler;
import net.minecraft.server.Packet106Transaction;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class ping extends JavaPlugin {
    private static final long WINDOW = 2000;
    private static final long TIMEOUT = 5000;
    private static final long PROBE_PERIOD_TICKS = 1L;
    private static final short ID_MIN = 30000;
    private static final short ID_MAX = 32000;

    private static class State {
        short expectedId = 0;
        long sentAt = 0;
        int probesSent = 0;
        boolean noDelaySet = false;
        LinkedList<Sample> samples = new LinkedList<Sample>();
    }

    private static class Sample {
        final long at;
        final long rtt;
        Sample(long at, long rtt) { this.at = at; this.rtt = rtt; }
    }

    private final Map<String, State> states = new HashMap<String, State>();
    private short nextId = ID_MIN;
    static ping instance;
    private Hook hook;

    void handleReply(String playerName, int window, short id, boolean accepted, long arrivedAt) {
        State s = states.get(playerName.toLowerCase());
        if (s == null || s.sentAt == 0) return;
        if (window == 0 && id == s.expectedId && accepted) {
            s.samples.addLast(new Sample(arrivedAt, Math.max(0L, arrivedAt - s.sentAt)));

            while (s.samples.size() > 1 && s.samples.getFirst().at < arrivedAt - WINDOW) s.samples.removeFirst();
            s.sentAt = 0;
        }
    }

    public void onEnable() {
        PlayerListener quit = new PlayerListener() {
            public void onPlayerQuit(PlayerQuitEvent event) {
                states.remove(event.getPlayer().getName().toLowerCase());
            }
        };

        instance = this;
        boolean poseidon;
        try {
            Class.forName("org.bukkit.event.packet.PacketListener");
            poseidon = true;
        } catch (Throwable notPoseidon) {
            poseidon = false;
        }
        try {
            if (poseidon) {
                hook = (Hook) Class.forName("com.ping.PoseidonHook", true, ping.class.getClassLoader()).newInstance();
            } else {
                hook = new CraftBukkitHook();
            }
            hook.enable(this);
            System.out.println("[ping] enabled (" + hook.label() + ")");
        } catch (Throwable t) {
            System.out.println("[ping] Could not hook packet replies: " + t);
            hook = null;
        }

        getServer().getPluginManager().registerEvent(Event.Type.PLAYER_QUIT, quit, Event.Priority.Monitor, this);

        getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            public void run() {
                probeAll();
            }
        }, 20L, PROBE_PERIOD_TICKS);
    }

    public void onDisable() {
        if (hook != null) hook.disable();
        hook = null;
        instance = null;
        states.clear();
        System.out.println("[ping] disabled.");
    }

    private void probeAll() {
        long now = System.currentTimeMillis();
        for (Player p : getServer().getOnlinePlayers()) {
            try {
                String key = p.getName().toLowerCase();
                State s = states.get(key);
                if (s == null) {
                    s = new State();
                    states.put(key, s);
                }

                if (s.sentAt != 0 && now - s.sentAt <= TIMEOUT) continue;

                short id = nextId++;
                if (nextId > ID_MAX) nextId = ID_MIN;
                s.expectedId = id;
                s.sentAt = now;
                s.probesSent++;
                NetServerHandler handler = ((CraftPlayer) p).getHandle().netServerHandler;
                if (!s.noDelaySet) {
                    try {
                        handler.networkManager.socket.setTcpNoDelay(true);
                    } catch (Throwable ignored) {
                    }
                    s.noDelaySet = true;
                }
                handler.sendPacket(new Packet106Transaction(0, id, false));
            } catch (Throwable ignored) {
            }
        }
    }

    private long getPing(Player player) {
        if (hook == null) return -2L;
        State s = states.get(player.getName().toLowerCase());
        if (s == null || s.samples.isEmpty()) {
            return (s != null && s.probesSent >= 4) ? -2L : -1L;
        }
        long cutoff = System.currentTimeMillis() - WINDOW;
        long lowest = Long.MAX_VALUE;
        for (Sample x : s.samples) if (x.at >= cutoff && x.rtt < lowest) lowest = x.rtt;
        if (lowest == Long.MAX_VALUE) lowest = s.samples.getLast().rtt;
        return lowest;
    }

    private String format(long ping) {
        if (ping == -1) return ChatColor.GRAY + "Measuring..";
        if (ping == -2) return ChatColor.RED + "Unavailable (Client does not answer to ping requests)";

        ChatColor c = ping <= 150 ? ChatColor.GREEN : (ping <= 250 ? ChatColor.YELLOW : ChatColor.RED);
        return c + "" + ping + "ms";
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("ping")) return false;

        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Console must specify a player: /ping <player>");
                return true;
            }
            sender.sendMessage(ChatColor.GOLD + "Ping: " + format(getPing((Player) sender)));
            return true;
        }

        if (args.length == 1) {
            Player target = getServer().getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player '" + args[0] + "' is not online.");
                return true;
            }
            sender.sendMessage(ChatColor.GOLD + target.getName() + "'s ping: " + format(getPing(target)));
            return true;
        }

        sender.sendMessage(ChatColor.RED + "Usage: /ping [player]");
        return true;
    }
}
