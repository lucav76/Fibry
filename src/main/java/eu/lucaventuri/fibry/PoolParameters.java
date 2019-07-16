package eu.lucaventuri.fibry;

public class PoolParameters {
    final int minSize;
    final int maxSize;
    final int scalingUpThreshold;
    final int scalingDownThreshold;
    final int scalingSpeed;
    final int timePollingMs;

    private PoolParameters(int minSize, int maxSize, int scalingUpThreshold, int scalingDownThreshold, int scalingSpeed, int timePollingMs) {
        this.minSize = Math.max(0, minSize);
        this.maxSize = Math.max(this.minSize, maxSize);
        this.scalingUpThreshold = Math.max(1, scalingUpThreshold);
        this.scalingDownThreshold = Math.min(this.scalingUpThreshold-1, scalingDownThreshold);
        this.scalingSpeed = Math.max(1, scalingSpeed);
        this.timePollingMs = Math.max(1, timePollingMs);
    }

    public static PoolParameters fixedSize(int size) {
        return new PoolParameters(size, size, Integer.MAX_VALUE, 0, 0, 1_000_000);
    }

    public static PoolParameters scaling(int minSize, int maxSize, int scalingUpThreshold, int scalingDownThreshold) {
        return new PoolParameters(minSize, maxSize, scalingUpThreshold, scalingDownThreshold, (maxSize-minSize) / 10, 1_000);
    }

    public static PoolParameters scaling(int minSize, int maxSize, int scalingUpThreshold, int scalingDownThreshold, int scalingSpeed, int timePollingMs) {
        return new PoolParameters(minSize, maxSize, scalingUpThreshold, scalingDownThreshold, scalingSpeed, timePollingMs);
    }
}