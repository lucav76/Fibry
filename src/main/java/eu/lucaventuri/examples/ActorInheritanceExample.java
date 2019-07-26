package eu.lucaventuri.examples;

import eu.lucaventuri.common.SystemUtils;
import eu.lucaventuri.fibry.CreationStrategy;
import eu.lucaventuri.fibry.CustomActor;
import eu.lucaventuri.fibry.CustomActorWithResult;
import eu.lucaventuri.fibry.FibryQueue;

import javax.sound.midi.SoundbankResource;
import java.util.concurrent.ExecutionException;

class ActorInherit extends CustomActor<Object, Void, Void> {
    protected ActorInherit() {
        super(new FibryQueue<>(), null);

        closeStrategy = CloseStrategy.SEND_POISON_PILL;
        CreationStrategy.AUTO.start(this);
    }

    @Override
    protected void onMessage(Object message) {
        System.out.println(message);
    }
}

class ActorInheritSlow extends CustomActor<Object, Void, Void> {
    final int ms;

    protected ActorInheritSlow(int ms, boolean waitOnClose) {
        super(new FibryQueue<>(), null);

        this.ms = ms;
        closeStrategy = waitOnClose ? CloseStrategy.SEND_POISON_PILL_AND_WAIT : CloseStrategy.SEND_POISON_PILL;
        CreationStrategy.AUTO.start(this);
    }

    @Override
    protected void onMessage(Object message) {
        System.out.println(message);
        SystemUtils.sleep(ms);
    }
}


class ActorInherit2 extends CustomActorWithResult<Integer, Integer, Void> {
    protected ActorInherit2() {
        super(new FibryQueue<>(), null);

        closeStrategy = CloseStrategy.SEND_POISON_PILL;
        CreationStrategy.AUTO.start(this);
    }

    @Override
    protected Integer onMessage(Integer message) {
        return message * message;
    }
}

public class ActorInheritanceExample {
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        try (ActorInherit actor = new ActorInherit();
             ActorInherit2 actor2 = new ActorInherit2();) {

            actor.sendMessage("Test");
            actor.sendMessage(1.3);

            System.out.println("Actor 2: " + actor2.sendMessageReturn(4).get());
            System.out.println("Actor 2: " + actor2.sendMessageReturn(5).get());
        }

        try (ActorInheritSlow actor = new ActorInheritSlow(150, false)) {
            actor.sendMessage("Slow Test");
        }

        System.out.println("Exited 1");

        try (ActorInheritSlow actor = new ActorInheritSlow(150, true)) {
            actor.sendMessage("Slow Test 2");
        }

        System.out.println("Exited 2");
    }
}
