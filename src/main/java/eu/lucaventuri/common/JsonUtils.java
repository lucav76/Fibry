package eu.lucaventuri.common;

import java.util.concurrent.atomic.AtomicReference;

public class JsonUtils {
    private static AtomicReference<JsonProcessor> processor = new AtomicReference<>();

    public static void setProcessor(JsonProcessor processor) {
        JsonUtils.processor.set(processor);
    }

    public static String toJson(Object obj) {
        return ensureProcessor().toJson(obj);
    }

    public static String traverseAsString(String json, Object... paths) {
        return ensureProcessor().traverseAsString(json, paths);
    }

    private static JsonProcessor ensureProcessor() {
        var proc = processor.get();

        if (proc == null) {
            synchronized(JsonUtils.class) {
                proc = processor.get();

                if (proc == null) {
                    if (JacksonProcessor.isJacksonAvailable()) {
                        proc = new JacksonProcessor();
                    }
                    else {
                        proc = new JsonMiniProcessor();
                        System.err.println("Please call setSerializer() to use a real production JSON serializer. For now, a simple one useful for experiments will be used instead. ");
                    }

                    setProcessor(proc);
                }
            }
        }

        return proc;
    }
}
