package eu.lucaventuri.common;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.*;
import java.util.function.UnaryOperator;

/** Utilities for records, in particular it provides a "wither" done with reflection */
public class RecordUtils {
    private RecordUtils() { }
    /**
     * Creates a new record with the same attributes as the given record, except for the specified attribute,
     * which will be updated with the given value.
     *
     * @param record       the original record
     * @param attributeName the name of the attribute to update
     * @param newValue      the new value for the specified attribute
     * @param <T>           the type of the record
     * @return a new record with the updated attribute
     * @throws IllegalArgumentException if the record is not a record type or the attribute does not exist
     * @throws RuntimeException         if an error occurs during record creation
     */
    public static <T extends Record> T with(T record, String attributeName, Object newValue) {
        if (!record.getClass().isRecord()) {
            throw new IllegalArgumentException("Provided object is not a record.");
        }

        Class<?> recordClass = record.getClass();
        RecordComponent[] components = recordClass.getRecordComponents();

        Object[] values = new Object[components.length];
        boolean attributeFound = false;

        for (int i = 0; i < components.length; i++) {
            RecordComponent component = components[i];
            try {
                if (component.getName().equals(attributeName)) {
                    values[i] = newValue;
                    attributeFound = true;
                } else {
                    values[i] = component.getAccessor().invoke(record);
                }
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Error accessing record component: " + component.getName(), e);
            }
        }

        if (!attributeFound) {
            throw new IllegalArgumentException("Attribute '" + attributeName + "' does not exist in the record.");
        }

        try {
            Constructor<?> constructor = recordClass.getDeclaredConstructor(
                    Arrays.stream(components).map(RecordComponent::getType).toArray(Class[]::new)
            );

            return (T) constructor.newInstance(values);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Error creating new record instance.", e);
        }
    }

    public static <T extends Record> T with(T record, String attributeName1, Object newValue1,
                                            String attributeName2, Object newValue2) {
        T updatedRecord = with(record, attributeName1, newValue1);

        return with(updatedRecord, attributeName2, newValue2);
    }

    public static <T extends Record> T with(T record, String attributeName1, Object newValue1,
                                            String attributeName2, Object newValue2,
                                            String attributeName3, Object newValue3) {
        T updatedRecord = with(record, attributeName1, newValue1);
        updatedRecord = with(updatedRecord, attributeName2, newValue2);

        return with(updatedRecord, attributeName3, newValue3);
    }

    /**
     * Overloaded method to update four attributes.
     */
    public static <T extends Record> T with(T record, String attributeName1, Object newValue1,
                                            String attributeName2, Object newValue2,
                                            String attributeName3, Object newValue3,
                                            String attributeName4, Object newValue4) {
        T updatedRecord = with(record, attributeName1, newValue1);
        updatedRecord = with(updatedRecord, attributeName2, newValue2);
        updatedRecord = with(updatedRecord, attributeName3, newValue3);

        return with(updatedRecord, attributeName4, newValue4);
    }

    public static <T extends Record> T with(T record, String attributeName1, Object newValue1,
                                            String attributeName2, Object newValue2,
                                            String attributeName3, Object newValue3,
                                            String attributeName4, Object newValue4,
                                            String attributeName5, Object newValue5) {
        T updatedRecord = with(record, attributeName1, newValue1);
        updatedRecord = with(updatedRecord, attributeName2, newValue2);
        updatedRecord = with(updatedRecord, attributeName3, newValue3);
        updatedRecord = with(updatedRecord, attributeName4, newValue4);

        return with(updatedRecord, attributeName5, newValue5);
    }

    public static <T extends Record> T with(T record, Map<String, Object> updates) {
        T updatedRecord = record;
        for (Map.Entry<String, Object> entry : updates.entrySet()) {
            updatedRecord = with(updatedRecord, entry.getKey(), entry.getValue());
        }
        return updatedRecord;
    }

    public static <T extends Record, O> O getAttribute(T record, String attributeName) {
        if (!record.getClass().isRecord()) {
            throw new IllegalArgumentException("Provided object is not a record.");
        }

        RecordComponent[] components = record.getClass().getRecordComponents();

        for (RecordComponent component : components) {
            if (component.getName().equals(attributeName)) {
                try {
                    return (O) component.getAccessor().invoke(record);
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException("Error retrieving value of attribute: " + attributeName, e);
                }
            }
        }

        throw new IllegalArgumentException("Attribute '" + attributeName + "' does not exist in the record.");
    }

    public static <T extends Record, O> T merge(T record, String attributeName, UnaryOperator<O> replacer) {
        O prevValue = getAttribute(record, attributeName);
        O newValue = replacer.apply(prevValue);
        return with(record, attributeName, newValue);
    }

    public static <T extends Record, O> T addToList(T record, String attributeName, List<O> valuesToAdd) {
        List<O> prevValue = getAttribute(record, attributeName);
        List<O> newValue = prevValue == null ? new ArrayList<>() : new ArrayList<>(prevValue);

        newValue.addAll(valuesToAdd);
        return with(record, attributeName, newValue);
    }

    public static <T extends Record, O> T addToSet(T record, String attributeName, Set<O> valuesToAdd) {
        Set<O> prevValue = getAttribute(record, attributeName);
        Set<O> newValue = prevValue == null ? new HashSet<>() : new HashSet<>(prevValue);

        newValue.addAll(valuesToAdd);
        return with(record, attributeName, newValue);
    }

    public static <T extends Record, K, V> T addToMap(T record, String attributeName, Map<K, V> valuesToAdd) {
        Map<K, V> prevValue = getAttribute(record, attributeName);
        Map<K, V> newValue = prevValue == null ? new HashMap<>() : new HashMap<>(prevValue);

        newValue.putAll(valuesToAdd);
        return with(record, attributeName, newValue);
    }

    /**
     * Replaces placeholders in the given input string with corresponding field values from the provided record.
     * Placeholders in the input string must match the field names of the record and be enclosed in curly braces, e.g., {fieldName}.
     *
     * @param <T> the type of the record, which must extend {@link Record}
     * @param input the input string containing placeholders to be replaced
     * @param record the record whose field values will be used for replacement
     * @return the input string with placeholders replaced by the corresponding field values from the record
     * @throws IllegalArgumentException if the record or input string is null
     * @throws RuntimeException         if an error occurs while accessing field values of the record
     */
    public static <T extends Record> String replaceAllFields(String input, T record) {
        if (record == null || input == null) {
            throw new IllegalArgumentException("Record and input string cannot be null");
        }

        for (RecordComponent component : record.getClass().getRecordComponents()) {
            try {
                Object fieldValue = component.getAccessor().invoke(record);

                if (fieldValue != null) {
                    input = input.replaceAll("\\{" + component.getName() + "\\}", fieldValue.toString());
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to access field value for " + component.getName(), e);
            }
        }

        return input;
    }

    public static <T extends Record> String replaceField(String input, T record, String attributeName) {
        if (record == null || input == null || attributeName == null) {
            throw new IllegalArgumentException("Record, input string, and attribute name cannot be null");
        }

        for (RecordComponent component : record.getClass().getRecordComponents()) {
            if (component.getName().equals(attributeName)) {
                try {
                    Object fieldValue = component.getAccessor().invoke(record);

                    if (fieldValue != null) {
                        return input.replaceAll("\\{" + attributeName + "\\}", fieldValue.toString());
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Failed to access field value for " + attributeName, e);
                }
            }
        }

        // If the attribute name doesn't match any record component, return the input unchanged
        return input;
    }

    public static <T extends Record> String replaceFields(T record, String input, Collection<String> attributeNames) {
        if (record == null || input == null || attributeNames == null) {
            throw new IllegalArgumentException("Record, input string, and attribute names cannot be null");
        }

        String result = input;

        // Iterate over the attribute names to replace them one by one
        for (String attributeName : attributeNames) {
            result = replaceField(result, record, attributeName);
        }

        return result;
    }
}

