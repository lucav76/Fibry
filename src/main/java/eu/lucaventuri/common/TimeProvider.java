package eu.lucaventuri.common;

import java.util.concurrent.atomic.AtomicLong;

@FunctionalInterface
public interface TimeProvider {
    long get();

    class FlexibleTime implements TimeProvider {
        private final AtomicLong time;

        public FlexibleTime(long timeMs) {
            this.time = new AtomicLong(timeMs);
        }

        public FlexibleTime() {
            this.time = new AtomicLong(0);
        }

        @Override
        public long get() {
            return time.get();
        }

        public void inc() {
            time.incrementAndGet();
        }

        public void inc(long delta) {
            time.addAndGet(delta);
        }

        public void dec() {
            time.decrementAndGet();
        }

        public void dec(long delta) {
            time.addAndGet(-delta);
        }
    }

    default void sleepMs(long timeout) {
        assert timeout >= 0;

        long now = get();
        long wakeupTime = now + timeout;

        while (wakeupTime > get()) {
            SystemUtils.sleep(0);
        }
    }

    default void sleepUntil(long wakeupTime) {
        long now = get();

        while (wakeupTime > get()) {
            SystemUtils.sleep(0);
        }
    }

    default TimeProvider freeze() {
        return fixedTime(get());
    }

    default FlexibleTime toFlexible() {
        return TimeProvider.flexible(get());
    }

    static TimeProvider fixedTime(long timeMs) {
        return () -> timeMs;
    }

    static TimeProvider fromSystemTime() {
        return new TimeProvider() {
            @Override
            public long get() {
                return System.currentTimeMillis();
            }

            @Override
            public void sleepMs(long timeout) {
                SystemUtils.sleepEnsure(timeout);
            }
        };
    }

    static TimeProvider fromSystemTimeRelative() {
        long start = System.currentTimeMillis();

        return new TimeProvider() {
            @Override
            public long get() {
                return System.currentTimeMillis() - start;
            }

            @Override
            public void sleepMs(long timeout) {
                SystemUtils.sleepEnsure(timeout);
            }
        };
    }

    static FlexibleTime flexible(long start) {
        return new FlexibleTime(start);
    }

    static FlexibleTime flexible() {
        return new FlexibleTime();
    }
}
