package eu.lucaventuri.examples;

import eu.lucaventuri.common.SystemUtils;
import eu.lucaventuri.fibry.Actor;
import eu.lucaventuri.fibry.ActorSystem;
import eu.lucaventuri.fibry.PartialActor;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

class C implements AutoCloseable {
    AtomicBoolean closed = new AtomicBoolean();

    @Override
    public void close() throws Exception {
        use("close");
        closed.set(true);
    }

    public void use(String reason) {
        if (closed.get())
            throw new IllegalArgumentException("Resource used after closing: " + reason + "!");

        System.out.println("OK: " + reason);
    }
}

public class AutoCloseableExample<main> {
    public static void main(String[] args) throws Exception {
        BiConsumer<C, PartialActor<C, Void>> actorLogic = (c, thisActor) -> {
            c.use("actor first usage");
            SystemUtils.sleep(1000);
            if (!thisActor.isExiting())
                c.use("actor second usage");
        };

        try (C c = new C(); Actor<C, Void, Void> actor = ActorSystem.anonymous().newActor(actorLogic)) {
            c.use("Inside try");
            actor.sendMessage(c);
            SystemUtils.sleep(500);
        }

        System.out.println("End of try");
        SystemUtils.sleep(1500);
    }
}
