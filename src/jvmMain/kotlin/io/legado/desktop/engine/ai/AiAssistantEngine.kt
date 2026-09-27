package io.legado.desktop.engine.ai

import io.legado.desktop.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

object AiAssistantEngine {

    const val KEY_AI_PROVIDER = "ai_provider"
    const val KEY_AI_BASE_URL = "ai_base_url"
    const val KEY_AI_API_KEY = "ai_api_key"
    const val KEY_AI_MODEL = "ai_model"
    const val KEY_AI_TEMPERATURE = "ai_temperature"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Load AI configuration safely from local SQLite database
     */
    suspend fun loadConfig(): AiConfig = withContext(Dispatchers.IO) {
        val providerId = AppDatabase.getConfig(KEY_AI_PROVIDER, AiProvider.DEEPSEEK.id)
        val provider = AiProvider.fromId(providerId)
        val defaultUrl = provider.defaultBaseUrl
        val defaultModel = provider.defaultModel

        val baseUrl = AppDatabase.getConfig(KEY_AI_BASE_URL, defaultUrl).ifBlank { defaultUrl }
        val apiKey = AppDatabase.getConfig(KEY_AI_API_KEY, "")
        val model = AppDatabase.getConfig(KEY_AI_MODEL, defaultModel).ifBlank { defaultModel }
        val tempStr = AppDatabase.getConfig(KEY_AI_TEMPERATURE, "0.7")
        val temperature = tempStr.toFloatOrNull() ?: 0.7f

        AiConfig(
            provider = provider,
            baseUrl = baseUrl,
            apiKey = apiKey,
            model = model,
            temperature = temperature
        )
    }

    /**
     * Save AI configuration to local SQLite database (API key stored strictly locally)
     */
    suspend fun saveConfig(config: AiConfig) = withContext(Dispatchers.IO) {
        AppDatabase.setConfig(KEY_AI_PROVIDER, config.provider.id)
        AppDatabase.setConfig(KEY_AI_BASE_URL, config.baseUrl.trim())
        AppDatabase.setConfig(KEY_AI_API_KEY, config.apiKey.trim())
        AppDatabase.setConfig(KEY_AI_MODEL, config.model.trim())
        AppDatabase.setConfig(KEY_AI_TEMPERATURE, config.temperature.toString())
    }

    /**
     * Standardize and resolve full chat completions endpoint URL
     */
    fun resolveChatUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().removeSuffix("/")
        if (trimmed.isEmpty()) return ""
        return when {
            trimmed.endsWith("/chat/completions") -> trimmed
            trimmed.endsWith("/v1") -> "$trimmed/chat/completions"
            else -> "$trimmed/v1/chat/completions"
        }
    }

    /**
     * Test connection to configured AI endpoint and report latency
     */
    suspend fun testConnection(config: AiConfig): Result<String> = withContext(Dispatchers.IO) {
        val url = resolveChatUrl(config.baseUrl)
        if (url.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("接口 Base URL 不能为空"))
        }
        if (config.provider.isKeyRequired && config.apiKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("${config.provider.displayName} 必须配置 API Key"))
        }

        val testMessages = listOf(
            AiMessage(
                role = "user",
                content = "你好，请回复'连接成功'并注明当前模型名称。"
            )
        )

        val startTime = System.currentTimeMillis()
        try {
            val response = executeChatCompletion(config, testMessages, maxTokensOverride = 60)
            val latency = System.currentTimeMillis() - startTime
            Result.success("✅ 连接成功！(耗时 ${latency}ms)\n回复: $response")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Execute standard OpenAI Chat Completions HTTP POST request
     */
    suspend fun executeChatCompletion(
        config: AiConfig,
        messages: List<AiMessage>,
        maxTokensOverride: Int? = null
    ): String = withContext(Dispatchers.IO) {
        val url = resolveChatUrl(config.baseUrl)
        if (url.isBlank()) {
            throw IllegalArgumentException("接口 Base URL 不能为空，请先在 AI 设置中配置")
        }
        if (config.provider.isKeyRequired && config.apiKey.isBlank()) {
            throw IllegalArgumentException("未配置 API Key，请点击右上角⚙️图标完成大模型设置")
        }

        val effectiveModel = config.model.ifBlank { config.provider.defaultModel }
        val effectiveTokens = maxTokensOverride ?: config.maxTokens

        val requestJson = buildJsonObject {
            put("model", effectiveModel)
            put("messages", buildJsonArray {
                for (msg in messages) {
                    add(buildJsonObject {
                        put("role", msg.role)
                        put("content", msg.content)
                    })
                }
            })
            put("temperature", config.temperature)
            put("max_tokens", effectiveTokens)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = requestJson.toString().toRequestBody(mediaType)

        val requestBuilder = Request.Builder()
            .url(url)
            .post(requestBody)
            .header("Content-Type", "application/json")

        if (config.apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer ${config.apiKey.trim()}")
        }

        val request = requestBuilder.build()
        val response = httpClient.newCall(request).execute()

        response.use { resp ->
            val bodyString = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val errorMsg = try {
                    val root = json.parseToJsonElement(bodyString).jsonObject
                    val errorObj = root["error"]?.jsonObject
                    errorObj?.get("message")?.jsonPrimitive?.contentOrNull
                } catch (_: Exception) {
                    null
                } ?: "HTTP ${resp.code}: ${resp.message}"

                val tip = when (resp.code) {
                    401 -> "（提示：API Key 无效或未授权，请检查凭据）"
                    404 -> "（提示：接口地址未找到，请检查 Base URL）"
                    429 -> "（提示：触发调用频次限制或账户额度用尽）"
                    else -> ""
                }
                throw IOException("大模型请求失败: $errorMsg $tip")
            }

            try {
                val root = json.parseToJsonElement(bodyString).jsonObject
                val choices = root["choices"]?.jsonArray
                val firstChoice = choices?.firstOrNull()?.jsonObject
                val message = firstChoice?.get("message")?.jsonObject
                val content = message?.get("content")?.jsonPrimitive?.contentOrNull

                if (content.isNullOrBlank()) {
                    throw IOException("大模型返回内容为空")
                }
                content.trim()
            } catch (e: Exception) {
                if (e is IOException) throw e
                throw IOException("解析大模型响应失败: ${e.message}\n原始响应: ${bodyString.take(200)}")
            }
        }
    }

    /**
     * 1. 章节智能速读摘要
     */
    suspend fun summarizeChapter(
        chapterTitle: String,
        content: String,
        config: AiConfig
    ): String {
        val sampleText = content.take(6000) // Ensure prompt fits comfortably within context
        val messages = listOf(
            AiMessage(
                role = "system",
                content = """
                    你是一位精通中文通俗文学、网络小说与名著的文学助理。请对用户提供的章节内容进行凝练、深刻的速读摘要。
                    严格按照以下 Markdown 格式结构输出：
                    ### 📖 核心剧情
                    用 2~3 句话提炼本章最核心的情节走向与主角行动。
                    ### ⚡ 关键转折 (Key Events)
                    列出 2~4 个本章重大转折点、精彩交锋或决定性场景。
                    ### 💡 伏笔与悬念
                    指出本章留下的潜在悬念或角色意图。
                """.trimIndent()
            ),
            AiMessage(
                role = "user",
                content = "【章节名】$chapterTitle\n\n【正文节选】\n$sampleText"
            )
        )
        return executeChatCompletion(config, messages)
    }

    /**
     * 2. 划选文本深度释义
     */
    suspend fun explainSelection(
        selectedText: String,
        surroundingContext: String? = null,
        config: AiConfig
    ): String {
        val contextPart = if (!surroundingContext.isNullOrBlank()) {
            "\n【上下文语境】\n...${surroundingContext.take(500)}..."
        } else ""

        val messages = listOf(
            AiMessage(
                role = "system",
                content = """
                    你是一位中文文学、文言文与传统文化研究专家。用户在阅读时选中了特定词句，请你结合语境进行生动、通俗、精辟的深度释义。
                    格式要求：
                    - **通俗释义**：解释字面含义及白话用法；
                    - **出处与典故**（如有）：溯源历史典故、神话或古诗文名篇；
                    - **小说语境解读**：说明在当前玄幻/历史/网文语境下的特有含义或修辞效果。
                """.trimIndent()
            ),
            AiMessage(
                role = "user",
                content = "【选中词句】$selectedText$contextPart"
            )
        )
        return executeChatCompletion(config, messages)
    }

    /**
     * 3. 登场人物谱系梳理
     */
    suspend fun extractCharacters(
        chapterTitle: String,
        content: String,
        config: AiConfig
    ): String {
        val sampleText = content.take(6000)
        val messages = listOf(
            AiMessage(
                role = "system",
                content = """
                    请分析用户提供的章节文本，梳理本章登场或提及的重要人物脉络及相互关系。
                    格式要求：
                    - **主角处境**：主角身份、当前状态与目标；
                    - **登场/关键配角**：列出本章活跃人物，注明阵营立场、与主角的关系、本章关键言行；
                    - **势力/宗门关联**：若涉及门派、家族或势力纠葛，简要说明背景。
                """.trimIndent()
            ),
            AiMessage(
                role = "user",
                content = "【章节名】$chapterTitle\n\n【正文节选】\n$sampleText"
            )
        )
        return executeChatCompletion(config, messages)
    }

    /**
     * 4. 自由沉浸式对话
     */
    suspend fun chatWithContext(
        chapterTitle: String,
        content: String,
        history: List<AiMessage>,
        userQuestion: String,
        config: AiConfig
    ): String {
        val sampleText = content.take(4000)
        val systemPrompt = AiMessage(
            role = "system",
            content = """
                你是一位陪伴在读者身边的贴心 AI 读书助手。
                当前读者正在阅读《$chapterTitle》。
                以下是该章节的正文节选以供参考：
                ---
                $sampleText
                ---
                请基于上述小说语境和你的文学知识回答读者的疑问。回答要生动、有见地，若读者没有明确询问后续情节，请注意不要剧透未提及的内容。
            """.trimIndent()
        )

        val fullMessages = mutableListOf<AiMessage>()
        fullMessages.add(systemPrompt)
        fullMessages.addAll(history.takeLast(6)) // keep last 3 turns
        fullMessages.add(AiMessage(role = "user", content = userQuestion))

        return executeChatCompletion(config, fullMessages)
    }
}
