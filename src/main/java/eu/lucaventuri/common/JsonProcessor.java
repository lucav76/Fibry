package eu.lucaventuri.common;

public interface JsonProcessor {
    public String traverseAsString(String json, Object... paths);
    public String toJson(Object value);
}
