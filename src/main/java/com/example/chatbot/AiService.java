package com.example.chatbot;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class AiService {
    public enum ApiType {
        ZHIPU("Zhipu GLM", "https://open.bigmodel.cn/api/paas/v4/chat/completions", "Bearer "),
        VOLC("Volcengine Ark", "https://ark.cn-beijing.volces.com/api/v3/chat/completions", "Bearer "),
        OPENAI("OpenAI", "https://api.openai.com/v1/chat/completions", "Bearer "),
        DEEPSEEK("DeepSeek", "https://api.deepseek.com/v1/chat/completions", "Bearer "),
        MINIMAX("MiniMax", "https://api.minimax.chat/v1/text/chatcompletion", "Bearer "),
        CUSTOM("Custom", "", "");

        public final String name;
        public final String defaultUrl;
        public final String authPrefix;

        ApiType(String name, String defaultUrl, String authPrefix) {
            this.name = name;
            this.defaultUrl = defaultUrl;
            this.authPrefix = authPrefix;
        }
    }

    private final HttpClient httpClient;
    private final Gson gson;
    private String apiKey;
    private String model;
    private ApiType apiType;
    private String customUrl;
    private boolean enabled;
    private String aiName;

    public AiService() {
        this.httpClient = HttpClient.newHttpClient();
        this.gson = new Gson();
        this.apiKey = "";
        this.model = "";
        this.apiType = ApiType.ZHIPU;
        this.customUrl = "";
        this.enabled = false;
        this.aiName = "ChatBot";
        ChatBot.LOGGER.info("[ChatBot] AiService created");
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
        updateEnabled();
    }

    public void setModel(String model) {
        this.model = model;
        updateEnabled();
    }

    public void setApiType(String apiTypeStr) {
        try {
            this.apiType = ApiType.valueOf(apiTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            this.apiType = ApiType.ZHIPU;
            ChatBot.LOGGER.warn("[ChatBot] Unknown API type: " + apiTypeStr + ", defaulting to ZHIPU");
        }
        ChatBot.LOGGER.info("[ChatBot] API type set: " + this.apiType.name + " (" + this.apiType + ")");
        updateEnabled();
    }

    public void setCustomUrl(String customUrl) {
        this.customUrl = customUrl;
    }

    public void setAiName(String aiName) {
        this.aiName = aiName;
    }

    public String getAiName() {
        return aiName;
    }

    private void updateEnabled() {
        this.enabled = apiKey != null && !apiKey.isEmpty() && model != null && !model.isEmpty();
        ChatBot.LOGGER.info("[ChatBot] AiService enabled: " + enabled);
    }

    public boolean isEnabled() {
        return enabled;
    }

    private String getApiUrl() {
        if (apiType == ApiType.CUSTOM && customUrl != null && !customUrl.isEmpty()) {
            return customUrl;
        }
        return apiType.defaultUrl;
    }

    public String getResponse(String playerName, String message) {
        if (!enabled || apiKey.isEmpty() || model.isEmpty()) {
            ChatBot.LOGGER.warn("[ChatBot] AI not enabled. apiKey: " + (apiKey.isEmpty() ? "EMPTY" : "SET") + ", model: " + (model.isEmpty() ? "EMPTY" : "SET"));
            return null;
        }

        String apiUrl = getApiUrl();
        if (apiUrl.isEmpty()) {
            ChatBot.LOGGER.error("[ChatBot] API URL is empty, please configure custom_url");
            return null;
        }

        ChatBot.LOGGER.info("[ChatBot] Sending request to " + apiType.name + " API, message: " + message.substring(0, Math.min(30, message.length())) + "...");

        String systemPrompt = "你是一个Minecraft服务器的聊天机器人，名字叫" + aiName + "。请用友好、简短的语言回复玩家的消息。玩家名字是：" + playerName + "。";

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);

        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", systemPrompt);

        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", message);

        JsonObject[] messages = {systemMessage, userMessage};
        requestBody.add("messages", gson.toJsonTree(messages));

        requestBody.addProperty("temperature", 0.7);
        requestBody.addProperty("max_tokens", 500);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", apiType.authPrefix + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                    .timeout(java.time.Duration.ofSeconds(15))
                    .build();

            ChatBot.LOGGER.info("[ChatBot] HTTP request built, sending to: " + apiUrl + ", model: " + model);

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            ChatBot.LOGGER.info("[ChatBot] HTTP response received, status code: " + response.statusCode());

            if (response.statusCode() == 200) {
                ChatBot.LOGGER.info("[ChatBot] API response body: " + response.body().substring(0, Math.min(200, response.body().length())) + "...");
                JsonObject responseJson = gson.fromJson(response.body(), JsonObject.class);
                if (responseJson.has("choices")) {
                    var choices = responseJson.getAsJsonArray("choices");
                    if (choices.size() > 0) {
                        var choice = choices.get(0).getAsJsonObject();
                        if (choice.has("message")) {
                            var msg = choice.getAsJsonObject("message");
                            if (msg.has("content")) {
                                String content = msg.get("content").getAsString().trim();
                                ChatBot.LOGGER.info("[ChatBot] AI response received: " + content.substring(0, Math.min(50, content.length())) + "...");
                                return content;
                            } else {
                                ChatBot.LOGGER.warn("[ChatBot] Response message has no content field");
                            }
                        } else {
                            ChatBot.LOGGER.warn("[ChatBot] Response choice has no message field");
                        }
                    } else {
                        ChatBot.LOGGER.warn("[ChatBot] Response has no choices");
                    }
                } else {
                    ChatBot.LOGGER.warn("[ChatBot] Response has no choices field");
                }
                return null;
            } else {
                ChatBot.LOGGER.error("[ChatBot] " + apiType.name + " API request failed with status: " + response.statusCode());
                ChatBot.LOGGER.error("[ChatBot] Error response: " + response.body());
                return null;
            }
        } catch (IOException e) {
            ChatBot.LOGGER.error("[ChatBot] " + apiType.name + " API IO error: " + e.getMessage());
            e.printStackTrace();
            return null;
        } catch (InterruptedException e) {
            ChatBot.LOGGER.error("[ChatBot] " + apiType.name + " API request interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
