package eu.lucaventuri.fibry.distributed;

import eu.lucaventuri.common.FunctionEx;
import eu.lucaventuri.common.SystemUtils;
import eu.lucaventuri.fibry.ActorSystem;
import eu.lucaventuri.fibry.CreationStrategy;
import eu.lucaventuri.fibry.CustomActorWithResult;
import eu.lucaventuri.fibry.FibryQueue;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

class TcpActorSender<R> extends CustomActorWithResult<MessageHolder<R>, CompletableFuture<R>, Void> {
    private final ChannelProvider<R> provider;
    private final MessageRegistry<R> msgReg;
    private final static int[] retries = {0, 100, 500, 1000, 5000, 30000};
    private final static boolean keepReconnecting = true;

    interface ChannelProvider<R> {
        CompletableFuture<R> useChannelRetries(FunctionEx<SocketChannel, CompletableFuture<R>, IOException> worker);
    }

    protected TcpActorSender(ChannelProvider<R> provider, MessageRegistry<R> msgReg) {
        super(new FibryQueue<>(), null, null, Integer.MAX_VALUE);

        this.provider = provider;
        this.msgReg = msgReg;
        closeStrategy = CloseStrategy.SEND_POISON_PILL;
        CreationStrategy.AUTO.start(this);
    }

    @Override
    protected CompletableFuture<R> onMessage(MessageHolder<R> message) {
        for (int i = 0; keepReconnecting || i < TcpActorSender.retries.length; i++) {
            AtomicBoolean processed = new AtomicBoolean();

            var ret = provider.useChannelRetries(ch -> {
                if (ch != null) {
                    processed.set(true);
                    return message.writeMessage(ch, msgReg);
                }

                return null;
            });

            if (processed.get())
                return ret;

            System.out.println("ret null");

            System.out.println("Retrying A - " + i + " - " + message.toString());
            // Spread reconnections from multiple actors, in case of network issue
            int retryTime = i < TcpActorSender.retries.length ? TcpActorSender.retries[i] : TcpActorSender.retries[TcpActorSender.retries.length - 1];

            SystemUtils.sleep((int) (retryTime / 2 + Math.random() * retryTime / 2));
        }

        return null;
    }

    public void registerAsNamedActor(String name) {
        ActorSystem.registerActorQueue(name, (FibryQueue) queue);
    }
}
