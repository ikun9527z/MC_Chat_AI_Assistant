package com.example.chatbot;

import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 命令执行器：负责在主线程上执行 Minecraft 命令，捕获结果。
 *
 * 注意：Minecraft 命令系统非线程安全，execute() 必须在主线程上调用。
 * 调用方负责线程切换（在 ChatListener 中通过 server.execute 切到主线程）。
 */
public class CommandExecutor {

    private final CommandValidator validator;
    private final int maxCommandsPerMinute;
    private final Map<UUID, Deque<Long>> recentCommands = new ConcurrentHashMap<>();

    public CommandExecutor(CommandValidator validator, int maxCommandsPerMinute) {
        this.validator = validator;
        this.maxCommandsPerMinute = maxCommandsPerMinute;
    }

    /**
     * 执行 AI 输出的命令。
     * 必须在主线程上调用！
     */
    public CommandResult execute(ServerPlayer player, String rawCommand) {
        if (player == null) {
            return CommandResult.fail("NO_PLAYER", "玩家不存在");
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return CommandResult.fail("NO_SERVER", "服务器不可用");
        }

        // 1. 频率限制
        if (isRateLimited(player.getUUID())) {
            ChatBot.LOGGER.warn("[ChatBot] Rate limit exceeded for player: " + player.getName().getString());
            return CommandResult.fail("RATE_LIMITED", "操作过于频繁，每分钟最多 " + maxCommandsPerMinute + " 条命令");
        }

        // 2. 校验
        boolean isOp = server.getPlayerList().isOp(player.getGameProfile());
        CommandResult validation = validator.validate(isOp, rawCommand);
        if (!validation.success()) {
            return validation;
        }

        // 3. 规范化命令（与 validator 内一致）
        String command = rawCommand.trim();
        if (command.startsWith("/")) {
            command = command.substring(1).trim();
        }

        // 4. 构建命令源：以玩家身份 + 最高权限等级 + 注册结果回调
        //    AI 执行命令时始终使用 level 4（最高权限），权限管控由白名单在校验阶段完成。
        //    使用 withCallback(CommandResultCallback) 接收命令成功/失败结果，
        //    同时让 MC 自身的反馈机制把命令输出/错误发到玩家聊天框。
        int permissionLevel = 4;
        final boolean[] successHolder = {false};
        final int[] resultCodeHolder = {0};
        final boolean[] callbackCalled = {false};

        CommandResultCallback callback = (success, result) -> {
            successHolder[0] = success;
            resultCodeHolder[0] = result;
            callbackCalled[0] = true;
        };

        CommandSourceStack source = player.createCommandSourceStack()
                .withPermission(permissionLevel)
                .withCallback(callback);

        ChatBot.LOGGER.info("[ChatBot] Executing command for player " + player.getName().getString()
                + " (isOp=" + isOp + ", execLevel=4): " + command);

        // 5. 执行：使用 performPrefixedCommand 让 MC 走完整的执行上下文
        //    （1.21 引入的 ExecutionContext 机制），命令对其他玩家/实体的操作才能正常工作。
        //    命令的输出会通过 source 自动发到玩家聊天框；结果通过 callback 回传。
        try {
            server.getCommands().performPrefixedCommand(source, command);
            recordCommand(player.getUUID());

            if (callbackCalled[0]) {
                if (successHolder[0]) {
                    ChatBot.LOGGER.info("[ChatBot] Command succeeded, result code: " + resultCodeHolder[0]);
                    return CommandResult.ok("命令已执行");
                } else {
                    ChatBot.LOGGER.warn("[ChatBot] Command failed (callback reported failure)");
                    return CommandResult.fail("COMMAND_FAILED", "命令执行失败（MC 反馈失败）");
                }
            } else {
                // 回调未被调用（命令可能异步执行或被中断），但无异常，视为提交成功
                ChatBot.LOGGER.info("[ChatBot] Command submitted, no callback received");
                return CommandResult.ok("命令已提交执行");
            }
        } catch (Exception e) {
            ChatBot.LOGGER.error("[ChatBot] Command execution exception: " + e.getMessage());
            return CommandResult.fail("EXCEPTION", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    /**
     * 检查玩家是否超出频率限制。
     */
    private boolean isRateLimited(UUID playerId) {
        if (maxCommandsPerMinute <= 0) {
            return false;
        }
        Deque<Long> timestamps = recentCommands.get(playerId);
        if (timestamps == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        long cutoff = now - 60_000L;
        synchronized (timestamps) {
            timestamps.removeIf(t -> t < cutoff);
            return timestamps.size() >= maxCommandsPerMinute;
        }
    }

    /**
     * 记录一次命令执行时间戳。
     */
    private void recordCommand(UUID playerId) {
        if (maxCommandsPerMinute <= 0) {
            return;
        }
        Deque<Long> timestamps = recentCommands.computeIfAbsent(playerId, k -> new ArrayDeque<>());
        long now = System.currentTimeMillis();
        synchronized (timestamps) {
            timestamps.addLast(now);
            long cutoff = now - 60_000L;
            timestamps.removeIf(t -> t < cutoff);
        }
    }
}
