package ru.strazh.ac.util;

/** Неизменяемый трёхмерный вектор двойной точности. */
public record Vec3(double x, double y, double z) {

    public static final Vec3 ZERO = new Vec3(0, 0, 0);

    public Vec3 add(Vec3 o) {
        return new Vec3(x + o.x, y + o.y, z + o.z);
    }

    public Vec3 add(double dx, double dy, double dz) {
        return new Vec3(x + dx, y + dy, z + dz);
    }

    public Vec3 subtract(Vec3 o) {
        return new Vec3(x - o.x, y - o.y, z - o.z);
    }

    public Vec3 multiply(double f) {
        return new Vec3(x * f, y * f, z * f);
    }

    public Vec3 multiply(double fx, double fy, double fz) {
        return new Vec3(x * fx, y * fy, z * fz);
    }

    public Vec3 withY(double ny) {
        return new Vec3(x, ny, z);
    }

    public double dot(Vec3 o) {
        return x * o.x + y * o.y + z * o.z;
    }

    public double lengthSquared() {
        return x * x + y * y + z * z;
    }

    public double length() {
        return Math.sqrt(lengthSquared());
    }

    public double horizontalLengthSquared() {
        return x * x + z * z;
    }

    public double horizontalLength() {
        return Math.sqrt(horizontalLengthSquared());
    }

    public double distanceSquared(Vec3 o) {
        double dx = x - o.x, dy = y - o.y, dz = z - o.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public double distance(Vec3 o) {
        return Math.sqrt(distanceSquared(o));
    }

    public Vec3 normalize() {
        double len = length();
        return len < 1.0E-5 ? ZERO : new Vec3(x / len, y / len, z / len);
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.ROOT, "(%.4f, %.4f, %.4f)", x, y, z);
    }
}
