package eu.lucaventuri.common;

public class BenchmarkResult {
    public final long min;
    public final long max;
    public final long average;
    public final long values[];

    public BenchmarkResult(long values[]) {
        this.values = values;
        this.min = min(values);
        this.max = max(values);
        this.average = average(values);
    }

    public static long min(long[] values) {
        long min = Long.MAX_VALUE;
        for (long value : values) min = Math.min(min, value);

        return min;
    }

    public static long max(long[] values) {
        long max = Long.MIN_VALUE;
        for (long value : values) max = Math.max(max, value);

        return max;
    }

    public static long average(long[] values) {
        long sum = 0;
        for (long value : values) sum += value;

        return sum / values.length;
    }

    @Override
    public String toString() {
        return "Min: " + min + "  -  Max: " + max + "  -  Average: " + average;
    }
}
