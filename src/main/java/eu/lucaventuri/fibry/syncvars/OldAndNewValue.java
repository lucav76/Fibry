package eu.lucaventuri.fibry.syncvars;

import eu.lucaventuri.fibry.distributed.JacksonSerDeser;

import java.util.function.Consumer;

/** Class holding the old and the new value of a variable; the attributes are not final to allow Jackson to serialize
 * the class without additional code */
public class OldAndNewValue<T> {
    private T oldValue;
    private T newValue;

    public interface OldAndNewValueConsumer<T> {
        void accept(T oldValue, T newValue);

        default Consumer<OldAndNewValue<T>> asConsumer() {
            return v -> accept(v.oldValue, v.newValue);
        }
    }

    public OldAndNewValue(T oldValue, T newValue) {
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public OldAndNewValue() {
    }

    public T getOldValue() {
        return oldValue;
    }

    public T getNewValue() {
        return newValue;
    }

    public static <T> Class<OldAndNewValue<T>> clazz() {
        return (Class<OldAndNewValue<T>>) (Object) OldAndNewValue.class;
    }

    public static <T> JacksonSerDeser<OldAndNewValue<T>, OldAndNewValue<T>> getJacksonSerDeser() {
        return new JacksonSerDeser<>(OldAndNewValue.clazz());
    }
}
