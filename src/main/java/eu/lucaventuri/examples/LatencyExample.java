package eu.lucaventuri.examples;

import eu.lucaventuri.common.Exceptions;
import eu.lucaventuri.common.SystemUtils;
import eu.lucaventuri.fibry.Actor;
import eu.lucaventuri.fibry.ActorSystem;
import eu.lucaventuri.fibry.ActorUtils;
import eu.lucaventuri.fibry.CreationStrategy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class LatencyExample {
    public static void main(String[] args) {
        boolean threads = args.length == 1 && args[0].equals("threads");
        try (Actor<Integer, Integer, Void> actor = ActorSystem.anonymous().strategy(threads ? CreationStrategy.THREAD : CreationStrategy.AUTO).newActorWithReturn(n -> n + 1)) {
            System.out.println("Using " + (threads ? "threads" : "auto - Fibers available: " + ActorUtils.areFibersAvailable()));

            AtomicInteger result = new AtomicInteger();
            long ms = SystemUtils.time(() -> result.set(actor.sendMessageReturnWait(1, 0)));

            System.out.println(result + " computed in " + ms + " ms using an actor");

            long msThread = SystemUtils.time(() -> {
                CountDownLatch latch = new CountDownLatch(1);

                new Thread(() -> {
                    result.set(100);
                    latch.countDown();
                }).start();

                Exceptions.silence(latch::await);
            });
            long msNone = SystemUtils.time(() -> {});

            System.out.println(result + " computed in " + msThread + " ms using a thread");
        }
    }
}
