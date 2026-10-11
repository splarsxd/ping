# ping

A ping plugin for Minecraft Beta 1.7.3 servers.<br>
Supports: CraftBukkit CB1060 & Project Poseidon (Version: **1.1.12-260725-0600-6a7facd** released in **2026 July 25**)

```
/ping            → shows your own ping
/ping <player>   → shows another player's ping (also works from the console)
```

| Ping | Color |
|---|---|
| 0–150ms | green (`&a`) |
| 151–250ms | yellow (`&e`) |
| 251ms and up | red (`&c`) |

## Installation

1. Put `ping.jar` in your server's `plugins` folder.
2. Start the server.
3. On startup the console shows. `[ping] enabled (Craftbukkit 1060)` or `[ping] enabled (Poseidon 1060)`

No configuration and no client mod are needed.

## How does it work?

### Measurement<br>
Beta 1.7.3's `Packet0KeepAlive` carries no payload and is not echoed by the client, so it cannot be used for round-trip timing. The only server to client packet the vanilla client echoes is **`Packet106Transaction`** (window id, action id, accepted flag):

- When the client receives a transaction with `accepted = false`, it replies with the same window id and action id and `accepted = true`.
- The plugin sends `Packet106Transaction(0, id, false)` with `id` cycling through **30000–32000**. Real inventory transactions never use that range. The server only acts on replies whose action id matches a transaction it is waiting for, so probe replies have no gameplay effect.

One probe is in flight per player at a time.<br>
A new probe goes out on the next server tick after the previous reply arrives (`PROBE_PERIOD_TICKS = 1`, 50ms), or after a **5s** timeout if no reply came.

### Timing source<br>
The round-trip time is `reply.timestamp − sentAt`:

- `sentAt` is `System.currentTimeMillis()` when the probe is queued with `NetServerHandler.sendPacket`.
- `reply.timestamp` is `Packet.timestamp`, which the server sets in the packet's constructor. Packets are constructed on the **network reader thread** the moment they are read off the socket. So the server's main thread tick wait (up to 50ms) is *not* included.

What *is* included, and why:<br>
| Component | Size | Notes |
|---|---|---|
| Server network writer thread wait | CraftBukkit: 0–100ms. Poseidon: ~0–2ms | CraftBukkit's `NetworkWriterThread` sleeps 100ms when its queue is empty. Poseidon's `settings.faster-packets.enabled` (default on) replaces this with a 2ms loop. |
| Network round trip | real | The player's actual connection. |
| Client tick processing | 0–50ms | The Beta client processes incoming packets once per 50ms tick and sends the echo from that tick. |

The displayed value is the **minimum** sample from the last **2s**.<br>
Each of these waits can only add time, so the minimum over many samples is the best estimate of the true round trip. If no sample is younger than 2s, the most recent sample is shown.

### Platform hooks<br>
The plugin detects the platform at startup by checking for `org.bukkit.event.packet.PacketListener`:

- **Poseidon** (`PoseidonHook`): registers a `PacketListener` for `Event.Type.PACKET_RECEIVED` at `Monitor` priority and reads incoming `Packet106Transaction`s.
- **CraftBukkit** (`CraftBukkitHook`): CraftBukkit has no packet events. Instead, the plugin uses reflection on `Packet`'s static id class maps (`Packet.a`, `Packet.b`) to register **`PingTransaction`**, a subclass of `Packet106Transaction`, as the class for packet id 106. Its `a(NetHandler)` override reports the reply to the plugin and then calls `super.a(...)`, so normal inventory handling is unchanged. The original mapping is restored in `onDisable`. If packet 106 is already mapped to something else (another plugin), the hook refuses to install.

If no hook can be installed, `/ping` reports `Unavailable`.<br>
A player who has been sent 4 or more probes without a single reply also shows `Unavailable (Client does not answer to ping requests)`.

### TCP_NODELAY<br>
On the first probe to each player (within one tick of joining, or about 1s after the plugin loads for players already online), the plugin calls:

```java
((CraftPlayer) p).getHandle().netServerHandler.networkManager.socket.setTcpNoDelay(true);
```

This disables Nagle's algorithm on that connection, so small packets (hits, block changes, movement) are sent immediately instead of being held until the previous segment is acknowledged.<br>
This is the same idea as the 1.7.X `TcpNoDelayMod`, applied serverside. It affects all server to client traffic on that connection, not just probes.

- **CraftBukkit 1060** never sets this option.
- **Poseidon** has a `settings.enable-tpc-nodelay` option, which is off by default. The plugin sets it regardless.

The client server direction is controlled by the player's own operating system, so the plugin can't change it.

### Notes for testing<br>
- **The number is game round-trip time, not ICMP ping.** Expect a host machine baseline of about **50ms on Poseidon** and **80–140ms on CraftBukkit**.
- **Clumsy on the same PC double counts delay.** When the client and server run on the same PC, packets pass through tools like Clumsy twice, once leaving the client and once arriving at the server. A 50ms delay with Inbound and Outbound both ticked therefore adds about 200ms to the round trip, not 100ms. Clumsy also adds about 100ms of its own overhead even at 0ms delay.

### Source layout<br>
```
plugin.yml
com/ping/ping.java             main plugin: probing, sampling, /ping command, TCP_NODELAY
com/ping/Hook.java             platform hook interface
com/ping/PoseidonHook.java     Poseidon packet-event hook
com/ping/CraftBukkitHook.java  CraftBukkit packet-registry hook
com/ping/PingTransaction.java  Packet106Transaction subclass used by the CraftBukkit hook
```

### Building<br>
Compile against your server jar, targeting Java 8:

```
javac -source 8 -target 8 -cp srv_poseidon1060.jar -d build src/com/ping/*.java
```

Then package `build/` together with `plugin.yml` into `ping.jar`.

`PoseidonHook` only compiles against a Poseidon jar. The rest compiles against either server.<br>
The released jar is built against Poseidon and runs on CraftBukkit aswell, because `PoseidonHook` is only loaded when Poseidon is detected.
