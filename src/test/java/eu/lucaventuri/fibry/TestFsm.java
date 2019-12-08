package eu.lucaventuri.fibry;

import eu.lucaventuri.fibry.fsm.*;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

enum States {
    A, B, C
}

public class TestFsm {
    private final Consumer<FsmContext<States, String>> consumerPrint = m -> System.out.println("From " + m.previousState + " to " + m.newState + " - message: " + m.message);
    private final MessageOnlyActor<FsmContext<States, String>, String, Void> actorPrint = ActorSystem.anonymous().newActorWithReturn(m -> {
        System.out.println("From " + m.previousState + " to " + m.newState + " - message: " + m.message);

        return "RET: " + m.message;
    });

    @Test(expected = IllegalArgumentException.class)
    public void testNotEnoughState() {
        var fsm = new FsmBuilderConsumer<States, String>()
                .addState(States.A, consumerPrint).goTo(States.B, "b").goTo(States.C, "c")
                .build();

    }

    @Test(expected = IllegalArgumentException.class)
    public void testUnknownState() {
        var fsm = new FsmBuilderConsumer<States, String>()
                .addState(States.A, consumerPrint).goTo(States.B, "b").goTo(States.C, "c")
                .addState(States.B, consumerPrint).goTo(States.B, "b").goTo(States.C, "c")
                .build();

    }

    @Test
    public void testCreation() {
        standardFsm(consumerPrint);
    }

    @Test
    public void testTransitions() {
        var fsm = standardFsm(consumerPrint).newFsmConsumer(States.A);

        Assert.assertEquals(fsm.onEvent("b"), States.B);
        Assert.assertEquals(fsm.getCurrentState(), States.B);
        Assert.assertEquals(fsm.onEvent("c"), States.C);
        Assert.assertEquals(fsm.getCurrentState(), States.C);
        Assert.assertEquals(fsm.onEvent("a"), States.A);
        Assert.assertEquals(fsm.getCurrentState(), States.A);
    }

    @Test
    public void testActor() {
        var fsm = standardFsm(consumerPrint).newFsmConsumer(States.A);

        fsm.getActor();
    }

    private FsmTemplate<States, String, ? extends Consumer<FsmContext<States, String>>> standardFsm(Consumer<FsmContext<States, String>> actor) {
        return new FsmBuilderConsumer<States, String>()
                .addState(States.A, actor).goTo(States.B, "b").goTo(States.C, "c")
                .addState(States.B, actor).goTo(States.A, "a").goTo(States.C, "c")
                .addState(States.C, actor).goTo(States.A, "a").goTo(States.B, "b")
                .build();
    }

    private FsmTemplateActor<States, String, String, MessageOnlyActor<FsmContext<States, String>, String, Void>> actorFsm(MessageOnlyActor<FsmContext<States, String>, String, Void> actor) {
        return new FsmBuilderActor<States, String, String, MessageOnlyActor<FsmContext<States, String>, String, Void>>()
                .addState(States.A, actor).goTo(States.B, "b").goTo(States.C, "c")
                .addState(States.B, actor).goTo(States.A, "a").goTo(States.C, "c")
                .addState(States.C, actor).goTo(States.A, "a").goTo(States.B, "b")
                .build();
    }

    @Test
    public void testActors() throws ExecutionException, InterruptedException {
        var fsm = actorFsm(actorPrint).newFsmActor(States.A);

        Assert.assertEquals("RET: b", fsm.onEvent("b", true).get());
        Assert.assertEquals(fsm.getCurrentState(), States.B);
        Assert.assertEquals("RET: c", fsm.onEvent("c", true).get());
        Assert.assertEquals(fsm.getCurrentState(), States.C);
        Assert.assertEquals("RET: a", fsm.onEvent("a", true).get());
        Assert.assertEquals(fsm.getCurrentState(), States.A);
    }
}
