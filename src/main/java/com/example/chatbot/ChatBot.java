package com.example.chatbot;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

@Mod(ChatBot.MOD_ID)
public class ChatBot {
    public static final String MOD_ID = "chatbot";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static AiService aiService;
    public static ChatBotConfig config;
    public static CommandExecutor commandExecutor;

    public ChatBot(IEventBus modEventBus) {
        LOGGER.info("[ChatBot] Initializing ChatBot mod...");
        aiService = new AiService();
        config = new ChatBotConfig();
        NeoForge.EVENT_BUS.register(new ChatListener());
        loadConfig();
        LOGGER.info("[ChatBot] ChatBot initialized! API Key: " + (config.apiKey.isEmpty() ? "NOT SET" : "SET") + ", Model: " + (config.model.isEmpty() ? "NOT SET" : config.model));
    }

    private void loadConfig() {
        Path configDir = Paths.get("config");
        if (!configDir.toFile().exists()) {
            configDir.toFile().mkdirs();
            LOGGER.info("[ChatBot] Created config directory: " + configDir.toAbsolutePath());
        }
        File configFile = configDir.resolve("chatbot.toml").toFile();
        LOGGER.info("[ChatBot] Loading config from: " + configFile.getAbsolutePath());
        config.load(configFile);
        applyConfig();
    }

    public static void applyConfig() {
        if (config != null && aiService != null) {
            aiService.setApiKey(config.apiKey);
            aiService.setModel(config.model);
            aiService.setApiType(config.apiType);
            aiService.setCustomUrl(config.customUrl);
            aiService.setAiName(config.aiName);
            aiService.setCmdBlacklist(config.cmdBlacklist);
            aiService.setCmdNormalWhitelist(config.cmdNormalWhitelist);

            // 构建/重建 CommandExecutor（依赖 config 中的白/黑名单和频率限制）
            CommandValidator validator = new CommandValidator(
                    config.cmdBlacklist, config.cmdNormalWhitelist);
            commandExecutor = new CommandExecutor(validator, config.cmdMaxPerMinute);

            if (config.aiEnabled && config.apiKey != null && !config.apiKey.isEmpty() && config.model != null && !config.model.isEmpty()) {
                LOGGER.info("[ChatBot] AI API configured: type=" + config.apiType + ", key=" + config.apiKey.substring(0, Math.min(8, config.apiKey.length())) + "..., model=" + config.model);
            } else {
                LOGGER.info("[ChatBot] AI disabled or missing configuration");
            }
            LOGGER.info("[ChatBot] Command executor ready: cmdEnabled=" + config.cmdEnabled
                    + ", blacklistSize=" + config.cmdBlacklist.split(",").length
                    + ", whitelistSize=" + config.cmdNormalWhitelist.split(",").length
                    + ", maxPerMinute=" + config.cmdMaxPerMinute);
        }
    }
}
