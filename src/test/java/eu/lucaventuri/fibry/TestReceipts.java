package eu.lucaventuri.fibry;

import eu.lucaventuri.common.Exceptions;
import eu.lucaventuri.fibry.receipts.Receipt;
import eu.lucaventuri.fibry.receipts.ReceiptFactory;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;

public class TestReceipts {
    final ReceiptFactory factory = ReceiptFactory.fromMap();

    @Test
    public void testAutoComplete() throws ExecutionException, InterruptedException {
        final Actor<String, String, Void> actor = ActorSystem.anonymous().newActorWithReturn((Function<String, String>) String::toUpperCase);
        assertEquals("ABC", actor.sendMessageReturn("abc").get());

        var rec = actor.sendMessageExternalReceipt(factory, "bcd");

        System.out.println(rec.getProgressPercent());
        rec.get();
        System.out.println(rec.getProgressPercent());
        assertEquals(1.0f, rec.getProgressPercent(), 0.01f);
    }

    @Test
    public void testProgress() throws ExecutionException, InterruptedException {
        var latchLogic = new CountDownLatch(1);
        var latchWaitCaller = new CountDownLatch(1);
        Function<Receipt<String, String>, String> logic = rec -> {
            rec.setProgress(0.5f);
            latchLogic.countDown();
            Exceptions.silence(() -> latchWaitCaller.await());
            return rec.getMessage().toUpperCase();
        };
        final Actor<Receipt<String, String>, String, Void> actor = ActorSystem.anonymous().newActorWithReturn(logic);
        var rec = actor.sendMessageInternalReceipt(factory.newReceipt("xyz"));

        latchLogic.await();
        System.out.println(rec.getProgressPercent());
        assertEquals(0.5f, rec.getProgressPercent(), 0.01f);
        latchWaitCaller.countDown();
        rec.get();
        System.out.println(rec.getProgressPercent());
        assertEquals(1.0f, rec.getProgressPercent(), 0.01f);
    }
}
