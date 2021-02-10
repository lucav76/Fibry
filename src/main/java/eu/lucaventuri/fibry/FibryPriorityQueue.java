package eu.lucaventuri.fibry;

import java.util.function.Consumer;

import eu.lucaventuri.collections.PriorityMiniQueue;
import eu.lucaventuri.functional.Either3;

public class FibryPriorityQueue<T, R, S> extends PriorityMiniQueue<Either3<Consumer<S>, T, MessageWithAnswer<T, R>>> implements MiniFibryQueue<T,R,S> {
}