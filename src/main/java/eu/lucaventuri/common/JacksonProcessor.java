package eu.lucaventuri.common;

public class JacksonProcessor implements JsonProcessor {
    private static Object objectMapper;
    private static Class<?> objectMapperClass;
    private static Class<?> jsonNodeClass;

    static {
        if (isJacksonAvailable()) {
            Exceptions.rethrowRuntime(() -> {
                objectMapperClass = Class.forName("com.fasterxml.jackson.databind.ObjectMapper");
                jsonNodeClass = Class.forName("com.fasterxml.jackson.databind.JsonNode");
                objectMapper = objectMapperClass.getDeclaredConstructor().newInstance();
            });
        }
    }

    /**
     * Checks if Jackson library is available in the classpath.
     *
     * @return true if Jackson is available, false otherwise.
     */
    public static boolean isJacksonAvailable() {
        try {
            // Attempt to load a Jackson class to confirm its presence
            Class.forName("com.fasterxml.jackson.databind.ObjectMapper");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Serializes an object to a JSON string using Jackson.
     *
     * @param obj the object to serialize
     * @return the JSON string representation of the object, or null if serialization fails
     */
    public String toJson(Object obj) {
        if (objectMapper == null) {
            throw new IllegalStateException("Jackson ObjectMapper is not available.");
        }

        return Exceptions.rethrowRuntime(() -> {
            return (String) objectMapperClass.getMethod("writeValueAsString", Object.class).invoke(objectMapper, obj);
        });
    }

    /**
     * Deserializes a JSON string into a JsonNode using Jackson.
     *
     * @param json the JSON string to deserialize
     * @return a JsonNode representation of the JSON string, or null if deserialization fails
     */
    public static Object fromJsonAsNode(String json) {
        if (objectMapper == null) {
            throw new IllegalStateException("Jackson ObjectMapper is not available.");
        }

        return Exceptions.rethrowRuntime(() -> {
            return objectMapperClass.getMethod("readTree", String.class).invoke(objectMapper, json);
        });
    }

    /**
     * Traverses a JSON string using the given paths.
     *
     * @param json  the JSON string to traverse
     * @param paths the paths to traverse within the JSON
     * @return the JsonNode at the specified path, or null if not found
     */
    public static Object traverse(String json, Object... paths) {
        return Exceptions.rethrowRuntime(() -> {
            Object node = fromJsonAsNode(json);
            for (Object path : paths) {
                if (node == null) {
                    return null;
                }

                if (path instanceof Integer) {
                    node = jsonNodeClass.getMethod("get", int.class).invoke(node, path);
                } else {
                    node = jsonNodeClass.getMethod("get", String.class).invoke(node, path.toString());
                }
            }

            return node;
        });
    }

    /**
     * Traverses a JSON string using the given paths and returns the value as a string.
     *
     * @param json  the JSON string to traverse
     * @param paths the paths to traverse within the JSON
     * @return the value at the specified path as a string, or null if not found
     */
    public String traverseAsString(String json, Object... paths) {
        Object node = traverse(json, paths);

        if (node == null) {
            return null;
        }

        return Exceptions.rethrowRuntime(() -> {
            return (String) jsonNodeClass.getMethod("asText").invoke(node);
        });
    }
}
