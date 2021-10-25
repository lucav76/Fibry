package eu.lucaventuri.common;

public final class MathUtil {
    private MathUtil() {
    }

    public static int signum(long num) {
        return num < 0 ? -1 : (num > 0 ? 1 : 0);
    }

    public static int signum(double num) {
        return num < 0 ? -1 : (num > 0 ? 1 : 0);
    }

    public static int signum(float num) {
        return num < 0 ? -1 : (num > 0 ? 1 : 0);
    }

    public static int ranged(int value, int min, int max) {
        return Math.min(max, Math.max(value, min));
    }

    public static long ranged(long value, long min, long max) {
        return Math.min(max, Math.max(value, min));
    }

    public static float ranged(float value, float min, float max) {
        return Math.min(max, Math.max(value, min));
    }

    public static double ranged(double value, double min, double max) {
        return Math.min(max, Math.max(value, min));
    }

    public static boolean inRangeExcluded(int value, int min, int max) {
        return value >= min && value < max;
    }

    public static boolean inRangeExcluded(long value, long min, long max) {
        return value >= min && value < max;
    }

    public static boolean inRangeExcluded(float value, float min, float max) {
        return value >= min && value < max;
    }

    public static boolean inRangeExcluded(double value, double min, double max) {
        return value >= min && value < max;
    }

    public static boolean inRangeIncluded(int value, int min, int max) {
        return value >= min && value <= max;
    }

    public static boolean inRangeIncluded(long value, long min, long max) {
        return value >= min && value <= max;
    }

    public static boolean inRangeIncluded(float value, float min, float max) {
        return value >= min && value <= max;
    }

    public static boolean inRangeIncluded(double value, double min, double max) {
        return value >= min && value <= max;
    }
}
