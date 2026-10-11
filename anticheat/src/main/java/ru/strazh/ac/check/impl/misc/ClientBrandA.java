package ru.strazh.ac.check.impl.misc;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.PacketCheck;
import ru.strazh.ac.player.PlayerData;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Бренд клиента или зарегистрированные каналы выдают чит-клиент. Приватные
 * клиенты обычно маскируются под «vanilla»/«fabric», но многие сборки и аддоны
 * не прячутся. Список — в config.yml (checks.clientbrand_a.blocked).
 */
@CheckInfo(name = "ClientBrand", type = "A", category = Category.MISC,
        description = "Бренд/каналы чит-клиента", maxVl = 1, decay = 0.0)
public final class ClientBrandA extends Check implements PacketCheck {

    private List<String> blocked = List.of();
    private boolean done;

    public ClientBrandA(PlayerData data) {
        super(data);
    }

    @Override
    protected void onReload() {
        String raw = cfg("blocked", "liquidbounce,meteor-client,meteorclient,wurst,aristois,impact,rusherhack,thunderhack,"
                + "future,boze,konas,inertia,aoba,nursultan,celestial,expensive,wexside,rockstar,deadcode,minced,"
                + "akrien,catlavan,evaware,exosware,zenith,bleachhack,sigma,novoline,vape,riseclient,augustus,myau");
        blocked = Arrays.stream(raw.split(",")).map(s -> s.trim().toLowerCase(Locale.ROOT)).filter(s -> !s.isEmpty()).toList();
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (done || event.getPacketType() != PacketType.Play.Client.PLUGIN_MESSAGE) return;
        String brand = data.client().brand.toLowerCase(Locale.ROOT);
        for (String b : blocked) {
            if (brand.contains(b)) {
                done = true;
                flag("бренд \"" + data.client().brand + "\"");
                return;
            }
        }
        for (String ch : data.client().channels) {
            String c = ch.toLowerCase(Locale.ROOT);
            for (String b : blocked) {
                if (c.startsWith(b + ":") || c.contains(":" + b)) {
                    done = true;
                    flag("канал \"" + ch + "\"");
                    return;
                }
            }
        }
    }
}
