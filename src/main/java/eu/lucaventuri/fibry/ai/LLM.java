package eu.lucaventuri.fibry.ai;

import java.util.List;

/** Generic interface to call an LLM */
public interface LLM {
    default String call(String role, String prompt) {
        return call(List.of(new LlmMessage(role, prompt)));
    }

    default String call(String prompt) {
        return call(List.of(new LlmMessage("user", prompt)));
    }

    String call(List<LlmMessage> promptParts);

    String modelName();
}
