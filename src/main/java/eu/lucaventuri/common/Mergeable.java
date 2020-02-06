package eu.lucaventuri.common;

/** Object that can be merged with another one of th esame type */
public interface Mergeable {
    String getKey();

    Mergeable mergeWith(Mergeable m);
}
