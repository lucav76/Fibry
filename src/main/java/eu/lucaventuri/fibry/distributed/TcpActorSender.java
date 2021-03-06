package eu.lucaventuri.fibry.distributed;

import eu.lucaventuri.common.SystemUtils;
import eu.lucaventuri.fibry.ActorSystem;
import eu.lucaventuri.fibry.CreationStrategy;
import eu.lucaventuri.fibry.CustomActorWithResult;
import eu.lucaventuri.fibry.FibryQueue;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

class TcpActorSender<R> extends CustomActorWithResult<MessageHolder<R>, CompletableFuture<R>, Void> {
    private final TcpChannel.ChannelProvider<R> provider;
    private final MessageRegistry<R> msgReg;
    final static int[] retries = {0, 100, 500, 1000, 5000, 30000};
    private final static boolean keepReconnecting = true;

    protected TcpActorSender(TcpChannel.ChannelProvider<R> provider, MessageRegistry<R> msgReg) {
        super(new FibryQueue<>(), null, null, Integer.MAX_VALUE);

        this.provider = provider;
        this.msgReg = msgReg;
        closeStrategy = CloseStrategy.SEND_POISON_PILL;
        CreationStrategy.AUTO.start(this);
    }

    @Override
    protected CompletableFuture<R> onMessage(MessageHolder<R> message) {
        for (int i = 0; keepReconnecting || i < TcpActorSender.retries.length; i++) {
            var ret = provider.useChannelRetries(ch -> {
                if (ch != null)
                    return message.writeMessage(ch, msgReg);
                return null;
            });

            if (ret!=null)
                return ret;

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
