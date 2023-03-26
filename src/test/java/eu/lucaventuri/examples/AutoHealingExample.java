package eu.lucaventuri.examples;

import eu.lucaventuri.common.SystemUtils;
import eu.lucaventuri.fibry.Actor;
import eu.lucaventuri.fibry.ActorSystem;
import eu.lucaventuri.fibry.HealRegistry;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class AutoHealingExample {
    public static void main(String[] args) throws ExecutionException, InterruptedException, TimeoutException {
        long start = System.currentTimeMillis();
        HealRegistry.INSTANCE.setFrequency(1, TimeUnit.SECONDS);
        HealRegistry.INSTANCE.setGracePeriod(1, TimeUnit.SECONDS);

        Actor<Long, Void, Void> actor = ActorSystem.anonymous().autoHealing(new ActorSystem.AutoHealingSettings(3, 5, () -> System.out.println("Notification by AutoHealing - Interruption"), () -> System.out.println("Notification by AutoHealing - New Thread"))).newActor((Long time) -> {
            System.out.println("Waiting for " + time + ": " + Thread.currentThread().getName() + " - " + Thread.currentThread().getId());
            SystemUtils.sleepEnsure(time);
        });

        actor.sendMessage(10L);
        actor.sendMessage(7_000L);
        actor.sendMessage(20L);
        actor.sendMessage(7_001L);
        actor.sendMessage(11L);
        actor.sendMessage(7_002L);
        actor.sendMessage(12L);
        actor.sendMessage(13L);
        actor.sendMessage(14L);
        actor.sendMessageReturn(15L).get();

        actor.sendPoisonPillFuture().get(1, TimeUnit.MINUTES);

        System.out.println("* Poison pill arrived");

        HealRegistry.INSTANCE.shutdown();
        System.out.println("Done");

        System.out.println("Total time: " + (System.currentTimeMillis() - start));
    }
}
