package eu.lucaventuri.fibry;

import java.util.concurrent.CompletableFuture;

/** Message that is supposed to get an answer at some point */
public class MessageWithAnswer<T, R> {
    public final T message;
    public final CompletableFuture<R> answers = new CompletableFuture<>();

    public MessageWithAnswer(T message) {
        this.message = message;
    }
}
