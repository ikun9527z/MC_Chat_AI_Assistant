package com.example.chatbot;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 命令校验器：在代码层强制权限规则，防止 AI 被 prompt injection 绕过。
 *
 * 校验流程：
 * 1. 规范化（trim、去掉前导 /）
 * 2. 全局黑名单命中 → 拒绝（任何身份都禁）
 * 3. 非 OP 且不在白名单 → 拒绝
 *
 * 命令权限完全由黑/白名单决定：
 * - 黑名单中的命令，任何人都不能让 AI 执行（含 OP）
 * - OP 可以执行黑名单外的任意命令
 * - 普通玩家只能执行白名单中的命令（包括 tp/teleport 等所有命令均靠白名单控制）
 */
public class CommandValidator {

    /**
     * 黑名单默认值：空 Set（OP 玩家默认可让 AI 执行任何命令，包括 stop/op/ban 等危险命令）。
     * 管理员如需限制 OP 可执行的命令，可在 chatbot.toml 的 cmd_blacklist 中显式添加。
     */
    private static final Set<String> DEFAULT_BLACKLIST = Set.of();

    /**
     * 白名单默认值：普通玩家可执行查询类、聊天类、回家类命令。
     * 不包含 give/gamemode/kill 等影响游戏状态的命令。
     */
    private static final Set<String> DEFAULT_WHITELIST = Set.of(
            "list", "seed", "spawn", "home", "help", "me", "tell", "msg", "w"
    );

    private final Set<String> blacklist;
    private final Set<String> whitelist;

    public CommandValidator(String blacklistCsv, String whitelistCsv) {
        // 黑名单：空时回退默认值（防止管理员误清空导致 stop 等危险命令被允许）
        this.blacklist = parseCsvOrDefault(blacklistCsv, DEFAULT_BLACKLIST);
        // 白名单：空时返回空 Set（默认普通玩家无命令权限，符合"默认拒绝"原则）
        this.whitelist = parseCsv(whitelistCsv);
    }

    /**
     * 校验命令。
     *
     * @param isOp       玩家是否为 OP
     * @param rawCommand AI 输出的原始命令字符串
     * @return CommandResult.success() 表示通过；CommandResult.fail(...) 表示拒绝
     */
    public CommandResult validate(boolean isOp, String rawCommand) {
        if (rawCommand == null || rawCommand.isBlank()) {
            return CommandResult.fail("EMPTY_COMMAND", "命令为空");
        }

        String command = rawCommand.trim();
        // 去掉 AI 可能多加的前导 /
        if (command.startsWith("/")) {
            command = command.substring(1).trim();
        }
        if (command.isEmpty()) {
            return CommandResult.fail("EMPTY_COMMAND", "命令为空");
        }

        String[] parts = command.split("\\s+");
        String baseCommand = parts[0].toLowerCase();

        // 1. 全局黑名单（任何身份都禁）
        if (blacklist.contains(baseCommand)) {
            ChatBot.LOGGER.warn("[ChatBot] Command blocked by global blacklist: " + baseCommand);
            return CommandResult.fail("GLOBAL_BLACKLIST",
                    "命令 '" + baseCommand + "' 被全局禁止，任何身份都不能执行");
        }

        // 2. 非 OP 走白名单
        if (!isOp && !whitelist.contains(baseCommand)) {
            ChatBot.LOGGER.warn("[ChatBot] Command not in whitelist for non-OP: " + baseCommand);
            return CommandResult.fail("NOT_IN_WHITELIST",
                    "普通玩家无法让 AI 执行命令 '" + baseCommand + "'，仅允许: " + String.join(", ", whitelist));
        }

        return CommandResult.ok();
    }

    private Set<String> parseCsvOrDefault(String csv, Set<String> defaultSet) {
        if (csv == null || csv.isBlank()) {
            return Collections.unmodifiableSet(new HashSet<>(defaultSet));
        }
        Set<String> result = Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .collect(Collectors.toCollection(HashSet::new));
        return Collections.unmodifiableSet(result);
    }

    /**
     * 解析 CSV 字符串为 Set；空字符串/null 时返回空 Set（不回退默认值）。
     * 用于白名单：管理员把白名单设为空表示禁止所有命令。
     */
    private Set<String> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return Collections.emptySet();
        }
        Set<String> result = Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .collect(Collectors.toCollection(HashSet::new));
        return Collections.unmodifiableSet(result);
    }
}
