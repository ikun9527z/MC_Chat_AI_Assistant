# ChatBot Mod

一个为 Minecraft 1.21.1 NeoForge 服务器设计的 AI 聊天机器人模组。

## 功能特性

- ✅ **多AI服务支持**：自动识别并支持多种AI服务
- ✅ **实时对话**：玩家发送消息后，AI自动回复
- ✅ **异步处理**：AI请求在后台线程处理，不阻塞游戏
- ✅ **配置灵活**：通过配置文件轻松切换AI服务
- ✅ **详细日志**：完整的错误日志便于排查问题

## 支持的AI服务

| API类型 | 服务名称 | 官方网站 |
|---------|----------|----------|
| `ZHIPU` | 智谱GLM | https://open.bigmodel.cn/ |
| `VOLC` | 火山方舟（豆包） | https://console.volcengine.com/ark/ |
| `OPENAI` | OpenAI | https://platform.openai.com/ |
| `DEEPSEEK` | DeepSeek | https://platform.deepseek.com/ |
| `MINIMAX` | MiniMax | https://api.minimax.chat/ |
| `CUSTOM` | 自定义API | 自定义端点 |

## 安装步骤

1. 下载模组文件 `chatbot-1.0.0.jar`
2. 将文件放入服务器的 `mods/` 文件夹
3. 启动服务器，配置文件会自动生成在 `config/chatbot.toml`
4. 停止服务器，编辑配置文件
5. 重启服务器即可使用

## 配置文件说明

配置文件路径：`config/chatbot.toml`

```toml
# API Key（必填）
api_key = "your-api-key-here"

# 模型名称（必填，根据API类型选择）
model = "GLM-4-Flash-250414"

# API类型：ZHIPU, VOLC, OPENAI, DEEPSEEK, MINIMAX, CUSTOM
api_type = "ZHIPU"

# 自定义API地址（仅api_type=CUSTOM时需要）
custom_url = ""

# 是否启用AI回复
ai_enabled = true

# 回复延迟（毫秒）
reply_delay_ms = 0
```

### 各AI服务配置示例

#### 智谱GLM
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

#### 自定义API
```toml
api_key = "你的API密钥"
model = "你的模型名称"
api_type = "CUSTOM"
custom_url = "https://your-api-endpoint.com/v1/chat/completions"
```

## 使用方法

1. 在游戏中按 `T` 键打开聊天窗口
2. 发送任意消息
3. ChatBot 会自动回复你的消息

### 示例

```
<玩家> @AI 你好
[ChatBot] 你好！很高兴见到你！

<玩家> @AI 创造代码怎么开？
[ChatBot] 在我的世界中，要进入创造模式，你可以使用指令"/gamemode creative "
```

## 获取API Key

### 智谱GLM
1. 访问 https://open.bigmodel.cn/ 注册账号
2. 在"API Key管理"页面创建API Key
3. 模型广场选模型并复制模型id

### 火山方舟（豆包）
1. 访问 https://console.volcengine.com/ark/ 登录火山引擎
2. 完成实名认证
3. 在"模型广场"开通需要的模型
4. 在"API Key管理"创建API Key

## 常见问题

### Q: AI没有回复
**A:** 检查服务器日志中 `[ChatBot]` 的相关信息：
- `AI服务未启用`：检查配置文件 `ai_enabled` 是否为 `true`
- `API请求失败`：检查API Key和模型名称是否正确
- `无法连接`：检查服务器网络是否正常

### Q: 配置文件在哪里？
**A:** 服务器根目录下的 `config/chatbot.toml`

### Q: 修改配置后需要重启服务器吗？
**A:** 是的，需要重启服务器才能生效

### Q: 如何查看详细日志？
**A:** 在服务器日志中搜索 `[ChatBot]` 关键字

## 技术支持

如果遇到问题，请提供以下信息：
1. 服务器日志中 `[ChatBot]` 的相关内容
2. 配置文件内容（隐藏API Key的后几位）
3. 使用的AI服务类型

## 版本信息

- **模组版本**: 1.0.0
- **Minecraft版本**: 1.21.1
- **NeoForge版本**: 21.1.236

## 许可证

MIT License
