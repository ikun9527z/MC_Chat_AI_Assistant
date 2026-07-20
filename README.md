# ChatBot Mod

一个为 Minecraft 1.21.1 NeoForge 设计的 AI 聊天机器人模组。
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

<玩家> @AI 给我一个钻石（普通玩家）
[ChatBot] 抱歉，你没有权限让我执行 give 命令。请联系 OP 处理。

<玩家> @AI 给我一个钻石（OP 玩家）
[ChatBot] 已给你 1 个钻石
```


## 常见问题

### Q: AI 没有回复
**A:** 检查服务器日志中 `[ChatBot]` 的相关信息：
- `AI 服务未启用`：检查配置文件 `ai_enabled` 是否为 `true`
- `API 请求失败`：检查 API Key 和模型名称是否正确
- `无法连接`：检查服务器网络是否正常

### Q: 配置文件在哪里？
**A:** 服务器根目录下的 `config/chatbot.toml`

### Q: 修改配置后需要重启服务器吗？
**A:** 是的，需要重启服务器才能生效。

### Q: 如何查看详细日志？
**A:** 在服务器日志中搜索 `[ChatBot]` 关键字。

## 版本信息

- **模组版本**：1.0.0
- **Minecraft 版本**：1.21.1
- **NeoForge 版本**：21.1.236

## 许可证

MIT License
