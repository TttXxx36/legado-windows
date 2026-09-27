package io.legado.desktop.engine.ai

import kotlinx.serialization.Serializable

enum class AiProvider(
    val id: String,
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val description: String,
    val isKeyRequired: Boolean = true
) {
    DEEPSEEK(
        id = "deepseek",
        displayName = "DeepSeek",
        defaultBaseUrl = "https://api.deepseek.com",
        defaultModel = "deepseek-chat",
        description = "高性价比、超强中文小说理解与剧情推演",
        isKeyRequired = true
    ),
    QWEN(
        id = "qwen",
        displayName = "通义千问 (DashScope)",
        defaultBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
        defaultModel = "qwen-plus",
        description = "阿里巴巴大模型，中文修辞、网文情境与文学理解极佳",
        isKeyRequired = true
    ),
    MOONSHOT(
        id = "moonshot",
        displayName = "月之暗面 (Kimi)",
        defaultBaseUrl = "https://api.moonshot.cn/v1",
        defaultModel = "moonshot-v1-8k",
        description = "擅长长文本阅读与精细脉络分析",
        isKeyRequired = true
    ),
    OPENAI(
        id = "openai",
        displayName = "OpenAI",
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-4o-mini",
        description = "国际主流标准接口",
        isKeyRequired = true
    ),
    OLLAMA(
        id = "ollama",
        displayName = "本地 Ollama",
        defaultBaseUrl = "http://localhost:11434/v1",
        defaultModel = "qwen2.5:7b",
        description = "本地离线私有化运行，无需网络与 API Key，100% 隐私",
        isKeyRequired = false
    ),
    CUSTOM(
        id = "custom",
        displayName = "自定义兼容源",
        defaultBaseUrl = "",
        defaultModel = "",
        description = "支持任意 OpenAI 规范兼容中转站或自建大模型服务",
        isKeyRequired = true
    );

    companion object {
        fun fromId(id: String): AiProvider {
            return values().firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DEEPSEEK
        }
    }
}

@Serializable
data class AiConfig(
    val provider: AiProvider = AiProvider.DEEPSEEK,
    val baseUrl: String = "https://api.deepseek.com",
    val apiKey: String = "",
    val model: String = "deepseek-chat",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 2000
)

@Serializable
data class AiMessage(
    val role: String, // system, user, assistant
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class AiActionType(val displayName: String, val emoji: String) {
    SUMMARY("章节速读", "⚡"),
    EXPLAIN("划词释义", "🔍"),
    CHARACTERS("人物谱系", "👥"),
    CHAT("自由问答", "💬")
}
