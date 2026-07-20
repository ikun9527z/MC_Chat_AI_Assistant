package com.example.chatbot;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ServerChatEvent;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatListener {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /** 解析 AI 回复中的 <cmd>...</cmd> 标签 */
    private static final Pattern CMD_PATTERN = Pattern.compile("<cmd>(.*?)</cmd>", Pattern.DOTALL);

    /** 主线程命令执行超时时间（秒） */
    private static final long COMMAND_TIMEOUT_SECONDS = 5;

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

        executor.submit(() -> handleAiMessage(player, playerName, aiMessage));
    }

    private void handleAiMessage(ServerPlayer player, String playerName, String aiMessage) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            sendChatMessage(player, "[ChatBot] 服务器不可用。");
            return;
        }

        boolean isOp = server.getPlayerList().isOp(player.getGameProfile());
        String aiName = ChatBot.aiService.getAiName();

        // 第一轮：AI 生成回复（可能含 <cmd> 标签）
        String firstResponse = generateFirstResponse(playerName, isOp, aiMessage);
        if (firstResponse == null || firstResponse.isEmpty()) {
            sendChatMessage(player, "[ChatBot] AI 回答失败，请稍后重试。");
            return;
        }

        // 解析 <cmd>...</cmd>
        Matcher matcher = CMD_PATTERN.matcher(firstResponse);
        if (!matcher.find()) {
            // 无命令标签，按原逻辑直接回复
            sendChatMessage(player, "[" + aiName + "] " + firstResponse);
            return;
        }

        String command = matcher.group(1).trim();
        ChatBot.LOGGER.info("[ChatBot] Parsed command from AI response: " + command);

        // 检测是否有多个 <cmd> 标签（只执行第一个）
        if (matcher.find()) {
            ChatBot.LOGGER.warn("[ChatBot] AI response contains multiple <cmd> tags, only the first will be executed: "
                    + matcher.group(1).trim());
        }

        // 全局开关检查
        if (!ChatBot.config.cmdEnabled) {
            ChatBot.LOGGER.info("[ChatBot] Command execution is disabled (cmdEnabled=false), sending AI response as-is");
            sendChatMessage(player, "[" + aiName + "] " + firstResponse);
            return;
        }

        // 把 <cmd> 标签外的文本作为"先发回复"立即发玩家（即时反馈）
        String preText = CMD_PATTERN.matcher(firstResponse).replaceAll("").trim();
        if (!preText.isEmpty()) {
            sendChatMessage(player, "[" + aiName + "] " + preText);
        }

        // 切回主线程执行命令（MC 命令系统非线程安全）
        CommandResult[] holder = new CommandResult[1];
        CountDownLatch latch = new CountDownLatch(1);
        server.execute(() -> {
            try {
                holder[0] = ChatBot.commandExecutor.execute(player, command);
            } catch (Throwable t) {
                ChatBot.LOGGER.error("[ChatBot] Unexpected error during command execution: " + t.getMessage(), t);
                holder[0] = CommandResult.fail("EXCEPTION", t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
            } finally {
                latch.countDown();
            }
        });

        try {
            if (!latch.await(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                sendChatMessage(player, "[ChatBot] 命令执行超时，请稍后重试。");
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sendChatMessage(player, "[ChatBot] 命令执行被中断。");
            return;
        }

        CommandResult result = holder[0];
        if (result == null) {
            sendChatMessage(player, "[ChatBot] 命令执行失败：未知错误。");
            return;
        }

        // 第二轮：让 AI 总结结果
        String summary = ChatBot.aiService.getResponseWithFeedback(
                playerName, isOp, aiMessage, firstResponse, command, result);

        if (summary != null && !summary.isEmpty()) {
            sendChatMessage(player, "[" + aiName + "] " + summary);
        } else {
            // 第二轮失败 fallback：直接把结果文本发玩家
            ChatBot.LOGGER.warn("[ChatBot] Second-round AI call failed, sending raw result as fallback");
            sendChatMessage(player, "[" + aiName + "] " + result.toFeedbackText());
        }
    }

    private void sendChatMessage(ServerPlayer player, String message) {
        Component chatComponent = Component.literal(message);
        player.sendSystemMessage(chatComponent);
    }

    private String generateFirstResponse(String playerName, boolean isOp, String message) {
        if (ChatBot.aiService == null || !ChatBot.aiService.isEnabled()) {
            ChatBot.LOGGER.warn("[ChatBot] AI service not enabled");
            return "AI服务未启用，请检查配置。";
        }

        String aiResponse = ChatBot.aiService.getResponse(playerName, isOp, message);
        if (aiResponse != null && !aiResponse.isEmpty()) {
            return aiResponse;
        }

        ChatBot.LOGGER.error("[ChatBot] AI request failed, could not get response");
        return null;
    }
}
