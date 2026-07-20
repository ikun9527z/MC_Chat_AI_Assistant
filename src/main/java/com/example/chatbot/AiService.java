package com.example.chatbot;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
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
    // AI 命令执行相关配置（用于构建 system prompt）
    private String cmdBlacklist;
    private String cmdNormalWhitelist;

    public AiService() {
        this.httpClient = HttpClient.newHttpClient();
        this.gson = new Gson();
        this.apiKey = "";
        this.model = "";
        this.apiType = ApiType.ZHIPU;
        this.customUrl = "";
        this.enabled = false;
        this.aiName = "ChatBot";
        this.cmdBlacklist = "";
        this.cmdNormalWhitelist = "";
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

    public void setCmdBlacklist(String cmdBlacklist) {
        this.cmdBlacklist = cmdBlacklist == null ? "" : cmdBlacklist;
    }

    public void setCmdNormalWhitelist(String cmdNormalWhitelist) {
        this.cmdNormalWhitelist = cmdNormalWhitelist == null ? "" : cmdNormalWhitelist;
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

    /**
     * 第一轮 AI 调用：根据玩家消息生成回复，回复中可能包含 <cmd>...</cmd> 标签。
     */
    public String getResponse(String playerName, boolean isOp, String message) {
        if (!enabled || apiKey.isEmpty() || model.isEmpty()) {
            ChatBot.LOGGER.warn("[ChatBot] AI not enabled. apiKey: " + (apiKey.isEmpty() ? "EMPTY" : "SET") + ", model: " + (model.isEmpty() ? "EMPTY" : "SET"));
            return null;
        }

        String apiUrl = getApiUrl();
        if (apiUrl.isEmpty()) {
            ChatBot.LOGGER.error("[ChatBot] API URL is empty, please configure custom_url");
            return null;
        }

        ChatBot.LOGGER.info("[ChatBot] Sending first-round request to " + apiType.name
                + " API (isOp=" + isOp + "), message: " + message.substring(0, Math.min(30, message.length())) + "...");

        String systemPrompt = buildSystemPrompt(playerName, isOp);

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);

        JsonArray messages = new JsonArray();
        messages.add(buildMessage("system", systemPrompt));
        messages.add(buildMessage("user", message));
        requestBody.add("messages", messages);

        requestBody.addProperty("temperature", 0.7);
        requestBody.addProperty("max_tokens", 600);

        return sendChatRequest(apiUrl, requestBody);
    }

    /**
     * 第二轮 AI 调用：把命令执行结果反馈给 AI，让 AI 总结后回复玩家。
     */
    public String getResponseWithFeedback(String playerName, boolean isOp,
                                          String originalUserMessage, String aiFirstResponse,
                                          String command, CommandResult result) {
        if (!enabled || apiKey.isEmpty() || model.isEmpty()) {
            ChatBot.LOGGER.warn("[ChatBot] AI not enabled for second-round call");
            return null;
        }

        String apiUrl = getApiUrl();
        if (apiUrl.isEmpty()) {
            return null;
        }

        ChatBot.LOGGER.info("[ChatBot] Sending second-round request to " + apiType.name
                + " API for result summary (success=" + result.success() + ")");

        String systemPrompt = buildSystemPrompt(playerName, isOp);

        String feedbackText = "系统已尝试执行你刚才输出的命令：\n"
                + "命令: " + command + "\n"
                + "结果: " + result.toFeedbackText() + "\n\n"
                + "请用一句话向玩家总结这个结果（不要重复命令本身，要描述对玩家的影响）。";

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);

        JsonArray messages = new JsonArray();
        messages.add(buildMessage("system", systemPrompt));
        messages.add(buildMessage("user", originalUserMessage));
        messages.add(buildMessage("assistant", aiFirstResponse));
        messages.add(buildMessage("user", feedbackText));
        requestBody.add("messages", messages);

        requestBody.addProperty("temperature", 0.5);
        requestBody.addProperty("max_tokens", 300);

        return sendChatRequest(apiUrl, requestBody);
    }

    private JsonObject buildMessage(String role, String content) {
        JsonObject msg = new JsonObject();
        msg.addProperty("role", role);
        msg.addProperty("content", content);
        return msg;
    }

    /**
     * 根据玩家身份构建 system prompt。
     * - OP：可执行大部分命令，列出黑名单
     * - 普通玩家：只能执行白名单命令，TP 目标必须是自己
     */
    private String buildSystemPrompt(String playerName, boolean isOp) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是").append(aiName).append("，一个运行在 Minecraft 1.21 服务器中的智能助手。\n\n");

        sb.append("【当前对话玩家】\n");
        sb.append("- 名字：").append(playerName).append("\n");
        sb.append("- 身份：").append(isOp ? "管理员（OP，权限等级4，最高）" : "普通玩家").append("\n\n");

        sb.append("【能力边界】\n");
        if (isOp) {
            boolean blacklistEmpty = cmdBlacklist == null || cmdBlacklist.isBlank();
            if (blacklistEmpty) {
                sb.append("你是 OP，可以执行任何服务器命令（无黑名单限制），权限等级 4（最高）。\n\n");
            } else {
                sb.append("你是 OP，可以执行大部分服务器命令来帮助玩家。但以下命令被全局禁止，");
                sb.append("如果玩家要求执行这些命令，请直接告知无法执行：\n");
                sb.append(formatCommandList(cmdBlacklist)).append("\n\n");
            }
        } else {
            boolean whitelistEmpty = cmdNormalWhitelist == null || cmdNormalWhitelist.isBlank();
            if (whitelistEmpty) {
                sb.append("你是普通玩家的助手，当前没有任何命令执行权限。\n");
                sb.append("所有命令（包括 tp、give、gamemode、kill、list 等）你都无法执行，");
                sb.append("请直接告知玩家无权限，并建议联系 OP 处理。\n\n");
            } else {
                sb.append("你是普通玩家的助手，只能执行以下命令：\n");
                sb.append(formatCommandList(cmdNormalWhitelist)).append("\n");
                sb.append("如果玩家要求执行上述以外的命令（如 give、gamemode、kill 等），");
                sb.append("请直接告知该玩家无权限，并建议联系 OP。\n\n");
            }
        }

        sb.append("【命令协议】\n");
        sb.append("当需要执行命令时，在回复中嵌入标签：<cmd>命令 参数</cmd>\n");
        sb.append("示例：\n");
        if (isOp) {
            sb.append("- 给玩家一个钻石：<cmd>give ").append(playerName).append(" diamond 1</cmd>\n");
            sb.append("- 设为白天：<cmd>time set day</cmd>\n");
            sb.append("- 传送玩家到坐标：<cmd>tp ").append(playerName).append(" 100 70 200</cmd>\n");
            sb.append("- 获取其他玩家信息：<cmd>data get entity ").append("目标玩家名</cmd>\n");
        } else {
            boolean whitelistEmpty = cmdNormalWhitelist == null || cmdNormalWhitelist.isBlank();
            if (whitelistEmpty) {
                sb.append("（你当前无任何命令权限，不要输出 <cmd> 标签）\n");
            } else {
                sb.append("- 查看在线玩家：<cmd>list</cmd>\n");
                sb.append("- 查看天气：<cmd>weather query</cmd>\n");
                sb.append("- 传送自己到其他玩家身边：<cmd>tp ").append("目标玩家名</cmd>\n");
                sb.append("- 传送自己到坐标：<cmd>tp 100 70 200</cmd>\n");
            }
        }
        sb.append("\n");

        sb.append("【协议规则】\n");
        sb.append("1. 每次回复最多包含 1 个 <cmd> 标签\n");
        sb.append("2. <cmd> 标签外的内容用自然语言向玩家解释你将做什么\n");
        sb.append("3. 不要输出未闭合或嵌套的 <cmd> 标签\n");
        sb.append("4. 系统会执行命令并把结果反馈给你，你再向玩家总结结果\n");
        sb.append("5. 如果不需要执行命令，就正常聊天，不要输出 <cmd> 标签\n");
        sb.append("6. 只能执行白名单中列出的命令（非 OP）或非黑名单的命令（OP）。");
        sb.append("如果玩家要求的命令不在你的权限内，请直接告知无权限，不要输出 <cmd> 标签\n");
        sb.append("\n");

        sb.append("【语气】友好、简洁，不要重复命令文本本身，重点说明对玩家的影响。");
        return sb.toString();
    }

    private String formatCommandList(String csv) {
        if (csv == null || csv.isEmpty()) return "（无）";
        StringBuilder sb = new StringBuilder();
        String[] parts = csv.split(",");
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(parts[i].trim());
        }
        return sb.toString();
    }

    /**
     * 发送 chat completion 请求并解析返回内容。
     * 抽取自原 getResponse 的 HTTP 逻辑，被第一轮和第二轮共用。
     */
    private String sendChatRequest(String apiUrl, JsonObject requestBody) {
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
