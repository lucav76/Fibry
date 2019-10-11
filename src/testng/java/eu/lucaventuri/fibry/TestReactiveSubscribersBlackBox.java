package eu.lucaventuri.fibry;

import org.reactivestreams.tck.flow.FlowSubscriberBlackboxVerification;
import org.reactivestreams.tck.TestEnvironment;

import java.util.concurrent.Flow;


public class TestReactiveSubscribersBlackBox extends FlowSubscriberBlackboxVerification<Integer> {
    public TestReactiveSubscribersBlackBox() {
        super(new TestEnvironment());
    }

    @Override
    public Flow.Subscriber<Integer> createFlowSubscriber() {
        return ActorSystem.anonymous().newActor((Integer n) -> {
        }).asReactiveSubscriber(100);
    }

    @Override
    public Integer createElement(int element) {
        return element;
    }
}
