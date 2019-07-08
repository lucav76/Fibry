package eu.lucaventuri.examples;

import eu.lucaventuri.fibry.Actor;
import eu.lucaventuri.fibry.ActorSystem;
import eu.lucaventuri.fibry.Stereotypes;

import static eu.lucaventuri.fibry.CreationStrategy.FIBER;
import static eu.lucaventuri.fibry.CreationStrategy.THREAD;

public class MessageExchangeExample {
    public static void main(String[] args) {
        int msToTest = 3_000;
        Actor<Integer, Integer, Void> studentThread = ActorSystem.anonymous().strategy(THREAD).newActorWithReturn(i -> i * i);
        Actor<Integer, Integer, Void> studentFiber = ActorSystem.anonymous().strategy(FIBER).newActorWithReturn(i -> i * i);

        System.out.println("1+1 thread - test of " + msToTest + " ms");

        Stereotypes.threads().runOnce(() -> {
            testStudent(msToTest, studentThread, false);
        }).waitForExit();

        System.out.println("1 thread + 1 fiber - test of " + msToTest + " ms");

        Stereotypes.threads().runOnce(() -> {
            testStudent(msToTest, studentFiber, false);
        }).waitForExit();

        System.out.println("1 fiber + 1 thread - test of " + msToTest + " ms");

        Stereotypes.fibers().runOnce(() -> {
            testStudent(msToTest, studentThread, false);
        }).waitForExit();

        System.out.println("1+1 fibers - test of " + msToTest + " ms");

        Stereotypes.fibers().runOnce(() -> {
            testStudent(msToTest, studentFiber, false);
        }).waitForExit();

        studentThread.askExit();
        studentFiber.askExit();
    }

    private static void testStudent(int msToTest, Actor<Integer, Integer, Void> student, boolean measureThroughput) {
        long start = System.currentTimeMillis();
        int batchSize = 10_000;
        long numMessages = 0;

        while (System.currentTimeMillis() < start + msToTest) {
            for (int i = 0; i < batchSize; i++)
                student.sendMessageReturnWait(i, -1);
            numMessages += batchSize;
        }

        long ms = System.currentTimeMillis() - start;
        System.out.println(numMessages + " sync messages exchanged in " + ms + " ms: " + (1000 * numMessages / ms) + " messages per second");

        if (measureThroughput) {
            start = System.currentTimeMillis();
            numMessages = 0;

            while (System.currentTimeMillis() < start + msToTest) {
                // Async messages that the actor will process in order
                for (int i = 0; i < batchSize; i++)
                    student.sendMessage(i);

                // Wait that all the messages
                //int n = student.sendMessageReturnWait(0, -1);
                //assert n == 0;
                numMessages += batchSize;
                //System.out.println(numMessages + " - Queue: " + student.getQueueLength());
            }

            long processed = numMessages - student.getQueueLength();
            ms = System.currentTimeMillis() - start;
            System.out.println("Throughput - " + numMessages + "  messages sent in " + ms + " ms (processed: " + processed + " ): " + (1000 * numMessages / ms) + " messages per second");
        }
    }
}
