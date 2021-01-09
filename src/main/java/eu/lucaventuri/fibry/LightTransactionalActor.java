package eu.lucaventuri.fibry;

import eu.lucaventuri.common.Exitable;
import eu.lucaventuri.common.Stateful;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Actor supporting light transactions; what it means is that multiple messages send with sendMessageTransaction() and
 * sendMessageTransactionReturn() are guaranteed to be executed in sequence, without any other message between them.
 * therefore, if the state was consistent before, and the sequence of messages keep the state consistent at the end
 * (though not necessarily between the message), the state is guarantee to always be seen consistent by otehr messages
 * (because they will be delivered of before the block or after it).
 *
 * This implementation does not support rollback or commit, nor a variable amount of message sent with different method class:
 * <b>all the messages need to be sent at the same time</b>
 * */
public class LightTransactionalActor<T, S> extends Exitable implements PartialActor<T, S> {
    private final Actor<List<T>, Void, S> listActor;
    public LightTransactionalActor(Actor<List<T>, Void, S> listActor) {
        super(listActor); // Link the Exitable behavior of this actor to the one of the listActor

        this.listActor = listActor;
    }
    @Override
    public PartialActor<T, S> sendMessage(T message) {
        this.listActor.sendMessage(List.of(message));

        return this;
    }

    /** Converted to light transaction */
    public PartialActor<T, S> sendMessages(T... messages) {
        sendMessageTransactionReturn(messages);

        return this;
    }

    public PartialActor<T, S> sendMessageTransaction(T... messages) {
        this.listActor.sendMessage(Arrays.asList(messages));

        return this;
    }

    public CompletableFuture<Void> sendMessageReturn(T message) {
        return this.listActor.sendMessageReturn(List.of(message));
    }

    public  CompletableFuture<Void> sendMessageTransactionReturn(T... messages) {
        return this.listActor.sendMessageReturn(Arrays.asList(messages));
    }

    @Override
    public void execAsync(Consumer<PartialActor<T, S>> worker) {
        throw new UnsupportedOperationException("Transactional actors to not support this operation");
    }


    @Override
    public void execAsync(Runnable worker) {
        this.listActor.execAsync(worker);
    }

    public void execAsyncStateful(Consumer<Stateful<S>> worker) {
        this.listActor.execAsyncStateful(worker);
    }

    public void execAsyncState(Consumer<S> worker) {
        this.listActor.execAsyncState(worker);
    }

    public void execAndWaitState(Consumer<S> worker) {
        this.listActor.execAndWaitState(worker);
    }

    @Override
    public SinkActorSingleTask<S> closeOnExit(AutoCloseable... closeables) {
        return this.listActor.closeOnExit(closeables);
    }

    @Override
    public void accept(T message) {
        sendMessage(message);
    }

    @Override
    public S getState() {
        return null;
    }

    @Override
    public void setState(S state) {

    }
}
