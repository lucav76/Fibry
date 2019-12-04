package eu.lucaventuri.examples;

import eu.lucaventuri.fibry.ActorSystem;
import eu.lucaventuri.fibry.distributed.HttpChannel;
import eu.lucaventuri.fibry.distributed.JacksonSerDeser;
import eu.lucaventuri.fibry.distributed.StringSerDeser;

public class RemoteActors {
    public static void main(String[] args) throws Exception {
        var channel = new HttpChannel("Http://localhost:8093/noAuth/actors", HttpChannel.HttpMethod.GET, null, null, false);
        var stringSerDeser = StringSerDeser.INSTANCE;

        try (
                var act1 = ActorSystem.anonymous().newRemoteActorWithReturn("act1", channel, stringSerDeser);
                var act2 = ActorSystem.anonymous().<TestMessage, String>newRemoteActorWithReturn("act2", channel, new JacksonSerDeser<>(String.class), stringSerDeser);
        ) {

            System.out.println(act1.sendMessageReturn("Message 1").get());
            System.out.println(act1.sendMessageReturn("Message 1.1").get());
            System.out.println(act2.sendMessageReturn(new TestMessage("abc", 123)).get());
            System.out.println(act2.sendMessageReturn(new TestMessage("abcde", 12345)).get());
        }

        //act1.sendPoisonPill();
        //act2.sendPoisonPill();
    }
}
