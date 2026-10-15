
package com.codingtwin.backend;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final RestClient ollamaClient =
            RestClient.create("http://127.0.0.1:11434");

    private final Map<String, List<Map<String, String>>> conversations =
            new ConcurrentHashMap<>();

    private static final String SYSTEM_PROMPT = """
            You are Coding Twin, a local coding assistant.
            Focus on the user's current coding task and preserve its
            programming language, method names, requirements, and context.
            Interpret follow-up messages in the context of the current task.
            If the user asks to modify previous code, modify that code
            rather than starting an unrelated task.
            Do not switch programming languages unless requested.
            Give accurate code and explanations. If uncertain, say so.
            You cannot access, modify, or execute workspace files.
            """;

    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody ChatRequest request) {

        if (request.message() == null || request.message().isBlank()) {
            throw new IllegalArgumentException("Message cannot be empty.");
        }

        String conversationId = request.conversationId();

        if (conversationId == null || conversationId.isBlank()) {
            conversationId = UUID.randomUUID().toString();
        }

        List<Map<String, String>> history =
                conversations.computeIfAbsent(
                        conversationId,
                        id -> new ArrayList<>()
                );

        synchronized (history) {
            history.add(Map.of(
                    "role", "user",
                    "content", request.message()
            ));

            List<Map<String, String>> messages = new ArrayList<>();

            messages.add(Map.of(
                    "role", "system",
                    "content", SYSTEM_PROMPT
            ));

            messages.addAll(history);

            Map<String, Object> ollamaRequest = new HashMap<>();
            ollamaRequest.put("model", "qwen2.5-coder:3b");
            ollamaRequest.put("messages", messages);
            ollamaRequest.put("stream", false);

            Map<?, ?> result = ollamaClient.post()
                    .uri("/api/chat")
                    .body(ollamaRequest)
                    .retrieve()
                    .body(Map.class);

            if (result == null || !(result.get("message") instanceof Map<?, ?> modelMessage)
                    || modelMessage.get("content") == null) {
                throw new IllegalStateException(
                        "Ollama returned an empty response."
                );
            }

            String answer = modelMessage.get("content").toString();

            history.add(Map.of(
                    "role", "assistant",
                    "content", answer
            ));

            return Map.of(
                    "conversationId", conversationId,
                    "response", answer
            );
        }
    }

    public record ChatRequest(
            String message,
            String conversationId
    ) {
    }
}