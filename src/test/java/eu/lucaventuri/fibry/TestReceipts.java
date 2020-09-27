package eu.lucaventuri.fibry;

import eu.lucaventuri.common.Exceptions;
import eu.lucaventuri.fibry.receipts.ImmutableReceipt;
import eu.lucaventuri.fibry.receipts.ReceiptFactory;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;

public class TestReceipts {
    final ReceiptFactory factory = ReceiptFactory.fromMap();

    @Test
    public void testAutoComplete() throws ExecutionException, InterruptedException, IOException {
        final Actor<String, String, Void> actor = ActorSystem.anonymous().newActorWithReturn((Function<String, String>) String::toUpperCase);
        assertEquals("ABC", actor.sendMessageReturn("abc").get());

        var rec = actor.sendMessageExternalReceipt(factory, "bcd");

        System.out.println(rec.getProgressCorrected().getProgressPercent());
        rec.get();
        System.out.println(rec.getProgressCorrected().getProgressPercent());
        assertEquals(1.0f, rec.getProgressCorrected().getProgressPercent(), 0.01f);
    }

    @Test
    public void testProgress() throws ExecutionException, InterruptedException, IOException {
        var latchLogic = new CountDownLatch(1);
        var latchWaitCaller = new CountDownLatch(1);
        var message = "xyx";

        Function<ImmutableReceipt, String> logic = rec -> {
            try {
                factory.save(rec.withProgressPercent(0.5f));
            } catch (IOException e) {
                e.printStackTrace();
            }
            latchLogic.countDown();
            Exceptions.silence(() -> latchWaitCaller.await());
            return message.toUpperCase();
        };
        final Actor<ImmutableReceipt, String, Void> actor = ActorSystem.anonymous().newActorWithReturn(logic);
        var rec = actor.sendMessageInternalReceipt(factory.newReceipt());

        latchLogic.await();
        rec = factory.refresh(rec);
        System.out.println(rec.getProgressCorrected().getProgressPercent());
        assertEquals(0.5f, rec.getProgressCorrected().getProgressPercent(), 0.01f);
        latchWaitCaller.countDown();
        rec.get();
        System.out.println(rec.getProgressCorrected().getProgressPercent());
        assertEquals(1.0f, rec.getProgressCorrected().getProgressPercent(), 0.01f);
    }
}
