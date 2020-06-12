package eu.lucaventuri.common;

/**
 * Object that can be merged with another one of th esame type
 */
public interface MergeableParallelBatches extends Mergeable {
    int elementsMerged();
}
