package eu.lucaventuri.common;

import java.util.concurrent.atomic.AtomicReference;

public class JsonUtils {
    private static AtomicReference<JsonProcessor> serializer = new AtomicReference<>();

    public static void setSerializer(JsonProcessor serializer) {
        JsonUtils.serializer.set(serializer);
    }

    public static String toJson(Object obj) {
        return ensureSerializer().toJson(obj);
    }

    public static String traverseAsString(String json, Object... paths) {
        return ensureSerializer().traverseAsString(json, paths);
    }

    private static JsonProcessor ensureSerializer() {
        var ser = serializer.get();

        if (ser == null) {
            synchronized(JsonUtils.class) {
                ser = serializer.get();

                if (ser == null) {
                    if (JacksonProcessor.isJacksonAvailable()) {
                        ser = new JacksonProcessor();
                    }
                    else {
                        ser = new JsonMiniProcessor();
                        System.err.println("Please call setSerializer() to use a real production JSON serializer. For now, a simple one useful for experiments will be used instead. ");
                    }

                    setSerializer(ser);
                }
            }
        }

        return ser;
    }
}
