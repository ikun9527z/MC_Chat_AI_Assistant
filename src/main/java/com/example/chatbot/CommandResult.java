package com.example.chatbot;

/**
 * 命令执行结果数据类。
 * success: 命令是否执行成功
 * output:  命令输出或简短描述（成功时可为空字符串，失败时为失败原因）
 * errorCode: 失败时的错误代码（如 GLOBAL_BLACKLIST, NOT_IN_WHITELIST, TP_RESTRICTED,
 *            RATE_LIMITED, COMMAND_FAILED, EXCEPTION, TIMEOUT）
 */
public record CommandResult(boolean success, String output, String errorCode) {

    /**
     * 成功工厂方法（无输出）。
     * 注意：方法名 ok() 避免与 record 自动生成的 boolean success() 存取方法冲突。
     */
    public static CommandResult ok() {
        return new CommandResult(true, "", "");
    }

    /** 成功工厂方法（带输出） */
    public static CommandResult ok(String output) {
        return new CommandResult(true, output == null ? "" : output, "");
    }

    /** 失败工厂方法 */
    public static CommandResult fail(String errorCode, String message) {
        return new CommandResult(false, message == null ? "" : message, errorCode);
    }

    /**
     * 生成给 AI 第二轮总结的反馈文本。
     */
    public String toFeedbackText() {
        if (success) {
            if (output == null || output.isEmpty()) {
                return "[命令执行成功]";
            }
            return "[命令执行成功] 输出: " + output;
        }
        String reason = output == null || output.isEmpty() ? errorCode : output;
        return "[命令执行失败] 原因: " + reason;
    }
}
