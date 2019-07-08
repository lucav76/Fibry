package eu.lucaventuri.examples;

import eu.lucaventuri.common.SystemUtils;
import eu.lucaventuri.fibry.Actor;
import eu.lucaventuri.fibry.ActorSystem;

import java.util.concurrent.atomic.AtomicBoolean;

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
    }
}

public class AutoClosableExample<main> {
    public static void main(String[] args) throws Exception {
        Actor<C, Void, Void> actor = ActorSystem.anonymous().newActor(c -> {
            c.use("actor first usage");
            SystemUtils.sleep(1000);
            c.use("actor second usage");
        });

        try (C c = new C()) {
            c.use("Inside try");
            actor.sendMessage(c);
        }

        System.out.println("End of try");
        SystemUtils.sleep(1500);
    }
}
