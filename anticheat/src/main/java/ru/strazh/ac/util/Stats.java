package ru.strazh.ac.util;

import java.util.Arrays;

/** Статистика по выборкам для эвристических проверок (кликеры, аим). */
public final class Stats {

    /** Множитель для перевода дробных дельт ротации в целые при поиске НОД. */
    public static final double EXPANDER = Math.pow(2, 24);

    private Stats() {
    }

    public static double mean(double[] v, int n) {
        if (n == 0) return 0.0;
        double s = 0.0;
        for (int i = 0; i < n; i++) s += v[i];
        return s / n;
    }

    public static double variance(double[] v, int n) {
        if (n < 2) return 0.0;
        double m = mean(v, n);
        double s = 0.0;
        for (int i = 0; i < n; i++) {
            double d = v[i] - m;
            s += d * d;
        }
        return s / (n - 1);
    }

    public static double stdDev(double[] v, int n) {
        return Math.sqrt(variance(v, n));
    }

    /** Коэффициент асимметрии (выборочный, скорректированный). */
    public static double skewness(double[] v, int n) {
        if (n < 3) return 0.0;
        double m = mean(v, n);
        double sd = stdDev(v, n);
        if (sd < 1.0E-12) return 0.0;
        double s = 0.0;
        for (int i = 0; i < n; i++) {
            double d = (v[i] - m) / sd;
            s += d * d * d;
        }
        return s * n / ((double) (n - 1) * (n - 2));
    }

    /** Избыточный эксцесс (excess kurtosis), 0 для нормального распределения. */
    public static double kurtosis(double[] v, int n) {
        if (n < 4) return 0.0;
        double m = mean(v, n);
        double m2 = 0.0, m4 = 0.0;
        for (int i = 0; i < n; i++) {
            double d = v[i] - m;
            double d2 = d * d;
            m2 += d2;
            m4 += d2 * d2;
        }
        m2 /= n;
        m4 /= n;
        if (m2 < 1.0E-12) return 0.0;
        return m4 / (m2 * m2) - 3.0;
    }

    public static double median(double[] v, int n) {
        if (n == 0) return 0.0;
        double[] c = Arrays.copyOf(v, n);
        Arrays.sort(c);
        return n % 2 == 1 ? c[n / 2] : (c[n / 2 - 1] + c[n / 2]) / 2.0;
    }

    /** Количество выбросов по правилу Тьюки (1.5 IQR). */
    public static int outliers(double[] v, int n) {
        if (n < 4) return 0;
        double[] c = Arrays.copyOf(v, n);
        Arrays.sort(c);
        double q1 = c[n / 4];
        double q3 = c[(3 * n) / 4];
        double iqr = q3 - q1;
        double lo = q1 - 1.5 * iqr;
        double hi = q3 + 1.5 * iqr;
        int out = 0;
        for (int i = 0; i < n; i++) {
            if (c[i] < lo || c[i] > hi) out++;
        }
        return out;
    }

    /** Число различных значений (с точностью eps). */
    public static int distinct(double[] v, int n, double eps) {
        if (n == 0) return 0;
        double[] c = Arrays.copyOf(v, n);
        Arrays.sort(c);
        int d = 1;
        for (int i = 1; i < n; i++) {
            if (c[i] - c[i - 1] > eps) d++;
        }
        return d;
    }

    /** Энтропия Шеннона (бит) по гистограмме с шагом bucket. */
    public static double entropy(double[] v, int n, double bucket) {
        if (n == 0) return 0.0;
        java.util.HashMap<Long, Integer> counts = new java.util.HashMap<>();
        for (int i = 0; i < n; i++) {
            counts.merge(Math.round(v[i] / bucket), 1, Integer::sum);
        }
        double e = 0.0;
        for (int c : counts.values()) {
            double p = (double) c / n;
            e -= p * (Math.log(p) / Math.log(2));
        }
        return e;
    }

    /** Самое частое значение (мода) и его частота. Возвращает {значение, частота}. */
    public static double[] mode(double[] v, int n, double eps) {
        if (n == 0) return new double[]{0, 0};
        double[] c = Arrays.copyOf(v, n);
        Arrays.sort(c);
        double best = c[0];
        int bestCount = 1, cur = 1;
        for (int i = 1; i < n; i++) {
            if (c[i] - c[i - 1] <= eps) {
                cur++;
            } else {
                cur = 1;
            }
            if (cur > bestCount) {
                bestCount = cur;
                best = c[i];
            }
        }
        return new double[]{best, bestCount};
    }

    /** Автокорреляция с лагом 1 (насколько каждое значение похоже на предыдущее). */
    public static double autocorrelation(double[] v, int n) {
        if (n < 3) return 0.0;
        double m = mean(v, n);
        double num = 0.0, den = 0.0;
        for (int i = 0; i < n; i++) {
            double d = v[i] - m;
            den += d * d;
            if (i > 0) num += d * (v[i - 1] - m);
        }
        return den < 1.0E-12 ? 1.0 : num / den;
    }

    /** НОД двух целых. */
    public static long gcd(long a, long b) {
        a = Math.abs(a);
        b = Math.abs(b);
        while (b != 0) {
            long t = a % b;
            a = b;
            b = t;
        }
        return a;
    }

    /** НОД двух дробных чисел через перевод в целые с множителем EXPANDER. */
    public static double gcd(double a, double b) {
        long g = gcd((long) (a * EXPANDER), (long) (b * EXPANDER));
        return g / EXPANDER;
    }

    /**
     * НОД вещественных чисел алгоритмом Евклида с порогом (для дельт ротации).
     */
    public static double gcdFloat(double a, double b, double threshold) {
        a = Math.abs(a);
        b = Math.abs(b);
        if (a < b) {
            double t = a;
            a = b;
            b = t;
        }
        int guard = 0;
        while (b > threshold && guard++ < 64) {
            double t = a - Math.floor(a / b) * b;
            a = b;
            b = t;
        }
        return a;
    }
}
