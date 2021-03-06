package eu.lucaventuri.fibry.distributed;

import eu.lucaventuri.fibry.CreationStrategy;
import eu.lucaventuri.fibry.CustomActorWithResult;
import eu.lucaventuri.fibry.FibryQueue;

import java.util.concurrent.CompletableFuture;

class TcpActorSender<R> extends CustomActorWithResult<MessageHolder<R>, CompletableFuture<R>, Void> {
    private final TcpChannel.ChannelProvider<R> provider;
    private final MessageRegistry<R> msgReg;

    protected TcpActorSender(TcpChannel.ChannelProvider<R> provider, MessageRegistry<R> msgReg) {
        super(new FibryQueue<>(), null, null, Integer.MAX_VALUE);

        this.provider = provider;
        this.msgReg = msgReg;
        closeStrategy = CloseStrategy.SEND_POISON_PILL;
        CreationStrategy.AUTO.start(this);
    }

    @Override
    protected CompletableFuture<R> onMessage(MessageHolder<R> message) {
        return provider.useChannelRetries(ch -> {
            if (ch != null)
                return message.writeMessage(ch, msgReg);
            return null;
        });
    }
}
