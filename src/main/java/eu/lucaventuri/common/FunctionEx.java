package eu.lucaventuri.common;

import java.util.Objects;

/** Funcntion that can throw an exception */
@FunctionalInterface
public interface FunctionEx<T, R, E extends Throwable> {
    R apply(T t) throws E;

    default <V> FunctionEx<V, R, E> compose(java.util.function.Function<? super V, ? extends T> before) throws E {
        Objects.requireNonNull(before);

        return (V v) -> apply(before.apply(v));
    }

    default <V> FunctionEx<T, V, E> andThen(java.util.function.Function<? super R, ? extends V> after) {
        Objects.requireNonNull(after);
        return (T t) -> after.apply(apply(t));
    }

    static <T, E extends Throwable> FunctionEx<T, T, E> identity() {
        return t -> t;
    }
}
