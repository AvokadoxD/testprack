package ru.strazh.ac.util;

/**
 * Точные копии математики клиента Minecraft (net.minecraft.util.Mth).
 * Клиент считает синус/косинус по таблице из 65536 значений, поэтому для
 * предсказания движения нужно использовать именно её, а не Math.sin.
 */
public final class McMath {

    private static final float[] SIN = new float[65536];
    public static final float DEG_TO_RAD = (float) (Math.PI / 180.0);
    public static final double DEG_TO_RAD_D = Math.PI / 180.0;

    static {
        for (int i = 0; i < SIN.length; i++) {
            SIN[i] = (float) Math.sin(i * Math.PI * 2.0 / 65536.0);
        }
    }

    private McMath() {
    }

    public static float sin(double value) {
        return SIN[(int) ((long) (value * 10430.378350470453) & 65535L)];
    }

    public static float cos(double value) {
        return SIN[(int) ((long) (value * 10430.378350470453 + 16384.0) & 65535L)];
    }

    public static float wrapDegrees(float value) {
        float f = value % 360.0F;
        if (f >= 180.0F) {
            f -= 360.0F;
        }
        if (f < -180.0F) {
            f += 360.0F;
        }
        return f;
    }

    public static double wrapDegrees(double value) {
        double d = value % 360.0;
        if (d >= 180.0) {
            d -= 360.0;
        }
        if (d < -180.0) {
            d += 360.0;
        }
        return d;
    }

    public static int floor(double value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }

    public static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }

    public static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }

    public static double square(double v) {
        return v * v;
    }

    /** Вектор взгляда как в Entity#calculateViewVector. */
    public static Vec3 viewVector(float pitch, float yaw) {
        float f = pitch * DEG_TO_RAD;
        float g = -yaw * DEG_TO_RAD;
        float h = cos(g);
        float i = sin(g);
        float j = cos(f);
        float k = sin(f);
        return new Vec3(i * j, -k, h * j);
    }

    /** Точный (не табличный) вектор взгляда — для лучей и аим-проверок. */
    public static Vec3 viewVectorPrecise(double pitch, double yaw) {
        double p = pitch * DEG_TO_RAD_D;
        double y = -yaw * DEG_TO_RAD_D;
        double cp = Math.cos(p);
        return new Vec3(Math.sin(y) * cp, -Math.sin(p), Math.cos(y) * cp);
    }

    /**
     * Ввод игрока -> ускорение в мире (Entity#getInputVector).
     * @param strafe   ввод влево(+)/вправо(-)
     * @param forward  ввод вперёд(+)/назад(-)
     */
    public static Vec3 inputVector(double strafe, double up, double forward, float speed, float yaw) {
        double lenSq = strafe * strafe + up * up + forward * forward;
        if (lenSq < 1.0E-7) {
            return Vec3.ZERO;
        }
        double x = strafe, y = up, z = forward;
        if (lenSq > 1.0) {
            double len = Math.sqrt(lenSq);
            x /= len;
            y /= len;
            z /= len;
        }
        x *= speed;
        y *= speed;
        z *= speed;
        float sin = sin(yaw * DEG_TO_RAD);
        float cos = cos(yaw * DEG_TO_RAD);
        return new Vec3(x * cos - z * sin, y, z * cos + x * sin);
    }

    /** Угол (в градусах) по горизонтали, под которым нужно смотреть из from в to. */
    public static double yawTo(double fromX, double fromZ, double toX, double toZ) {
        return Math.toDegrees(Math.atan2(toZ - fromZ, toX - fromX)) - 90.0;
    }

    public static double pitchTo(double fromX, double fromY, double fromZ, double toX, double toY, double toZ) {
        double dx = toX - fromX;
        double dy = toY - fromY;
        double dz = toZ - fromZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        return -Math.toDegrees(Math.atan2(dy, horizontal));
    }

    /** Кратчайшая разница углов в градусах, результат в [-180; 180). */
    public static double angleDiff(double a, double b) {
        return wrapDegrees(a - b);
    }
}
