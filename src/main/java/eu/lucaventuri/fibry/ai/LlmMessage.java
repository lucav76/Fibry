package eu.lucaventuri.fibry.ai;

/** Marker interface, to implement different types of LLM messages */
public record LlmMessage(String role, String prompt) {
}
