package ru.strazh.ac.check.impl.movement;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientVehicleMove;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Camel;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import ru.strazh.ac.StrazhPlugin;
import ru.strazh.ac.check.Category;
import ru.strazh.ac.check.Check;
import ru.strazh.ac.check.CheckInfo;
import ru.strazh.ac.check.hook.PacketCheck;
import ru.strazh.ac.player.PlayerData;
import ru.strazh.ac.util.Text;

/**
 * Ванильный управляемый транспорт (лодки, лошади, свиньи, страйдеры):
 * BoatFly — лодка поднимается без воды под собой несколько тиков подряд;
 * EntitySpeed — скорость транспорта выше физически возможной.
 * Кастомный транспорт (Skript-машины, display-сиденья) не проверяется: им
 * управляет сервер, а клиент не шлёт VehicleMove.
 */
@CheckInfo(name = "Vehicle", type = "A", category = Category.MOVEMENT,
        description = "Полёт/ускорение ванильного транспорта (BoatFly, EntitySpeed)", maxVl = 10, decay = 1.5)
public final class VehicleA extends Check implements PacketCheck {

    private double lastX, lastY, lastZ;
    private boolean hasLast;
    private int risingTicks;

    public VehicleA(PlayerData data) {
        super(data);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.VEHICLE_MOVE) return;
        Vector3d p = new WrapperPlayClientVehicleMove(event).getPosition();
        if (!hasLast) {
            hasLast = true;
            lastX = p.getX();
            lastY = p.getY();
            lastZ = p.getZ();
            return;
        }
        double dx = p.getX() - lastX, dy = p.getY() - lastY, dz = p.getZ() - lastZ;
        lastX = p.getX();
        lastY = p.getY();
        lastZ = p.getZ();
        double x = p.getX(), y = p.getY(), z = p.getZ();
        data.queueMain(d -> evaluate(x, y, z, dx, dy, dz));
    }

    private void evaluate(double x, double y, double z, double dx, double dy, double dz) {
        Player player = data.player();
        if (player == null) return;
        Entity v = player.getVehicle();
        StrazhPlugin plugin = StrazhPlugin.get();
        if (v == null || data.vehicle().onCustomVehicle || (plugin != null && plugin.vehicleCompat().isCustomVehicle(v))) {
            hasLast = false;
            return;
        }
        long sinceMount = System.currentTimeMillis() - data.vehicle().lastMountMillis;
        if (sinceMount < 1500 || System.currentTimeMillis() - data.lastDamageMillis < 1000
                || System.currentTimeMillis() - data.vehicle().lastVehicleTeleportMillis < 1500) {
            risingTicks = 0;
            return;
        }
        World w = v.getWorld();
        if (!w.isChunkLoaded((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4)) return;
        boolean liquid = false, ice = false, solidBelow = false;
        for (int ix = -1; ix <= 1; ix++) {
            for (int iz = -1; iz <= 1; iz++) {
                for (int iy = -2; iy <= 1; iy++) {
                    Material m = w.getBlockAt((int) Math.floor(x) + ix, (int) Math.floor(y) + iy, (int) Math.floor(z) + iz).getType();
                    if (m == Material.WATER || m == Material.BUBBLE_COLUMN || m == Material.LAVA || m == Material.KELP
                            || m == Material.KELP_PLANT || m == Material.SEAGRASS || m == Material.TALL_SEAGRASS) liquid = true;
                    if (Tag.ICE.isTagged(m)) ice = true;
                    if (iy < 0 && m.isSolid()) solidBelow = true;
                }
            }
        }
        double dxz = Math.sqrt(dx * dx + dz * dz);
        String why = null;
        if (v instanceof Boat) {
            if (dy > 0.01 && !liquid) {
                if (++risingTicks >= 4) why = "лодка поднимается без воды (" + risingTicks + " тиков)";
            } else {
                risingTicks = 0;
            }
            double max = ice ? Double.MAX_VALUE : liquid ? 0.9 : 0.5;
            if (why == null && dxz > max) why = "скорость лодки " + Text.fmt(dxz, 3) + " > " + Text.fmt(max, 2);
        } else if (v instanceof LivingEntity mount && !(v instanceof Camel)) {
            if (v.getType().name().equals("HAPPY_GHAST")) return; // летает легально
            AttributeInstance speedAttr = mount.getAttribute(Attribute.MOVEMENT_SPEED);
            double speed = speedAttr == null ? 0.3 : speedAttr.getValue();
            double max = (v instanceof AbstractHorse ? speed * 2.6 : 1.2) + 0.35;
            if (dxz > max && !liquid && !ice) why = "скорость " + v.getType().name().toLowerCase() + " " + Text.fmt(dxz, 3)
                    + " > " + Text.fmt(max, 2);
            if (dy > 0.01 && !liquid && !solidBelow) {
                if (++risingTicks >= 20) why = "транспорт поднимается " + risingTicks + " тиков";
            } else {
                risingTicks = 0;
            }
        }
        if (why != null) {
            if (increaseBuffer(1.0) > 2.0) {
                flag(why);
                buffer = 1.0;
            }
        } else {
            decreaseBuffer(0.05);
        }
    }
}
