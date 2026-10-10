package ru.strazh.ac.util;

/** Неизменяемый axis-aligned bounding box, как AABB в Minecraft. */
public record AABB(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {

    public static AABB of(double x1, double y1, double z1, double x2, double y2, double z2) {
        return new AABB(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
    }

    /** Хитбокс сущности по позиции ног, ширине и высоте. */
    public static AABB ofEntity(double x, double y, double z, double width, double height) {
        double h = width / 2.0;
        return new AABB(x - h, y, z - h, x + h, y + height, z + h);
    }

    public AABB expand(double v) {
        return new AABB(minX - v, minY - v, minZ - v, maxX + v, maxY + v, maxZ + v);
    }

    public AABB expand(double x, double y, double z) {
        return new AABB(minX - x, minY - y, minZ - z, maxX + x, maxY + y, maxZ + z);
    }

    /** Растягивает бокс в направлении движения (как AABB#expandTowards). */
    public AABB expandTowards(double x, double y, double z) {
        double a = minX, b = minY, c = minZ, d = maxX, e = maxY, f = maxZ;
        if (x < 0) a += x; else d += x;
        if (y < 0) b += y; else e += y;
        if (z < 0) c += z; else f += z;
        return new AABB(a, b, c, d, e, f);
    }

    public AABB offset(double x, double y, double z) {
        return new AABB(minX + x, minY + y, minZ + z, maxX + x, maxY + y, maxZ + z);
    }

    public AABB union(AABB o) {
        return new AABB(Math.min(minX, o.minX), Math.min(minY, o.minY), Math.min(minZ, o.minZ),
                Math.max(maxX, o.maxX), Math.max(maxY, o.maxY), Math.max(maxZ, o.maxZ));
    }

    public boolean intersects(AABB o) {
        return minX < o.maxX && maxX > o.minX && minY < o.maxY && maxY > o.minY && minZ < o.maxZ && maxZ > o.minZ;
    }

    public boolean intersects(double x1, double y1, double z1, double x2, double y2, double z2) {
        return minX < x2 && maxX > x1 && minY < y2 && maxY > y1 && minZ < z2 && maxZ > z1;
    }

    public boolean contains(double x, double y, double z) {
        return x >= minX && x < maxX && y >= minY && y < maxY && z >= minZ && z < maxZ;
    }

    public double width() {
        return maxX - minX;
    }

    public double height() {
        return maxY - minY;
    }

    public Vec3 center() {
        return new Vec3((minX + maxX) / 2.0, (minY + maxY) / 2.0, (minZ + maxZ) / 2.0);
    }

    /** Квадрат расстояния от точки до ближайшей точки бокса (0, если внутри). */
    public double distanceSquared(double x, double y, double z) {
        double dx = Math.max(Math.max(minX - x, 0), x - maxX);
        double dy = Math.max(Math.max(minY - y, 0), y - maxY);
        double dz = Math.max(Math.max(minZ - z, 0), z - maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Пересечение луча с боксом (slab-метод).
     * @return расстояние до точки входа, 0 если начало луча внутри, или -1 если пересечения нет
     */
    public double rayIntersect(double ox, double oy, double oz, double dx, double dy, double dz, double maxDist) {
        if (contains(ox, oy, oz)) {
            return 0.0;
        }
        double tMin = 0.0;
        double tMax = maxDist;
        double[] o = {ox, oy, oz};
        double[] d = {dx, dy, dz};
        double[] mn = {minX, minY, minZ};
        double[] mx = {maxX, maxY, maxZ};
        for (int i = 0; i < 3; i++) {
            if (Math.abs(d[i]) < 1.0E-9) {
                if (o[i] < mn[i] || o[i] > mx[i]) {
                    return -1.0;
                }
            } else {
                double inv = 1.0 / d[i];
                double t1 = (mn[i] - o[i]) * inv;
                double t2 = (mx[i] - o[i]) * inv;
                if (t1 > t2) {
                    double tmp = t1;
                    t1 = t2;
                    t2 = tmp;
                }
                tMin = Math.max(tMin, t1);
                tMax = Math.min(tMax, t2);
                if (tMin > tMax) {
                    return -1.0;
                }
            }
        }
        return tMin;
    }

    /** Вычисляет допустимое смещение по оси X до упора в этот бокс (VoxelShape#collide). */
    public double collideX(AABB moving, double dx) {
        if (moving.maxY <= minY || moving.minY >= maxY || moving.maxZ <= minZ || moving.minZ >= maxZ) {
            return dx;
        }
        if (dx > 0.0 && moving.maxX <= minX + 1.0E-7) {
            double d = minX - moving.maxX;
            if (d < dx) dx = d;
        } else if (dx < 0.0 && moving.minX >= maxX - 1.0E-7) {
            double d = maxX - moving.minX;
            if (d > dx) dx = d;
        }
        return dx;
    }

    public double collideY(AABB moving, double dy) {
        if (moving.maxX <= minX || moving.minX >= maxX || moving.maxZ <= minZ || moving.minZ >= maxZ) {
            return dy;
        }
        if (dy > 0.0 && moving.maxY <= minY + 1.0E-7) {
            double d = minY - moving.maxY;
            if (d < dy) dy = d;
        } else if (dy < 0.0 && moving.minY >= maxY - 1.0E-7) {
            double d = maxY - moving.minY;
            if (d > dy) dy = d;
        }
        return dy;
    }

    public double collideZ(AABB moving, double dz) {
        if (moving.maxX <= minX || moving.minX >= maxX || moving.maxY <= minY || moving.minY >= maxY) {
            return dz;
        }
        if (dz > 0.0 && moving.maxZ <= minZ + 1.0E-7) {
            double d = minZ - moving.maxZ;
            if (d < dz) dz = d;
        } else if (dz < 0.0 && moving.minZ >= maxZ - 1.0E-7) {
            double d = maxZ - moving.minZ;
            if (d > dz) dz = d;
        }
        return dz;
    }
}
