package ru.strazh.ac.util;

/** Кольцевой буфер последних N значений с быстрым доступом к статистике. */
public final class DoubleWindow {

    private final double[] data;
    private int size;
    private int head;

    public DoubleWindow(int capacity) {
        this.data = new double[capacity];
    }

    public void add(double v) {
        data[head] = v;
        head = (head + 1) % data.length;
        if (size < data.length) size++;
    }

    public boolean isFull() {
        return size == data.length;
    }

    public int size() {
        return size;
    }

    public int capacity() {
        return data.length;
    }

    public void clear() {
        size = 0;
        head = 0;
    }

    /** Значения в порядке добавления (от старых к новым). */
    public double[] toArray() {
        double[] out = new double[size];
        int start = (head - size + data.length) % data.length;
        for (int i = 0; i < size; i++) {
            out[i] = data[(start + i) % data.length];
        }
        return out;
    }

    /** Последнее добавленное значение. */
    public double last() {
        return size == 0 ? 0.0 : data[(head - 1 + data.length) % data.length];
    }

    /** i-е значение с конца (0 — последнее). */
    public double fromEnd(int i) {
        return data[(head - 1 - i + data.length * 2) % data.length];
    }

    public double mean() {
        double[] a = toArray();
        return Stats.mean(a, a.length);
    }

    public double stdDev() {
        double[] a = toArray();
        return Stats.stdDev(a, a.length);
    }

    public double max() {
        double m = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < size; i++) m = Math.max(m, data[i]);
        return size == 0 ? 0.0 : m;
    }

    public double min() {
        double m = Double.POSITIVE_INFINITY;
        for (int i = 0; i < size; i++) m = Math.min(m, data[i]);
        return size == 0 ? 0.0 : m;
    }

    public double sum() {
        double s = 0.0;
        for (int i = 0; i < size; i++) s += data[i];
        return s;
    }
}
