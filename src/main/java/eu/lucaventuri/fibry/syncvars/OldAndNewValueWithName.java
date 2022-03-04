package eu.lucaventuri.fibry.syncvars;

import eu.lucaventuri.fibry.distributed.JacksonSerDeser;

import java.util.function.Consumer;

/** Class holding the old and the new value of a variable; the attributes are not final to allow Jackson to serialize
 * the class without additional code */
public class OldAndNewValueWithName<T> {
    private String name;
    private T oldValue;
    private T newValue;


    public interface OldAndNewValueWithNameConsumer<T> {
        void accept(String name, T oldValue, T newValue);

        default Consumer<OldAndNewValueWithName<T>> asConsumer() {
            return v -> accept(v.name, v.oldValue, v.newValue);
        }
    }

    public OldAndNewValueWithName(String name, T oldValue, T newValue) {
        this.name = name;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public OldAndNewValueWithName() {
    }

    public T getOldValue() {
        return oldValue;
    }

    public T getNewValue() {
        return newValue;
    }

    public String getName() {
        return name;
    }

    public static <T> Class<OldAndNewValueWithName<T>> clazz() {
        return (Class<OldAndNewValueWithName<T>>) (Object) OldAndNewValueWithName.class;
    }

    public static <T> JacksonSerDeser<OldAndNewValueWithName<T>, OldAndNewValueWithName<T>> getJacksonSerDeser() {
        return new JacksonSerDeser<>(OldAndNewValueWithName.clazz());
    }
}
