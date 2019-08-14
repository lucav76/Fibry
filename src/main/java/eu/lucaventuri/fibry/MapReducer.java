package eu.lucaventuri.fibry;

import eu.lucaventuri.common.Exitable;

import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * Map Reducer.
 */
public class MapReducer<I, O> {
    private final Consumer<I> mapperConsumer;
    private final Exitable exitableMapper;
    private final Actor<?, ?, O> reducer;

    public MapReducer(Actor<I, ?, ?> mapper, Actor<?, ?, O> reducer) {
        this.mapperConsumer = mapper;
        this.exitableMapper = mapper;
        this.reducer = reducer;
    }

    public MapReducer(Spawner<I, ?, Object> mapSpawner, Actor<?, ?, O> reducer) {
        this.mapperConsumer = input -> mapSpawner.spawn().sendMessage(input).sendPoisonPill();
        this.exitableMapper = mapSpawner;
        this.reducer = reducer;
    }


    /**
     * Used to send the data to the actors pool
     *
     * @param input data to process
     */
    public MapReducer<I, O> map(I input) {
        mapperConsumer.accept(input);

        return this;
    }

    /**
     * Used to send the data to the actors pool
     *
     * @param input data to process
     */
    public MapReducer<I, O> map(I... input) {
        for (int i = 0; i < input.length; i++)
            map(input[i]);

        return this;
    }


    /**
     * Used to send the data to the actors pool
     *
     * @param input data to process
     */
    public MapReducer<I, O>  map(Iterable<I> input) {
        for (I data : input)
            mapperConsumer.accept(data);

        return this;
    }


    /**
     * Signal that all the data has been sent. No more data should be sent to the actors, as the behavior is undefined.
     */
    public MapReducer<I, O>  completed() {
        if (!exitableMapper.isExiting())
            exitableMapper.sendPoisonPill();

        return this;
    }

    /**
     * Returns the result. This is a blocking operation.
     *
     * @param forceComplete True to force complete. Normally you call complete() when ready
     * @return the result of the computation.
     * @throws InterruptedException
     * @throws ExecutionException
     */
    public O get(boolean forceComplete) {
        if (forceComplete)
            completed();

        System.out.println("Waiting for exitableMapper");
        exitableMapper.waitForExit();
        System.out.println("Sending poison pill");
        reducer.sendPoisonPill();
        System.out.println("Waiting for reducer");
        reducer.waitForExit();
        System.out.println("Done");

        return reducer.getState();
    }
}
