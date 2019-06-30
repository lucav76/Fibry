package eu.lucaventuri.functional;

import eu.lucaventuri.common.ConsumerEx;
import eu.lucaventuri.common.Exceptions;

import java.util.Optional;

/** Functional Either, containing or one type or another */
public class Either3<L, R, O> {
    private final L left;
    private final R right;
    private final O other;

    private Either3(L left, R right, O other) {
        this.left = left;
        this.right = right;
        this.other = other;

        assert (left != null && right == null && other == null) ||
                (left == null && right != null && other == null) ||
                (left == null && right == null && other != null);
    }

    public static <L, R, O> Either3<L, R, O> left(L value) {
        Exceptions.assertAndThrow(value != null, "Left value is null!");

        return new Either3<L, R, O>(value, null, null);
    }

    public static <L, R, O> Either3<L, R, O> right(R value) {
        Exceptions.assertAndThrow(value != null, "Right value is null!");

        return new Either3<L, R, O>(null, value, null);
    }

    public static <L, R, O> Either3<L, R, O> other(O value) {
        Exceptions.assertAndThrow(value != null, "Other value is null!");

        return new Either3<L, R, O>(null, null, value);
    }

    public boolean isLeft() {
        return left != null;
    }

    public boolean isRight() {
        return right != null;
    }

    public boolean isOther() {
        return other != null;
    }

    public L left() {
        return left;
    }

    public R right() {
        return right;
    }

    public O other() {
        return other;
    }

    public Optional<L> leftOpt() {
        return Optional.ofNullable(left);
    }

    public Optional<R> rightOpt() {
        return Optional.ofNullable(right);
    }

    public Optional<O> otherOpt() {
        return Optional.ofNullable(other);
    }

    public <E extends Throwable> void ifLeft(ConsumerEx<L, E> consumer) throws E {
        if (left != null)
            consumer.accept(left);
    }

    public <E extends Throwable> void ifRight(ConsumerEx<R, E> consumer) throws E {
        if (right != null)
            consumer.accept(right);
    }

    public <E extends Throwable> void ifOther(ConsumerEx<O, E> consumer) throws E {
        if (right != null)
            consumer.accept(other);
    }

    public <E extends Throwable> void ifEither(ConsumerEx<L, E> consumerLeft, ConsumerEx<R, E> consumerRight, ConsumerEx<O, E> consumerOther) throws E {
        if (left != null)
            consumerLeft.accept(left);
        else if (right!=null)
            consumerRight.accept(right);
        else
            consumerOther.accept(other);
    }
}
