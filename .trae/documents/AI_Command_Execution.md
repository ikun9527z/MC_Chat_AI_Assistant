# AI 命令执行功能实现方案

## Context

当前 chatbot mod 仅支持"玩家 @AI 提问 → AI 文字回复"的单轮对话。用户希望扩展让 AI 能向服务器输入指令，并区分权限：

- **OP 玩家**：可控制 AI 执行任意命令（黑名单除外），命令以 level 4（最高）权限执行
- **普通玩家**：只能让 AI 使用 TP（仅传自身）和服务器信息查询类命令

由于项目使用多种 AI API（智谱/火山/OpenAI/DeepSeek/MiniMax），不依赖 function calling，采用**标记解析法**：AI 在回复中嵌入 `<cmd>命令 参数</cmd>`，由代码解析并执行。为提升体验采用**双轮对话**：第一轮 AI 给出意图+命令；执行后第二轮 AI 总结结果。

所有权限校验在**代码层强制**（不依赖 AI 自觉），防御 prompt injection。

---

## 改动文件清单

### 新增 3 个文件

| 文件 | 职责 |
|------|------|
| `CommandResult.java` | 命令执行结果 record（success/output/errorCode） |
| `CommandValidator.java` | 白名单/黑名单/TP 限制校验，代码层强制 |
| `CommandExecutor.java` | 调用 MC CommandDispatcher 执行命令，频率限制 |

### 修改 4 个文件

| 文件 | 改动要点 |
|------|---------|
| `ChatBotConfig.java` | 新增 5 个配置项 |
| `ChatBot.java` | `applyConfig` 把新配置传给 `AiService` |
| `AiService.java` | 重构 system prompt（按权限注入），新增 `getResponseWithFeedback` |
| `ChatListener.java` | 解析 `<cmd>` 标签，调度命令执行+第二轮 AI |

---

## 实现细节

### 1. 配置项新增（`ChatBotConfig.java`）

```java
public boolean cmdEnabled = true;
public String cmdBlacklist = "stop,restart,op,deop,ban,ban-ip,pardon,pardon-ip,whitelist,kick,reload,save-all,save-off,save-on,defaultgamemode,publish";
public String cmdNormalWhitelist = "tp,teleport,list,seed,difficulty,weather,time,gamerule,spawn,home,help,me,tell,msg,w";
public boolean tpRestrictToSelf = true;
public int cmdMaxPerMinute = 10;
```

`load()` 和 `save()` 中各加几个 `else if` 分支，与现有 TOML KV 风格一致。

### 2. `CommandResult.java`（record，最简单）

```java
public record CommandResult(boolean success, String output, String errorCode) {
    public static CommandResult success(String output) { ... }
    public static CommandResult fail(String code, String msg) { ... }
    public String toFeedbackText() { ... }  // 给 AI 第二轮的反馈文本
}
```

### 3. `CommandValidator.java`（安全核心）

校验流程：
1. 规范化：trim、去掉前导 `/`
2. 提取基础命令名（`split("\\s+")[0].toLowerCase()`）
3. **全局黑名单**命中 → fail（任何身份都禁用 `stop/op/ban/kick/...`）
4. 判断 isOp（`server.getPlayerList().isOp(player.getGameProfile())`）
5. 非 OP 且不在白名单 → fail
6. 非 OP 且是 tp/teleport → `checkTpSelfOnly()`：`parts[1]` 必须是 `@s` 或玩家名，拒绝 `@a/@e/@p/@r`
7. 通过 → success

### 4. `CommandExecutor.java`

```java
public CommandResult execute(ServerPlayer player, String rawCommand) {
    // 1. 频率限制（每玩家 UUID 一个 Deque<Long>，清掉 60s 前时间戳）
    // 2. validator.validate() 失败直接返回
    // 3. 计算 level：OP=4，非 OP=0
    // 4. CommandSourceStack source = player.createCommandSourceStack()
    //        .withPermission(level);  // 不抑制输出，让玩家直接看到命令反馈
    // 5. server.getCommands().performPrefixedCommand(source, rawCommand);
    //    catch CommandSyntaxException → fail(message)
    //    result > 0 成功，否则失败
    // 6. 记录时间戳，返回 CommandResult
}
```

**线程要求**：必须在主线程执行（MC 命令系统非线程安全）。

### 5. `AiService.java` 改造

**重构 system prompt**：抽成 `buildSystemPrompt(playerName, isOp)`，根据权限注入不同内容：
- OP：可执行大部分命令，但显式列出黑名单
- 普通玩家：只能用白名单命令，TP 目标必须是自己
- 命令协议：`<cmd>命令 参数</cmd>`，每次最多 1 个
- 提供真实玩家名的示例

**改造 `getResponse` 签名**：增加 `isOp` 参数
```java
public String getResponse(String playerName, boolean isOp, String message)
```

**新增 `getResponseWithFeedback`**：第二轮 AI 总结
```java
public String getResponseWithFeedback(
    String playerName, boolean isOp,
    String originalUserMessage, String aiFirstResponse,
    String command, CommandResult result
)
// messages: [system, user, assistant, user(系统反馈+请总结)]
```

**抽取 HTTP 逻辑**为 `private String sendChatRequest(JsonObject body)`，让两个方法共用。

### 6. `ChatListener.java` 改造（核心）

```java
private static final Pattern CMD_PATTERN = Pattern.compile("<cmd>(.*?)</cmd>", Pattern.DOTALL);

// onChat 的 executor.submit 块中：
executor.submit(() -> {
    boolean isOp = player.getServer().getPlayerList().isOp(player.getGameProfile());
    
    // 第一轮：AI 生成（可能含 <cmd>）
    String firstResponse = ChatBot.aiService.getResponse(playerName, isOp, aiMessage);
    if (firstResponse == null || firstResponse.isEmpty()) {
        sendChatMessage(player, "[ChatBot] AI 回答失败，请稍后重试。");
        return;
    }
    
    // 解析 <cmd>
    Matcher m = CMD_PATTERN.matcher(firstResponse);
    if (!m.find()) {
        sendChatMessage(player, "[" + aiName + "] " + firstResponse);  // 无命令，原逻辑
        return;
    }
    
    String command = m.group(1).trim();
    if (!ChatBot.config.cmdEnabled) {
        sendChatMessage(player, "[" + aiName + "] " + firstResponse);  // 全局开关关闭
        return;
    }
    
    // 把 <cmd> 标签外的文本先发玩家（即时反馈）
    String preText = m.replaceAll("").trim();
    if (!preText.isEmpty()) sendChatMessage(player, "[" + aiName + "] " + preText);
    
    // 切回主线程执行命令
    CommandResult[] holder = new CommandResult[1];
    CountDownLatch latch = new CountDownLatch(1);
    player.getServer().execute(() -> {
        holder[0] = commandExecutor.execute(player, command);
        latch.countDown();
    });
    if (!latch.await(5, TimeUnit.SECONDS)) {
        sendChatMessage(player, "[ChatBot] 命令执行超时。");
        return;
    }
    
    CommandResult result = holder[0];
    
    // 第二轮：AI 总结
    String summary = ChatBot.aiService.getResponseWithFeedback(
        playerName, isOp, aiMessage, firstResponse, command, result);
    if (summary != null && !summary.isEmpty()) {
        sendChatMessage(player, "[" + aiName + "] " + summary);
    } else {
        sendChatMessage(player, "[ChatBot] " + result.toFeedbackText());  // fallback
    }
});
```

### 7. `ChatBot.java` 改动

`applyConfig` 中把新配置传给 `AiService`：
```java
aiService.setCmdBlacklist(config.cmdBlacklist);
aiService.setCmdNormalWhitelist(config.cmdNormalWhitelist);
```

`CommandExecutor` 作为 static 字段（类似 `aiService`），便于配置热更新。

---

## 错误处理矩阵

| 场景 | 处理 |
|------|------|
| AI 第一轮失败/空 | 发"AI 回答失败，请稍后重试。" |
| `<cmd>` 未闭合 | 正则只匹配完整闭合，未闭合忽略，原文发玩家 |
| 多个 `<cmd>` | 只执行第一个，日志 warn |
| 命令校验失败（白/黑名单/TP） | CommandResult.fail，进入第二轮让 AI 解释"无权限" |
| 命令执行异常 | catch，message 作为失败原因 |
| 主线程 5s 超时 | 发"命令执行超时" |
| 第二轮 AI 失败 | fallback：直接发 `CommandResult.toFeedbackText()` |
| 频率限制触发 | CommandResult.fail("RATE_LIMITED")，进入第二轮 |
| `cmdEnabled = false` | 即使有 `<cmd>` 也不执行，原文发玩家 |

---

## 安全设计

1. **代码层强制**：所有权限校验在 `CommandValidator`，不依赖 AI 自觉
2. **白名单优先**：普通玩家默认拒绝，仅白名单命令允许
3. **TP 限制硬编码**：`parts[1]` 必须是 `@s` 或玩家名；拒绝 `@a/@e/@p/@r`
4. **全局黑名单**：`stop/op/ban/kick/reload/save-*` 等危险命令任何身份都禁
5. **单条命令限制**：每条 AI 回复最多执行 1 个命令
6. **频率限制**：每玩家每分钟 N 条（默认 10）
7. **OP 身份服务端校验**：`server.getPlayerList().isOp(gameProfile)`，不信客户端
8. **主线程执行**：`server.execute` + `CountDownLatch(5s)`

---

## 验证方案（端到端测试矩阵）

### 功能测试
| 用例 | 玩家 | 输入 | 预期 |
|------|------|------|------|
| T1 | OP | "给我一个钻石" | 执行 `/give OP diamond 1`，玩家收到钻石 |
| T2 | OP | "把时间设为白天" | 执行 `/time set day` |
| T3 | 普通玩家 | "在线多少人" | 执行 `/list`，AI 总结人数 |
| T4 | 普通玩家 | "把我传到家" | 执行 `/tp 玩家 home` |
| T5 | 普通玩家 | "给我钻石" | AI 解释无权限，无命令执行 |
| T6 | 任意 | "你好" | 普通聊天，无命令 |

### 安全测试
| 用例 | 玩家 | 场景 | 预期 |
|------|------|------|------|
| S1 | OP | "关闭服务器" | AI/代码拒绝（黑名单） |
| S2 | OP | "给 Steve OP" | 拒绝（黑名单 op） |
| S3 | 普通玩家 | "踢掉 Steve" | 拒绝（不在白名单） |
| S4 | 普通玩家 | "把 Steve 传到我这里" | 代码层拒绝（TP source 非 @s） |
| S5 | 普通玩家 | AI 输出 `<cmd>give Player diamond</cmd>` | 代码层拒绝（不在白名单） |
| S6 | 任意 | 短时间发 11 条命令 | 第 11 条被频率限制 |

### 部署验证步骤
1. `./gradlew build` 生成 `chatbot-1.0.0.jar`
2. 复制 jar 到服务器 `mods/` 目录
3. 启动服务器，确认日志 `[ChatBot] ChatBot initialized!`
4. 在游戏内分别用 OP 和普通玩家身份按测试矩阵验证
5. 检查 `config/chatbot.toml` 是否生成新配置项

---

## 实现顺序

1. `CommandResult.java`（最简单）
2. `CommandValidator.java`（不依赖 MC API）
3. `CommandExecutor.java`（依赖 MC + Validator）
4. `ChatBotConfig.java` 加配置 + `ChatBot.java` 传递
5. `AiService.java` 改造 prompt + `getResponseWithFeedback`
6. `ChatListener.java` 集成（解析 + 调度 + 双轮）
7. 编译验证 + 部署测试
