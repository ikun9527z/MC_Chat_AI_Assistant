# ChatBot Mod

一个为 Minecraft 1.21.1 NeoForge 服务器设计的 AI 聊天机器人模组。
让 NPC 不再只会说模板台词，而是真正能聊天、能帮你执行服务器命令的智能助手。

## 功能特性

- ✅ **多 AI 服务支持**：内置智谱 GLM、火山方舟、OpenAI、DeepSeek、MiniMax 等 6 种 API，自动识别适配
- ✅ **实时对话**：玩家在聊天框 @AI 即可触发对话，AI 异步处理不阻塞主线程
- ✅ **AI 命令执行**：玩家可以让 AI 帮忙执行服务器命令（tp、weather、time、give 等），AI 通过 `<cmd>` 标签协议输出命令
- ✅ **权限分级**：OP 与普通玩家分离权限模型，黑/白名单双重防护，防止 prompt injection 绕过
- ✅ **频率限制**：每玩家每分钟可执行命令数可配置，防止 AI 滥用
- ✅ **双轮对话**：AI 先输出意图和命令 → 系统执行命令 → AI 再根据执行结果向玩家总结
- ✅ **配置灵活**：所有功能通过 `config/chatbot.toml` 配置，无需重启代码即可调整

## 支持的 AI 服务

| API 类型 | 服务名称 | 官方网站 |
|---------|----------|----------|
| `ZHIPU` | 智谱 GLM | https://open.bigmodel.cn/ |
| `VOLC` | 火山方舟（豆包） | https://console.volcengine.com/ark/ |
| `OPENAI` | OpenAI | https://platform.openai.com/ |
| `DEEPSEEK` | DeepSeek | https://platform.deepseek.com/ |
| `MINIMAX` | MiniMax | https://api.minimax.chat/ |
| `CUSTOM` | 自定义 API | 自定义端点（兼容 OpenAI 协议即可） |

## 安装步骤

1. 下载模组文件 `chatbot-1.0.0.jar`
2. 将文件放入服务器的 `mods/` 文件夹
3. 启动服务器，配置文件会自动生成在 `config/chatbot.toml`
4. 停止服务器，编辑配置文件填入 API Key & model
5. 重启服务器即可使用

## 配置文件说明

配置文件路径：`config/chatbot.toml`

```toml
# ===== AI 服务配置 =====
# API Key（必填）
api_key = "your-api-key-here"

# 模型名称（必填，根据 API 类型选择）
model = "GLM-4-Flash-250414"

# API 类型：ZHIPU, VOLC, OPENAI, DEEPSEEK, MINIMAX, CUSTOM
api_type = "ZHIPU"

# 自定义 API 地址（仅 api_type=CUSTOM 时需要）
custom_url = ""

# 是否启用 AI 回复
ai_enabled = true

# 回复延迟（毫秒）
reply_delay_ms = 0

# AI 显示名称
ai_name = "ChatBot"

# 触发前缀（玩家聊天以此开头才会触发 AI）
ai_trigger = "@AI"

# ===== AI 命令执行 =====
# 是否启用 AI 命令执行功能
cmd_enabled = true

# 全局黑名单（所有人包括 OP 都不能让 AI 执行，逗号分隔）
cmd_blacklist = "stop,setblock"

# 普通玩家白名单（仅这些命令可被非 OP 玩家通过 AI 执行，逗号分隔）
cmd_normal_whitelist = "list,seed,spawn,home,help,me,tell,msg,w"

# 每玩家每分钟最大命令数（0 = 不限制）
cmd_max_per_minute = 10
```

### 各 AI 服务配置示例

#### 智谱 GLM
```toml
api_key = "你的智谱API Key"
model = "GLM-4-Flash-250414"
api_type = "ZHIPU"
```

#### 火山方舟（豆包）
```toml
api_key = "你的火山方舟API Key"
model = "doubao-seed-2-0-lite"
api_type = "VOLC"
```

#### OpenAI
```toml
api_key = "sk-你的OpenAI密钥"
model = "gpt-3.5-turbo"
api_type = "OPENAI"
```

#### DeepSeek
```toml
api_key = "你的DeepSeek密钥"
model = "deepseek-chat"
api_type = "DEEPSEEK"
```

#### MiniMax
```toml
api_key = "你的MiniMax密钥"
model = "abab5.5-chat"
api_type = "MINIMAX"
```

#### 自定义 API
```toml
api_key = "你的API密钥"
model = "你的模型名称"
api_type = "CUSTOM"
custom_url = "https://your-api-endpoint.com/v1/chat/completions"
```

## 使用方法

### 基础对话

1. 在游戏中按 `T` 键打开聊天窗口
2. 输入 `@AI 你的问题` 并发送
3. AI 会异步回复你的消息

### AI 命令执行

只要 `cmd_enabled = true`，玩家可以让 AI 帮忙执行服务器命令。AI 会在回复中嵌入 `<cmd>命令 参数</cmd>` 标签，系统自动解析并以最高权限（level 4）执行。

#### 示例对话

```
<玩家> @AI 你好
[ChatBot] 你好！很高兴见到你！有什么可以帮你的吗？

<玩家> @AI 在线多少人
[ChatBot] 我查一下在线玩家...
[ChatBot] 当前服务器有 3 位玩家在线：Steve、Alex、PanKa

<玩家> @AI 把我传到 Steve 身边
[ChatBot] 好的，正在传送你到 Steve 身边
[ChatBot] 已将你传送到 Steve 身边

<玩家> @AI 设为白天
[ChatBot] 好的，已将时间设为白天

<玩家> @AI 给我一个钻石（普通玩家）
[ChatBot] 抱歉，你没有权限让我执行 give 命令。请联系 OP 处理。

<玩家> @AI 给我一个钻石（OP 玩家）
[ChatBot] 已给你 1 个钻石
```

### 权限模型

| 玩家身份 | 能让 AI 执行的命令 | 执行权限等级 |
|---------|------------------|------------|
| OP | 除 `cmd_blacklist` 外的任意命令 | level 4（最高） |
| 普通玩家 | 仅 `cmd_normal_whitelist` 中的命令 | level 4（最高） |

**关键设计：** 通过校验后所有命令一律以 level 4（最高权限）执行，确保白名单中的命令能正常工作。例如普通玩家虽然自身权限是 level 0，但通过 AI 执行 `tp` 时会以 level 4 执行，否则会因为权限不足而失败。

### 命令权限配置示例

**让普通玩家只能查询信息，不能 tp：**
```toml
cmd_normal_whitelist = "list,seed,spawn,home,help,me,tell,msg,w"
```

**让普通玩家完全不能用 AI 执行命令：**
```toml
cmd_normal_whitelist = ""
```

**禁止任何人（含 OP）通过 AI 执行危险命令：**
```toml
cmd_blacklist = "stop,restart,op,deop,ban,ban-ip,pardon,pardon-ip,whitelist,kick,reload,save-all,save-off,save-on,defaultgamemode,publish"
```

**关闭 AI 命令执行功能：**
```toml
cmd_enabled = false
```

## 获取 API Key

### 智谱 GLM
1. 访问 https://open.bigmodel.cn/ 注册账号
2. 完成实名认证
3. 在"API Key 管理"页面创建 API Key

### 火山方舟（豆包）
1. 访问 https://console.volcengine.com/ark/ 登录火山引擎
2. 完成实名认证
3. 在"模型广场"开通需要的模型
4. 在"API Key 管理"创建 API Key

### DeepSeek
1. 访问 https://platform.deepseek.com/ 注册账号
2. 充值后创建 API Key

## 常见问题

### Q: AI 没有回复
**A:** 检查服务器日志中 `[ChatBot]` 的相关信息：
- `AI 服务未启用`：检查配置文件 `ai_enabled` 是否为 `true`
- `API 请求失败`：检查 API Key 和模型名称是否正确
- `无法连接`：检查服务器网络是否正常

### Q: 玩家发消息后 AI 没反应
**A:** 检查消息是否以触发前缀开头（默认 `@AI`）。可在配置文件中修改 `ai_trigger`。

### Q: AI 命令执行失败
**A:** 检查日志：
- `Command not in whitelist for non-OP`：普通玩家试图执行白名单外的命令
- `Command blocked by global blacklist`：命令在全局黑名单中
- `Rate limit exceeded`：超出每分钟频率限制，调高 `cmd_max_per_minute` 或稍后重试
- `Command failed (callback reported failure)`：命令通过了校验但 MC 执行时报错（语法错误、参数无效等）

### Q: 配置文件在哪里？
**A:** 服务器根目录下的 `config/chatbot.toml`

### Q: 修改配置后需要重启服务器吗？
**A:** 是的，需要重启服务器才能生效。

### Q: 如何查看详细日志？
**A:** 在服务器日志中搜索 `[ChatBot]` 关键字。

### Q: 普通玩家用 AI 执行 tp 还是失败怎么办？
**A:** 检查以下几点：
1. `cmd_normal_whitelist` 中是否包含 `tp`（默认包含）
2. `cmd_blacklist` 中是否误加了 `tp`
3. 是否触发了频率限制（默认每分钟 10 条）
4. 玩家是否真的是非 OP（OP 走黑名单逻辑，能执行除黑名单外任意命令）

## 技术架构

### 核心组件

| 文件 | 职责 |
|------|------|
| [ChatBot.java](file:///d:/PanKa/Downloads/MC_Chat_AI/src/main/java/com/example/chatbot/ChatBot.java) | 主类，管理生命周期、配置加载、组件装配 |
| [ChatBotConfig.java](file:///d:/PanKa/Downloads/MC_Chat_AI/src/main/java/com/example/chatbot/ChatBotConfig.java) | 配置文件加载与持久化 |
| [ChatListener.java](file:///d:/PanKa/Downloads/MC_Chat_AI/src/main/java/com/example/chatbot/ChatListener.java) | 聊天事件监听，解析 `<cmd>` 标签，主线程切换 |
| [AiService.java](file:///d:/PanKa/Downloads/MC_Chat_AI/src/main/java/com/example/chatbot/AiService.java) | AI API 调用，多服务商适配，system prompt 构建 |
| [CommandExecutor.java](file:///d:/PanKa/Downloads/MC_Chat_AI/src/main/java/com/example/chatbot/CommandExecutor.java) | 命令执行，频率限制，结果回调 |
| [CommandValidator.java](file:///d:/PanKa/Downloads/MC_Chat_AI/src/main/java/com/example/chatbot/CommandValidator.java) | 命令校验，黑/白名单过滤 |
| [CommandResult.java](file:///d:/PanKa/Downloads/MC_Chat_AI/src/main/java/com/example/chatbot/CommandResult.java) | 命令执行结果 record |

### AI 命令执行流程

```
玩家发消息 @AI 帮我 tp 到 Steve 身边
  │
  ▼
ChatListener 接收聊天事件
  │
  ▼
异步线程调用 AiService 第一轮对话
  │  system prompt 注入：玩家身份、白名单、协议规则
  ▼
AI 返回："好的，正在传送你 <cmd>tp Steve</cmd>"
  │
  ▼
ChatListener 解析 <cmd> 标签
  │
  ▼
server.execute() 切到主线程 → CommandExecutor.execute()
  │
  ▼
CommandValidator.validate()  ← 黑名单 + 白名单校验
  │
  ▼
player.createCommandSourceStack().withPermission(4)
  │
  ▼
performPrefixedCommand() 执行命令（MC 完整上下文）
  │
  ▼
通过 callback 回收执行结果
  │
  ▼
异步线程调用 AiService 第二轮对话
  │  告知 AI 执行结果（成功/失败 + 反馈文本）
  ▼
AI 返回："已将你传送到 Steve 身边"
  │
  ▼
玩家收到最终回复
```

### 线程模型

- **主线程**：聊天事件接收、命令执行（MC 命令系统非线程安全）
- **异步线程**：AI API 调用（HTTP 请求）
- **线程切换**：通过 `MinecraftServer.execute()` + `CountDownLatch(5s)` 从异步线程切回主线程执行命令

## 技术支持

如果遇到问题，请提供以下信息：
1. 服务器日志中 `[ChatBot]` 的相关内容
2. 配置文件内容（隐藏 API Key 的后几位）
3. 使用的 AI 服务类型
4. 复现步骤

## 版本信息

- **模组版本**：1.0.0
- **Minecraft 版本**：1.21.1
- **NeoForge 版本**：21.1.236

## 许可证

MIT License
