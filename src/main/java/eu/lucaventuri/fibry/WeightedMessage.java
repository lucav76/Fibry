package eu.lucaventuri.fibry;

/** Message carrying a weight; normal messages have weight 1 */
public class WeightedMessage<T> {
    public final T message;
    public final int weight;

    public WeightedMessage(T message, int weight) {
        this.message = message;
        this.weight = weight;
    }

    public WeightedMessage(T message) {
        this(message, 1);
    }
}
