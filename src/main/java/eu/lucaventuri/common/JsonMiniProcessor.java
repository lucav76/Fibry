package eu.lucaventuri.common;

import java.lang.reflect.RecordComponent;
import java.util.Collection;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Json processor used only to test Fibry with agents; it should work with ChatGpt
 * */
class JsonMiniProcessor implements JsonProcessor {
    @Override
    public String traverseAsString(String json, Object... paths) {
        return findAttributeValue(json, paths[paths.length-1].toString());
    }

    public static String findAttributeValue(String json, String attributeName) {
        String regex = "\\\"" + attributeName + "\\\"\\s*:\\s*\\\"(.*?)(?<!\\\\)\\\"";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(json);

        if (matcher.find()) {
            return matcher.group(1)
                    .replaceAll("\\\\\"", "\"")
                    .replaceAll("\\\\n", "\n")
                    .replaceAll("\\\\r", "\r")
                    .replaceAll("\\\\t", "\t")
                    .replaceAll("\\\\", "\\");
        } else {
            return null;
        }
    }


    public String toJson(Object record) {
        if (!record.getClass().isRecord()) {
            throw new IllegalArgumentException("The provided object is not a record.");
        }

        StringBuilder jsonBuilder = new StringBuilder("{");

        RecordComponent[] components = record.getClass().getRecordComponents();

        for (int i = 0; i < components.length; i++) {
            RecordComponent component = components[i];
            String name = component.getName();
            Object value;
            try {
                value = component.getAccessor().invoke(record);
            } catch (Exception e) {
                throw new RuntimeException("Failed to access record component: " + name, e);
            }

            jsonBuilder.append("\"").append(name).append("\": ");
            jsonBuilder.append(formatValue(value));

            if (i < components.length - 1) {
                jsonBuilder.append(", ");
            }
        }

        jsonBuilder.append("}");
        return jsonBuilder.toString();
    }

    private String formatValue(Object value) {
        if (value == null) {
            return "null";
        } else if (value instanceof String) {
            return "\"" + escapeString((String) value) + "\"";
        } else if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        } else if (value instanceof Collection) {
            return formatCollection((Collection<?>) value);
        } else if (value instanceof Map) {
            return formatMap((Map<?, ?>) value);
        } else if (value.getClass().isArray()) {
            return formatArray(value);
        } else if (value.getClass().isRecord()) {
            return toJson(value); // Recursively serialize the record
        } else {
            // For unsupported types, convert to string and quote it
            return "\"" + escapeString(value.toString()) + "\"";
        }
    }

    private String formatCollection(Collection<?> collection) {
        StringBuilder builder = new StringBuilder("[");
        int index = 0;
        for (Object item : collection) {
            builder.append(formatValue(item));
            if (index < collection.size() - 1) {
                builder.append(", ");
            }
            index++;
        }
        builder.append("]");
        return builder.toString();
    }

    private String formatMap(Map<?, ?> map) {
        StringBuilder builder = new StringBuilder("{");
        int index = 0;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            builder.append("\"").append(escapeString(entry.getKey().toString())).append("\": ");
            builder.append(formatValue(entry.getValue()));
            if (index < map.size() - 1) {
                builder.append(", ");
            }
            index++;
        }
        builder.append("}");
        return builder.toString();
    }

    private String formatArray(Object array) {
        StringBuilder builder = new StringBuilder("[");
        int length = java.lang.reflect.Array.getLength(array);
        for (int i = 0; i < length; i++) {
            builder.append(formatValue(java.lang.reflect.Array.get(array, i)));
            if (i < length - 1) {
                builder.append(", ");
            }
        }
        builder.append("]");
        return builder.toString();
    }

    // Helper method to escape special characters in strings
    private static String escapeString(String str) {
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // Main method for testing
    public static void main(String[] args) {
        // Example record
        record Person(String name, int age, boolean active) {}

        Person person = new Person("Alice", 30, true);

        // Serialize the record to JSON
        String json = new JsonMiniProcessor().toJson(person);
        System.out.println(json);
        // Output: {"name": "Alice", "age": 30, "active": true}
    }
}

