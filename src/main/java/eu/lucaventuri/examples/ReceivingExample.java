package eu.lucaventuri.examples;

import eu.lucaventuri.fibry.MessageReceiver;
import eu.lucaventuri.fibry.ActorSystem;
import eu.lucaventuri.fibry.ReceivingActor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;

public class ReceivingExample {
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        BiFunction<MessageReceiver<Object>, Object, String> actorLogicReturn = (rec, msg) -> {
            if (msg instanceof Integer) {
                int n = ((Integer) msg).intValue();

                String lookFor = "name" + n + ":";
                String name = rec.receive(String.class, s -> s.startsWith(lookFor));

                if (name == null)
                    return "NoName for " + n;

                return "Name of " + n + ": " + name.substring(lookFor.length());
            } else {
                System.out.println("Skipping " + msg);
                return "Skipping " + msg;
            }
        };

        ReceivingActor<Object, String, Object> actor = ActorSystem.anonymous().initialState(null).newReceivingActorWithReturn(actorLogicReturn);

        CompletableFuture<String> res1 = actor.sendMessageReturn(1);
        actor.sendMessage("Blah!");
        CompletableFuture<String> res2 = actor.sendMessageReturn(2);
        actor.sendMessage("name3: Stefano");
        actor.sendMessage("name2: Marco");
        actor.sendMessage("name4: Test");
        actor.sendMessage("name1: Luca");
        CompletableFuture<String> res5 = actor.sendMessageReturn(5);

        System.out.println(res1.get());
        System.out.println(res2.get());
        //System.out.println(res5.get());

    }

}
