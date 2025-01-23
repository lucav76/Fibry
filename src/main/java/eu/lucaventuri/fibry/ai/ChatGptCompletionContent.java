package eu.lucaventuri.fibry.ai;

public record ChatGptCompletionContent(String role, String content)
{
    public static ChatGptCompletionContent from(LlmMessage message) {
        return new ChatGptCompletionContent(message.role(), message.prompt());
    }
}
