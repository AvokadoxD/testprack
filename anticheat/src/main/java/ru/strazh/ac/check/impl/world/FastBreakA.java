package ru.strazh.ac.check.impl.world;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.BukkitCheck;
import ru.strazh.ac.check.hook.PacketCheck;
import ru.strazh.ac.check.hook.TickCheck;
import ru.strazh.ac.player.PlayerData;

/**
 * Скорость копания и Nuker:
 * <ul>
 *   <li>блок сломан заметно быстрее, чем позволяет скорость копания игрока
 *       (Paper Block#getBreakSpeed учитывает инструмент, чары, спешку, усталость,
 *       воду, землю и атрибуты); сервер сам допускает 30% — ловим систематическое
 *       ускорение;</li>
 *   <li>начато копание нескольких разных блоков за один тик (Nuker).</li>
 * </ul>
 */
@CheckInfo(name = "FastBreak", type = "A", category = Category.WORLD,
        description = "Слишком быстрое копание или несколько блоков за тик (FastBreak, Nuker)", maxVl = 10, decay = 1.0)
public final class FastBreakA extends Check implements PacketCheck, TickCheck, BukkitCheck {

    private volatile long startNanos;
    private volatile int sx = Integer.MIN_VALUE, sy, sz;
    private int startsThisTick;
    private Vector3i firstStart;

    public FastBreakA(PlayerData data) {
        super(data);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PLAYER_DIGGING) return;
        WrapperPlayClientPlayerDigging w = new WrapperPlayClientPlayerDigging(event);
        if (w.getAction() != DiggingAction.START_DIGGING) return;
        Vector3i p = w.getBlockPosition();
        startNanos = System.nanoTime();
        sy = p.getY();
        sz = p.getZ();
        sx = p.getX();
        if (firstStart == null) {
            firstStart = p;
        } else if (!firstStart.equals(p) && data.client().sendsTickEnd) {
            startsThisTick++;
            if (startsThisTick == 2 && data.abilities().gameMode != 1) {
                flag("копание " + (startsThisTick + 1) + " блоков за тик");
            }
        }
    }

    @Override
    public void onClientTickEnd() {
        firstStart = null;
        startsThisTick = 0;
    }

    @Override
    public void onBukkitEvent(Event event) {
        if (!(event instanceof BlockBreakEvent e)) return;
        if (e.getPlayer().getGameMode() == GameMode.CREATIVE || data.client().bedrock) return;
        Block b = e.getBlock();
        if (b.getX() != sx || b.getY() != sy || b.getZ() != sz) return;
        float speed = b.getBreakSpeed(e.getPlayer());
        if (speed <= 0 || speed >= 1.0f) return;
        double requiredTicks = Math.ceil(1.0 / speed);
        if (requiredTicks < 4) return;
        double elapsedTicks = (System.nanoTime() - startNanos) / 50_000_000.0;
        double ratio = elapsedTicks / requiredTicks;
        if (ratio < 0.8 && requiredTicks - elapsedTicks > 2.5) {
            if (increaseBuffer(1.0) > 2.0) {
                flag(b.getType().name().toLowerCase() + " за " + Math.round(elapsedTicks) + " из " + (int) requiredTicks + " тиков");
                e.setCancelled(true);
            }
        } else {
            decreaseBuffer(0.25);
        }
    }
}
