package eu.lucaventuri.fibry.distributed;

import eu.lucaventuri.fibry.BaseActor;
import eu.lucaventuri.fibry.SinkActorSingleTask;
import eu.lucaventuri.fibry.Stereotypes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Base class simplifying the creation of a registry, for actors discovery.
 * The implementation should:
 * - implement sendActorsInfo() to broadcast the local actors to the remote machines
 * - call onActorsInfo() when new information are received from the remote machines
 */
public abstract class BaseActorRegistry implements ActorRegistry, AutoCloseable {
    private static class IdAndSupplier {
        private final String id;
        private final Supplier<String> supplier;

        IdAndSupplier(String id, Supplier<String> supplier) {
            this.id = id;
            this.supplier = supplier;
        }
    }

    private static class TimeAndInfo {
        private final long lastInfo = System.currentTimeMillis();
        private final String info;

        TimeAndInfo(String info) {
            this.info = info;
        }
    }

    public enum RegistryAction {
        REGISTER, DEREGISTER, JOINING
    }

    private final ConcurrentHashMap<BaseActor, IdAndSupplier> localRegistry = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TimeAndInfo> remoteRegistry = new ConcurrentHashMap<>();
    private final SinkActorSingleTask<Void> scheduledSendInfo;
    private final SinkActorSingleTask<Void> scheduledClean;
    private final int msGraceSendRefresh;
    private final int msGgraceCleanRemoteActors;
    private final AtomicLong lastSent = new AtomicLong();
    private final Predicate<ActorAction> validator;

    protected BaseActorRegistry(int msRefresh, int msGraceSendRefresh, int msCleanRemoteActors, int msGgraceCleanRemoteActors, Predicate<ActorAction> validator) {
        scheduledSendInfo = Stereotypes.auto().schedule(this::sendAllActorsInfo, msRefresh);
        scheduledClean = Stereotypes.auto().schedule(this::cleanRemoteActors, msCleanRemoteActors);
        this.msGraceSendRefresh = msGraceSendRefresh;
        this.msGgraceCleanRemoteActors = msGgraceCleanRemoteActors;
        this.validator = validator;
    }

    private void cleanRemoteActors() {
        var actorsToDeregister = new ArrayList<String>();
        var limit = System.currentTimeMillis() - msGgraceCleanRemoteActors;

        for (var entry : remoteRegistry.entrySet()) {
            if (entry.getValue().lastInfo < limit)
                actorsToDeregister.add(entry.getKey());
        }

        for (var id : actorsToDeregister)
            remoteRegistry.remove(id);
    }

    /** Transmit or broadcast the information related to the actors */
    protected abstract void sendActorsInfo(Collection<ActorAction> info);

    protected void onActorsInfo(Collection<ActorAction> actions) {
        for (var action : actions) {
            if (validator!=null && !validator.test(action))
                continue;

            if (action.action == RegistryAction.JOINING)
                sendAllActorsInfo();
            else if (action.action == RegistryAction.REGISTER)
                remoteRegistry.put(action.id, new TimeAndInfo(action.info));
            else if (action.action == RegistryAction.DEREGISTER)
                remoteRegistry.remove(action.id);
        }
    }

    private void sendAllActorsInfo() {
        if (System.currentTimeMillis() < lastSent.get() + msGraceSendRefresh)
            return;

        var info = localRegistry.values().stream().map(ids -> new ActorAction(RegistryAction.REGISTER, ids.id, ids.supplier.get())).collect(Collectors.toList());

        sendActorsInfo(info);

        lastSent.set(System.currentTimeMillis());
    }

    @Override
    public <T, R, S> void registerActor(BaseActor<T, R, S> actor, Supplier<String> infoSupplier) {
        String id = UUID.randomUUID().toString();
        localRegistry.put(actor, new IdAndSupplier(id, infoSupplier));
        actor.closeOnExit(() -> deregisterActor(actor));
        sendActorsInfo(List.of(new ActorAction(RegistryAction.REGISTER, id, infoSupplier.get())));
    }

    @Override
    public <T, R, S> void registerActor(ActorAndSupplier... actors) {
        var list = new ArrayList<ActorAction>();

        for (var actor : actors) {
            String id = UUID.randomUUID().toString();
            localRegistry.put(actor.actor, new IdAndSupplier(id, actor.infoSupplier));
            actor.actor.closeOnExit(() -> deregisterActor(actor.actor));
            list.add(new ActorAction(RegistryAction.REGISTER, id, actor.infoSupplier.get()));
        }

        sendActorsInfo(list);
    }

    @Override
    public <T, R, S> boolean deregisterActor(BaseActor<T, R, S> actor) {
        var prev = localRegistry.remove(actor);

        if (prev != null)
            sendActorsInfo(List.of(new ActorAction(RegistryAction.DEREGISTER, prev.id, "")));

        return prev != null;
    }

    @Override
    public void close() throws Exception {
        scheduledClean.askExit();
        scheduledSendInfo.askExit();
    }

    @Override
    public void visitRemoteActors(BiConsumer<String, String> worker) {
        for (var entry : remoteRegistry.entrySet()) {
            worker.accept(entry.getKey(), entry.getValue().info);
        }
    }

    @Override
    public int getHowManyRemoteActors() {
        return remoteRegistry.size();
    }
}
