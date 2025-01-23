package eu.lucaventuri.fibry;

import eu.lucaventuri.common.SystemUtils;
import eu.lucaventuri.fibry.ai.AgentNode;
import eu.lucaventuri.fibry.ai.AiAgent;
import eu.lucaventuri.fibry.ai.AiAgentBuilderActor;
import eu.lucaventuri.fibry.ai.GuardLogic;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Test for an AI agent that can help you to find a plane
 */
public class TestAIAgent {
    public enum TravelState {
        SELECT_COUNTRIES,
        SELECT_CITIES,
        SEARCH_FLIGHTS,
        DONE
    }

    public record TravelInfo(List<String> countries, List<String> cities, List<String> flights) {
        static TravelInfo empty() { return new TravelInfo(null, null, null); }
    }

    public enum ShoppingState {
        COLLECT_FOOD,
        LOOK_AROUND,
        LOOK_AROUND2,
        LOOK_AROUND_OUTSIDE,
        PAY
    }

    public record ShoppingContext(int priceVeggies, int priceMeat, int totalPaid, boolean peekedAround, boolean peekedAround2, boolean peekedOutside) {}


    private AiAgent<TravelState, TravelInfo> buildAgent(boolean parallel, int parallelism, int sleep) {
        var builder = new AiAgentBuilderActor<TravelState, TravelInfo>(false);
        List<AgentNode<TravelState, TravelInfo>> nodes = List.of(
                ctx -> {
                    System.out.println("Italy: " + Thread.currentThread().threadId() + " - virtual: " + Thread.currentThread().isVirtual());
                    SystemUtils.sleep(sleep);
                    return  ctx.info.mergeList("countries", List.of("Italy")); },
                ctx -> {
                    System.out.println("Spain: " + Thread.currentThread().threadId() + " - virtual: " + Thread.currentThread().isVirtual());
                    SystemUtils.sleep(sleep);
                    return  ctx.info.mergeList("countries", List.of("Spain")); });

        if (parallel)
            builder.addStateParallel(TravelState.SELECT_COUNTRIES, TravelState.SELECT_CITIES, parallelism, nodes, null);
        else
            builder.addStateSerial(TravelState.SELECT_COUNTRIES, TravelState.SELECT_CITIES, parallelism, nodes, null);
        builder.addState(TravelState.SELECT_CITIES, TravelState.SEARCH_FLIGHTS, parallelism, ctx -> {
            System.out.println("SELECT_CITIES: " + Thread.currentThread().threadId() + " - virtual: " + Thread.currentThread().isVirtual());
            return ctx.info.replace("cities", List.of("Florence", "Barcelona"));
        }, null);
        builder.addState(TravelState.SEARCH_FLIGHTS, TravelState.DONE, parallelism, ctx -> {
            System.out.println("SEARCH_FLIGHTS: " + Thread.currentThread().threadId() + " - virtual: " + Thread.currentThread().isVirtual());
            return ctx.info.replace("flights", List.of("Ticket to Florence", "Ticket to Barcelona"));
        }, null);

        return builder.build(TravelState.SELECT_COUNTRIES, TravelState.DONE);
    }

    @Test
    public void testSerial() throws ExecutionException, InterruptedException {
        var aiAgent = buildAgent(false, 1, 50);

        var result = aiAgent.execute(TravelInfo.empty(), (state, info) -> {
            System.out.println(state + ": " + info);
        });

        System.out.println();
        System.out.println(result);
    }

    @Test
    public void testParallel() throws ExecutionException, InterruptedException {
        var aiAgent = buildAgent(true, 1, 50);

        var result = aiAgent.execute(TravelInfo.empty(), (state, info) -> {
            System.out.println(state + ": " + info);
        });

        System.out.println();
        System.out.println(result);
    }

    @Test
    public void testThreadsSerial() throws ExecutionException, InterruptedException {
        var aiAgent = buildAgent(false, 1, 150);

        System.out.println(aiAgent.execute(TravelInfo.empty(), null));
        System.out.println("\n\n\n*****\n");
        System.out.println(aiAgent.execute(TravelInfo.empty(), null));
        System.out.println("\n\n\n*****\n");
        System.out.println(aiAgent.execute(TravelInfo.empty(), null));
    }

    @Test
    public void testThreadsSerialOneThreadPerMessage() throws ExecutionException, InterruptedException {
        var aiAgent = buildAgent(false, 0, 150);

        System.out.println(aiAgent.execute(TravelInfo.empty(), null));
        System.out.println("\n\n\n*****\n");
        System.out.println(aiAgent.execute(TravelInfo.empty(), null));
        System.out.println("\n\n\n*****\n");
        System.out.println(aiAgent.execute(TravelInfo.empty(), null));
    }

    @Test
    public void testThreadsParallel() throws ExecutionException, InterruptedException {
        var aiAgent = buildAgent(true, 1, 150);

        System.out.println(aiAgent.execute(TravelInfo.empty(), null));
        System.out.println("\n\n\n*****\n");
        System.out.println(aiAgent.execute(TravelInfo.empty(), null));
        System.out.println("\n\n\n*****\n");
        System.out.println(aiAgent.execute(TravelInfo.empty(), null));
    }

    @Test
    public void testThreadsParallelOneThreadPerMessage() throws ExecutionException, InterruptedException {
        var aiAgent = buildAgent(true, 0, 150);

        System.out.println(aiAgent.execute(TravelInfo.empty(), null));
        System.out.println("\n\n\n*****\n");
        System.out.println(aiAgent.execute(TravelInfo.empty(), null));
        System.out.println("\n\n\n*****\n");
        System.out.println(aiAgent.execute(TravelInfo.empty(), null));
    }

    @Test
    public void testThreadsAsyncSerial() throws ExecutionException, InterruptedException {
        asyncExec(buildAgent(false, 1, 150));
    }

    @Test
    public void testThreadsAsyncSerial2Threads() throws ExecutionException, InterruptedException {
        asyncExec(buildAgent(false, 2, 150));
    }

    @Test
    public void testThreadsAsyncParallelOneThreadPerMessage() throws ExecutionException, InterruptedException {
        asyncExec(buildAgent(true, 0, 150));

    }

    @Test
    public void testThreadsAsyncParallel() throws ExecutionException, InterruptedException {
        asyncExec(buildAgent(true, 1, 150));
    }

    @Test
    public void testThreadsAsyncParallel2Threads() throws ExecutionException, InterruptedException {
        asyncExec(buildAgent(true, 2, 150));
    }

    @Test
    public void testThreadsAsyncSerialOneThreadPerMessage() throws ExecutionException, InterruptedException {
        asyncExec(buildAgent(false, 0, 150));

    }

    private static void asyncExec(AiAgent<TravelState, TravelInfo> aiAgent) throws InterruptedException, ExecutionException {
        CompletableFuture<AiAgent.AgentResult<TravelInfo>>[] futures = new CompletableFuture[3];
        futures[0] = aiAgent.executeAsync(TravelInfo.empty(), null);
        futures[1] = aiAgent.executeAsync(TravelInfo.empty(), null);
        futures[2] = aiAgent.executeAsync(TravelInfo.empty(), null);

        CompletableFuture.allOf(futures).get();
        System.out.println("\n\n\n*****\n");
        System.out.println(futures[0].get());
        System.out.println("\n\n\n*****\n");
        System.out.println(futures[0].get());
    }

    private AiAgent<ShoppingState, ShoppingContext> buildShoppingAgent() {
        var builder = new AiAgentBuilderActor<ShoppingState, ShoppingContext>(true);
        builder.addStateSerial(ShoppingState.COLLECT_FOOD, List.of(ShoppingState.PAY, ShoppingState.LOOK_AROUND), 1, List.of(
                ctx -> ctx.info.replace("priceVeggies", 100),
                ctx -> ctx.info.replace("priceMeat", 200) ), null);
        builder.addState(ShoppingState.PAY, ShoppingState.LOOK_AROUND_OUTSIDE, 1, ctx -> ctx.info.replace("totalPaid", ctx.info.getState().priceMeat + ctx.info.getState().priceVeggies), null);
        builder.addState(ShoppingState.LOOK_AROUND, ShoppingState.LOOK_AROUND2, 1, ctx -> {
            SystemUtils.sleep(100);
            return ctx.info.replace("peekedAround", true);
        }, null);
        builder.addState(ShoppingState.LOOK_AROUND2, ShoppingState.LOOK_AROUND_OUTSIDE, 1, ctx -> {
            SystemUtils.sleep(100);
            return ctx.info.replace("peekedAround2", true);
        }, null);
        builder.addState(ShoppingState.LOOK_AROUND_OUTSIDE, null, 1, ctx -> {
            if (ctx.info.getState().totalPaid <= 0)
                throw new IllegalStateException("Not paid yet!");
            if (!ctx.info.getState().peekedAround)
                throw new IllegalStateException("Not peeked around!");
            if (!ctx.info.getState().peekedAround2)
                throw new IllegalStateException("Not peeked around2!");
            System.out.println("The guard worked!");
            return ctx.info.replace("peekedOutside", true);
        //}, (prevState, newState, ctx) -> ctx.getState().totalPaid > 0 && ctx.getState().peekedAround && ctx.getState().peekedAround2);
        }, null /*GuardLogic.waitStates(ShoppingState.LOOK_AROUND2, ShoppingState.PAY)*/);

        return builder.build(ShoppingState.COLLECT_FOOD, null);
    }

    @Test
    public void testShopping() throws ExecutionException, InterruptedException {
        var agent = buildShoppingAgent();
        var startingState = new ShoppingContext(0,0,0, false, false, false);
        var res = agent.execute(startingState, null);
        var result = res.result();

        System.out.println("Total paid: " + result.totalPaid + " - " + result.peekedAround() + " - " + result.peekedAround2() + " - States: " + res.statesProcessed());
        Assert.assertEquals(300, result.totalPaid);
        Assert.assertTrue(result.peekedAround());
        Assert.assertTrue(result.peekedAround2());
        Assert.assertTrue(result.peekedOutside());
        Assert.assertEquals(6, res.statesProcessed());

        System.out.println(agent.sendMessageReturn(startingState).get().result());
    }
}
