package com.example.chatbot;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ChatBotConfig {
    public String apiKey = "your-api-key-here";
    public String model = "GLM-4-Flash-250414";
    public String apiType = "ZHIPU";
    public String customUrl = "";
    public boolean aiEnabled = true;
    public int replyDelayMs = 0;
    public String aiName = "ChatBot";
    public String aiTrigger = "@AI";

    // AI 命令执行功能配置
    public boolean cmdEnabled = true;
    public String cmdBlacklist = "stop,setblock";
    public String cmdNormalWhitelist = "list,seed,spawn,home,help,me,tell,msg,w";
    public int cmdMaxPerMinute = 10;

    public void load(File configFile) {
        if (!configFile.exists()) {
            save(configFile);
            return;
        }

        try {
            String content = Files.readString(Paths.get(configFile.toURI()));
            String[] lines = content.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                if (line.startsWith("api_key")) {
                    apiKey = parseValue(line);
                } else if (line.startsWith("model")) {
                    model = parseValue(line);
                } else if (line.startsWith("api_type")) {
                    apiType = parseValue(line);
                } else if (line.startsWith("custom_url")) {
                    customUrl = parseValue(line);
                } else if (line.startsWith("ai_enabled")) {
                    aiEnabled = Boolean.parseBoolean(parseValue(line));
                } else if (line.startsWith("reply_delay_ms")) {
                    replyDelayMs = Integer.parseInt(parseValue(line));
                } else if (line.startsWith("ai_name")) {
                    aiName = parseValue(line);
                } else if (line.startsWith("ai_trigger")) {
                    aiTrigger = parseValue(line);
                } else if (line.startsWith("cmd_enabled")) {
                    cmdEnabled = Boolean.parseBoolean(parseValue(line));
                } else if (line.startsWith("cmd_max_per_minute")) {
                    cmdMaxPerMinute = Integer.parseInt(parseValue(line));
                } else if (line.startsWith("cmd_blacklist")) {
                    cmdBlacklist = parseValue(line);
                } else if (line.startsWith("cmd_normal_whitelist")) {
                    cmdNormalWhitelist = parseValue(line);
                }
            }
        } catch (Exception e) {
            ChatBot.LOGGER.warn("Failed to load config: " + e.getMessage());
            save(configFile);
        }
    }

    public void save(File configFile) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ChatBot Configuration\n\n");
        sb.append("# API Key for AI service\n");
        sb.append("api_key = \"").append(apiKey).append("\"\n\n");
        sb.append("# Model name (varies by API provider)\n");
        sb.append("model = \"").append(model).append("\"\n\n");
        sb.append("# API type: ZHIPU, VOLC, OPENAI, DEEPSEEK, MINIMAX, or CUSTOM\n");
        sb.append("#   ZHIPU:    Zhipu GLM API (https://open.bigmodel.cn/)\n");
        sb.append("#   VOLC:     Volcengine Ark (https://console.volcengine.com/ark/)\n");
        sb.append("#   OPENAI:   OpenAI API\n");
        sb.append("#   DEEPSEEK: DeepSeek API\n");
        sb.append("#   MINIMAX:  MiniMax API\n");
        sb.append("#   CUSTOM:   Custom API endpoint (requires custom_url)\n");
        sb.append("api_type = \"").append(apiType).append("\"\n\n");
        sb.append("# Custom API URL (only used when api_type = CUSTOM)\n");
        sb.append("custom_url = \"").append(customUrl).append("\"\n\n");
        sb.append("# Enable AI responses\n");
        sb.append("ai_enabled = ").append(aiEnabled).append("\n\n");
        sb.append("# Reply delay in milliseconds\n");
        sb.append("reply_delay_ms = ").append(replyDelayMs).append("\n\n");
        sb.append("# AI chat bot display name\n");
        sb.append("ai_name = \"").append(aiName).append("\"\n\n");
        sb.append("# AI trigger prefix (e.g., @AI, @Bot)\n");
        sb.append("ai_trigger = \"").append(aiTrigger).append("\"\n\n");

        sb.append("# ===== AI Command Execution =====\n\n");
        sb.append("# Enable AI to execute server commands\n");
        sb.append("cmd_enabled = ").append(cmdEnabled).append("\n\n");
        sb.append("# Global command blacklist (forbidden for everyone, including OPs)\n");
        sb.append("cmd_blacklist = \"").append(cmdBlacklist).append("\"\n\n");
        sb.append("# Command whitelist for non-OP players (comma-separated)\n");
        sb.append("cmd_normal_whitelist = \"").append(cmdNormalWhitelist).append("\"\n\n");
        sb.append("# Max commands per player per minute (0 = unlimited)\n");
        sb.append("cmd_max_per_minute = ").append(cmdMaxPerMinute).append("\n");

        try {
            Files.writeString(Paths.get(configFile.toURI()), sb.toString());
        } catch (IOException e) {
            ChatBot.LOGGER.warn("Failed to save config: " + e.getMessage());
        }
    }

    private String parseValue(String line) {
        int eqIndex = line.indexOf('=');
        if (eqIndex == -1) return "";
        String value = line.substring(eqIndex + 1).trim();
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
