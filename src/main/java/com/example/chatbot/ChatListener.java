package com.example.chatbot;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ServerChatEvent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatListener {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @SubscribeEvent
    public void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        String message = event.getMessage().getString().trim();
        String playerName = player.getName().getString();

        if (!ChatBot.config.aiEnabled) {
            return;
        }

        String trigger = ChatBot.config.aiTrigger;
        if (!message.startsWith(trigger)) {
            return;
        }

        event.setCanceled(true);

        String aiMessage = message.substring(trigger.length()).trim();
        if (aiMessage.isEmpty()) {
            return;
        }

        executor.submit(() -> {
            String response = generateResponse(aiMessage, playerName);

            if (response != null) {
                String aiName = ChatBot.aiService.getAiName();
                sendChatMessage(player, "[" + aiName + "] " + response);
            }
        });
    }

    private void sendChatMessage(ServerPlayer player, String message) {
        Component chatComponent = Component.literal(message);
        player.sendSystemMessage(chatComponent);
    }

    private String generateResponse(String message, String playerName) {
        if (ChatBot.aiService == null || !ChatBot.aiService.isEnabled()) {
            ChatBot.LOGGER.warn("[ChatBot] AI service not enabled");
            return "AI服务未启用，请检查配置。";
        }

        String aiResponse = ChatBot.aiService.getResponse(playerName, message);
        if (aiResponse != null && !aiResponse.isEmpty()) {
            return aiResponse;
        }

        ChatBot.LOGGER.error("[ChatBot] AI request failed, could not get response");
        return "AI回答失败，请稍后重试。";
    }
}
