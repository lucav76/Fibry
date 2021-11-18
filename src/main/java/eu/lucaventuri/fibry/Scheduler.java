package eu.lucaventuri.fibry;

import eu.lucaventuri.common.MathUtil;
import eu.lucaventuri.common.TimeProvider;

import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Class able to postpone messages to other actors;
 * this is not intended for ms precision
 */
public class Scheduler {
    private class Message<T> implements Comparable<Message<T>> {
        private final Actor<T, ?, ?> actor;
        private final T message;
        private final long time;
        private final long fixedRateMs;
        private final long fixedDelayMs;
        private final long maxMessages;

        private Message(Actor<T, ?, ?> actor, T message, long time, long repeatMs, long fixedDelayMs, long maxMessages) {
            this.actor = actor;
            this.message = message;
            this.time = time;
            this.fixedRateMs = repeatMs;
            this.fixedDelayMs = fixedDelayMs;
            this.maxMessages = maxMessages;

            assert this.actor != null;
            assert this.message != null;
            assert this.fixedRateMs == 0 || this.fixedDelayMs == 0;
        }

        @Override
        public int compareTo(Message that) {
            return MathUtil.signum(this.time - that.time);
        }

        Message<T> withTime(long newTime) {
            return new Message<>(actor, message, newTime, fixedRateMs, fixedDelayMs, maxMessages);
        }

        Message<T> withTimeDecreaseMax(long newTime) {
            return new Message<>(actor, message, newTime, fixedRateMs, fixedDelayMs, maxMessages-1);
        }
    }

    private final PriorityBlockingQueue<Message> queue = new PriorityBlockingQueue<>();
    private final SinkActorSingleTask<Void> schedulingActor;
    private final TimeProvider timeProvider;

    public Scheduler() {
        this(50, TimeProvider.fromSystemTime());
    }

    public Scheduler(int waitMs) {
        this(waitMs, TimeProvider.fromSystemTime());
    }

    public Scheduler(int waitMs, TimeProvider timeProvider) {
        this.timeProvider = timeProvider;

        this.schedulingActor = Stereotypes.def().runOnceWithThis((thisActor) -> {
            while (!thisActor.isExiting()) {
                try {
                    Message message = queue.poll(100, TimeUnit.MILLISECONDS);

                    if (message == null) {
                        timeProvider.sleepMs(waitMs);
                    } else {
                        long now = timeProvider.get();

                        if (now >= message.time) {
                            if (message.fixedDelayMs > 0 && message.maxMessages > 1) {
                                message.actor.sendMessageReturn(message.message).thenRunAsync(() -> {
                                    //System.out.println("Delay - Received: " + timeProvider.get() + " - delay: " + message.fixedDelayMs);
                                    queue.add(message.withTimeDecreaseMax(timeProvider.get() + message.fixedDelayMs));
                                });
                            } else {
                                message.actor.sendMessage(message.message);
                                if (message.fixedRateMs > 0 && message.maxMessages > 1) {
                                    queue.add(message.withTimeDecreaseMax(message.time + message.fixedRateMs));
                                }
                            }
                        } else {
                            queue.add(message);
                            timeProvider.sleepMs(Math.min(waitMs, message.time - now));
                        }
                    }
                } catch (InterruptedException e) {
                    System.err.println(e);
                }
            }

        });
    }

    public <T> void scheduleOnce(Actor<T, ?, ?> actor, T message, long delay, TimeUnit timeUnit) {
        schedule(actor, message, delay, timeUnit);
    }

    public <T> void schedule(Actor<T, ?, ?> actor, T message, long delay, TimeUnit timeUnit) {
        scheduleMs(actor, message, timeUnit.toMillis(delay), 0, 0, 1);
    }

    private <T> void scheduleMs(Actor<T, ?, ?> actor, T message, long waitMs, long fixedRateMs, long fixedDelayMs, long maxMessages) {
        queue.add(new Message(actor, message, timeProvider.get() + waitMs, fixedRateMs, fixedDelayMs, maxMessages));
    }

    public <T> void scheduleAtFixedRate(Actor<T, ?, ?> actor, T message, long initialDelay, long period, TimeUnit timeUnit) {
        scheduleMs(actor, message, timeUnit.toMillis(initialDelay), timeUnit.toMillis(period), 0, Long.MAX_VALUE);
    }

    public <T> void scheduleAtFixedRate(Actor<T, ?, ?> actor, T message, long initialDelay, long period, TimeUnit timeUnit, long maxMessages) {
        scheduleMs(actor, message, timeUnit.toMillis(initialDelay), timeUnit.toMillis(period), 0, maxMessages);
    }

    public <T> void scheduleWithFixedDelay(Actor<T, ?, ?> actor, T message, long initialDelay, long delay, TimeUnit timeUnit) {
        scheduleMs(actor, message, timeUnit.toMillis(initialDelay), 0, timeUnit.toMillis(delay), Long.MAX_VALUE);
    }

    public <T> void scheduleWithFixedDelay(Actor<T, ?, ?> actor, T message, long initialDelay, long delay, TimeUnit timeUnit, long maxMessages) {
        scheduleMs(actor, message, timeUnit.toMillis(initialDelay), 0, timeUnit.toMillis(delay), maxMessages);
    }

    void askExit() {
        schedulingActor.askExit();
    }

    public void waitForExit() {
        schedulingActor.waitForExit();
    }
}
