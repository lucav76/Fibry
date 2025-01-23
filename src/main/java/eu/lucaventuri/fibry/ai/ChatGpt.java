package eu.lucaventuri.fibry.ai;

import eu.lucaventuri.common.Exceptions;
import eu.lucaventuri.common.HttpUtil;
import eu.lucaventuri.common.JsonUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatGpt {
    public static final LLM GPT_MODEL_4O = llm("gpt-4o");
    public static final LLM GPT_MODEL_4O_MINI = llm("gpt-4o-mini");
    public static final LLM GPT_MODEL_O1 = llm("o1");
    public static final LLM GPT_MODEL_O1_MINI = llm("o1-mini");

    enum ChatGptResponseFormat {
        TEXT, JSON_OBJECT;

        public String getText() {
            return this.name().toLowerCase();
        }
    }

    public record ChatGptCompletion(String model, ChatGptResponseFormat response_format, List<ChatGptCompletionContent> messages) { }

    public static String request(String model, String role, String content) {
        return request(model, List.of(new LlmMessage(role, content)));
    }

    public static String request(String model, List<LlmMessage> contents) {
        return extractContent(requestRaw(model, contents), true);
    }

    public static String requestRaw(String model, String role, String content)  {
        return requestRaw(model, List.of(new LlmMessage(role, content)));
    }

    public static String requestRaw(String model, List<LlmMessage> contents) {
        return Exceptions.rethrowRuntime(() -> {
            Object request = new ChatGptCompletion(model, null, contents.stream().map(ChatGptCompletionContent::from).toList());
            URI uri = new URI("https://api.openai.com/v1/chat/completions");

            final HttpResponse<String> res;
            try {
                res = HttpUtil.post(uri, JsonUtils.toJson(request), headersJson(), 10);
            } catch (InterruptedException e) {
                throw new IOException(e);
            }

            return res.body();
        });
    }

    private static Map<String, String> headers() {
        return headers(Map.of());
    }

    private static Map<String, String> headersJson() {
        return headers(Map.of("Content-Type", "application/json"));
    }

    private static Map<String, String> headers(Map<String, Object> additionalHeaders) {
        var map = new HashMap<String, String>();

        map.put("Authorization", "Bearer " + System.getProperty("OPENAI_API_KEY"));

        for (var e : additionalHeaders.entrySet())
            map.put(e.getKey(), e.getValue() instanceof String s ? s : (e.getValue() instanceof Number n ? n.toString() : JsonUtils.toJson(e.getValue())));

        return map;
    }

    public static String extractContent(String gptAnswer, boolean clean) {
        String answer = JsonUtils.traverseAsString(gptAnswer, "choices", 0, "message", "content");

        if (answer == null) {
            // Try old API
            answer = JsonUtils.traverseAsString(gptAnswer, "choices", 0, "text");

            if (answer == null)
                throw new UnsupportedOperationException("Unrecognized answer format: " + gptAnswer);
        }

        if (clean) {
            answer = answer.charAt(0) == '\"' ? answer.substring(1) : answer;
            answer = answer.startsWith("«") ? answer.substring(1) : answer;
            answer = answer.endsWith("\"") ? answer.substring(0, answer.length() - 1) : answer;
            answer = answer.endsWith("»") ? answer.substring(0, answer.length() - 1) : answer;
        }

        return answer;
    }

    public static String extractContent(String gptAnswer) {
        return extractContent(gptAnswer, false);
    }

    public static LLM llm(String modelName) {
        return new LLM() {
            @Override
            public String call(List<LlmMessage> promptParts) {
                return ChatGpt.request(modelName, promptParts);
            }

            @Override
            public String modelName() {
                return modelName;
            }
        };
    }
}
