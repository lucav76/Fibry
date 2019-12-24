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
    private final Consumer<FsmContext<States, String, String>> consumerPrint = m -> System.out.println("From " + m.previousState + " to " + m.newState + " - message: " + m.message);
    private final MessageOnlyActor<FsmContext<States, String, String>, String, Void> actorPrint = ActorSystem.anonymous().newActorWithReturn(m -> {
        System.out.println("From " + m.previousState + " to " + m.newState + " - message: " + m.message);

        return "RET: " + m.message;
    });

    @Test(expected = IllegalArgumentException.class)
    public void testNotEnoughState() {
        var fsm = new FsmBuilderConsumer<States, String, String>()
                .addState(States.A, consumerPrint).goTo(States.B, "b").goTo(States.C, "c")
                .build();

    }

    @Test(expected = IllegalArgumentException.class)
    public void testUnknownState() {
        var fsm = new FsmBuilderConsumer<States, String, String>()
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

        Assert.assertEquals(fsm.onEvent("b", "Test"), States.B);
        Assert.assertEquals(fsm.getCurrentState(), States.B);
        Assert.assertEquals(fsm.onEvent("c", "Test"), States.C);
        Assert.assertEquals(fsm.getCurrentState(), States.C);
        Assert.assertEquals(fsm.onEvent("a", "Test"), States.A);
        Assert.assertEquals(fsm.getCurrentState(), States.A);
    }

    @Test
    public void testActor() {
        var fsm = standardFsm(consumerPrint).newFsmConsumer(States.A);

        fsm.getActor();
    }

    private FsmTemplate<States, String, ? extends Consumer<FsmContext<States, String, String>>, String> standardFsm(Consumer<FsmContext<States, String, String>> actor) {
        return new FsmBuilderConsumer<States, String, String>()
                .addState(States.A, actor).goTo(States.B, "b").goTo(States.C, "c")
                .addState(States.B, actor).goTo(States.A, "a").goTo(States.C, "c")
                .addState(States.C, actor).goTo(States.A, "a").goTo(States.B, "b")
                .build();
    }

    private FsmTemplateActor<States, String, String, MessageOnlyActor<FsmContext<States, String, String>, String, Void>, String> actorFsm(MessageOnlyActor<FsmContext<States, String, String>, String, Void> actor) {
        return new FsmBuilderActor<States, String, String, MessageOnlyActor<FsmContext<States, String, String>, String, Void>, String>()
                .addState(States.A, actor).goTo(States.B, "b").goTo(States.C, "c")
                .addState(States.B, actor).goTo(States.A, "a").goTo(States.C, "c")
                .addState(States.C, actor).goTo(States.A, "a").goTo(States.B, "b")
                .build();
    }

    @Test
    public void testActorsAfter() throws ExecutionException, InterruptedException {
        var fsmTemplate = actorFsm(actorPrint);
        var fsm = fsmTemplate.newFsmActor(States.A);
        var fsm2 = fsmTemplate.newFsmActorReplace(States.A, null);

        Assert.assertEquals("RET: b", fsm.onEventAfter("b", "Test", true).get());
        Assert.assertEquals(fsm.getCurrentState(), States.B);
        Assert.assertEquals("RET: c", fsm.onEventAfter("c", "Test", true).get());
        Assert.assertEquals(fsm.getCurrentState(), States.C);
        Assert.assertEquals("RET: a", fsm.onEventAfter("a", "Test", true).get());
        Assert.assertEquals(fsm.getCurrentState(), States.A);

        Assert.assertNull(fsm2.onEventAfter("b", "Test", true));
        Assert.assertEquals(fsm2.getCurrentState(), States.B);
        Assert.assertNull(fsm2.onEventAfter("c", "Test", true));
        Assert.assertEquals(fsm2.getCurrentState(), States.C);
        Assert.assertNull(fsm2.onEventAfter("a", "Test", true));
        Assert.assertEquals(fsm2.getCurrentState(), States.A);
    }

    @Test
    public void testActorsBefore() throws ExecutionException, InterruptedException {
        var fsmTemplate = actorFsm(actorPrint);
        var fsm = fsmTemplate.newFsmActor(States.A);
        var fsm2 = fsmTemplate.newFsmActorReplace(States.A, null);

        Assert.assertEquals("RET: b", fsm.onEventBefore("b", "Test", true));
        Assert.assertEquals(fsm.getCurrentState(), States.B);
        Assert.assertEquals("RET: c", fsm.onEventBefore("c", "Test", true));
        Assert.assertEquals(fsm.getCurrentState(), States.C);
        Assert.assertEquals("RET: a", fsm.onEventBefore("a", "Test", true));
        Assert.assertEquals(fsm.getCurrentState(), States.A);

        Assert.assertNull(fsm2.onEventBefore("b", "Test", true));
        Assert.assertEquals(fsm2.getCurrentState(), States.B);
        Assert.assertNull(fsm2.onEventBefore("c", "Test", true));
        Assert.assertEquals(fsm2.getCurrentState(), States.C);
        Assert.assertNull(fsm2.onEventBefore("a", "Test", true));
        Assert.assertEquals(fsm2.getCurrentState(), States.A);
    }
}
