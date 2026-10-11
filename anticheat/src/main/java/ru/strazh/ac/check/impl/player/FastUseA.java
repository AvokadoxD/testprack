package ru.strazh.ac.check.impl.player;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.PacketCheck;
import ru.strazh.ac.check.hook.TickCheck;
import ru.strazh.ac.player.PlayerData;

/**
 * FastUse/FastEXP/FastPlace: больше трёх «использований» или установок блоков
 * за тик. Каждое нажатие ПКМ ванильно даёт одно использование; три за 50 мс —
 * это 60+ кликов в секунду. Обычная стрельба ПКМ (оружие на Skript) не страдает.
 */
@CheckInfo(name = "FastUse", type = "A", category = Category.PLAYER,
        description = "Слишком много использований/установок за тик (FastEXP, FastPlace, CrystalAura)", maxVl = 10, decay = 1.5)
public final class FastUseA extends Check implements PacketCheck, TickCheck {

    private int uses, places;
    private int limit;

    public FastUseA(PlayerData data) {
        super(data);
    }

    @Override
    protected void onReload() {
        limit = cfg("max-per-tick", 3);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.USE_ITEM) {
            if (++uses > limit) {
                event.setCancelled(true);
                if (uses == limit + 1) flag("use/tick>" + limit);
            }
        } else if (event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
            if (++places > limit) {
                event.setCancelled(true);
                if (places == limit + 1) flag("place/tick>" + limit);
            }
        }
    }

    @Override
    public void onClientTickEnd() {
        uses = 0;
        places = 0;
    }
}
