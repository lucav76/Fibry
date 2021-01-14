package eu.lucaventuri.fibry;

import eu.lucaventuri.common.SystemUtils;
import eu.lucaventuri.fibry.distributed.ActorRegistry;
import junit.framework.TestCase;

import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.CountDownLatch;

public class TestActorRegistry extends TestCase {
    public void testRegistryAndDeregister() throws IOException, InterruptedException {
        CountDownLatch latchRegister = new CountDownLatch(1);
        CountDownLatch latchDeregister = new CountDownLatch(1);
        InetAddress address = InetAddress.getByName("224.0.0.0");
        int multicastPort = 10001;
        var actor = ActorSystem.anonymous().newActor(obj -> { });
        var reg1 = ActorRegistry.usingMulticast(address, multicastPort, 5000, 0, 5000, 0, null);

        new Thread(() -> {
            try {
                var reg2 = ActorRegistry.usingMulticast(address, multicastPort, 5000, 0, 5000, 0, null);

                while (reg2.getHowManyRemoteActors() == 0)
                    SystemUtils.sleep(1);

                reg2.visitRemoteActors((id, info) -> {
                    if (info.equals("ABC")) {
                        System.out.println("Received " + info + " from " + id);
                        latchRegister.countDown();

                        assertEquals(reg2.getHowManyRemoteActors(), 1);
                        new Thread(() -> {
                            reg1.deregisterActor(actor);

                            while (reg2.getHowManyRemoteActors() > 0)
                                SystemUtils.sleep(1);

                            System.out.println("Actor deregisterd - num actors in registry: " + reg2.getHowManyRemoteActors());
                            latchDeregister.countDown();
                        }).start();
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        reg1.registerActor(actor, "ABC");

        latchRegister.await();
        latchDeregister.await();
    }
}
