package eu.lucaventuri.fibry;

import org.reactivestreams.tck.TestEnvironment;
import org.reactivestreams.tck.flow.FlowSubscriberWhiteboxVerification;
import org.testng.annotations.Test;

import java.util.concurrent.Flow;


@Test
public class TestReactiveSubscribersWhiteBox extends FlowSubscriberWhiteboxVerification<Integer> {
    public TestReactiveSubscribersWhiteBox() {
        super(new TestEnvironment());
    }

    @Override
    public Flow.Subscriber<Integer> createFlowSubscriber(final WhiteboxSubscriberProbe<Integer> probe) {
        var sub = ActorSystem.anonymous().newActor((Integer n) -> {
        }).asReactiveSubscriber(100);

        return new Flow.Subscriber<Integer>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                sub.onSubscribe(subscription);

                // register a successful Subscription, and create a Puppet,
                // for the WhiteboxVerification to be able to drive its tests:
                probe.registerOnSubscribe(new SubscriberPuppet() {

                    @Override
                    public void triggerRequest(long elements) {
                        subscription.request(elements);
                    }

                    @Override
                    public void signalCancel() {
                        subscription.cancel();
                    }
                });
            }

            @Override
            public void onNext(Integer item) {
                sub.onNext(item);
                probe.registerOnNext(item);
            }

            @Override
            public void onError(Throwable throwable) {
                sub.onError(throwable);
                probe.registerOnError(throwable);
            }

            @Override
            public void onComplete() {
                sub.onComplete();
                probe.registerOnComplete();
            }
        };
    }

    @Override
    public Integer createElement(int element) {
        return element;
    }
}
