package io.legado.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.desktop.engine.ai.AiAssistantEngine
import io.legado.desktop.engine.ai.AiConfig
import io.legado.desktop.engine.ai.AiProvider
import io.legado.desktop.ui.theme.LegadoIcons
import kotlinx.coroutines.launch

@Composable
fun AiSettingsDialog(
    initialConfig: AiConfig? = null,
    onDismissRequest: () -> Unit,
    onSaved: (AiConfig) -> Unit
) {
    val scope = rememberCoroutineScope()

    var selectedProvider by remember { mutableStateOf(initialConfig?.provider ?: AiProvider.DEEPSEEK) }
    var baseUrl by remember { mutableStateOf(initialConfig?.baseUrl ?: selectedProvider.defaultBaseUrl) }
    var apiKey by remember { mutableStateOf(initialConfig?.apiKey ?: "") }
    var model by remember { mutableStateOf(initialConfig?.model ?: selectedProvider.defaultModel) }
    var temperature by remember { mutableStateOf(initialConfig?.temperature ?: 0.7f) }
    var showApiKey by remember { mutableStateOf(false) }

    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testSuccess by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    LegadoIcons.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text("AI 智能大模型服务配置", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .width(520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Provider Selection
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("推荐服务商预设", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(AiProvider.DEEPSEEK, AiProvider.QWEN, AiProvider.MOONSHOT).forEach { provider ->
                            val isSelected = selectedProvider == provider
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedProvider = provider
                                    baseUrl = provider.defaultBaseUrl
                                    model = provider.defaultModel
                                    testResult = null
                                },
                                label = { Text(provider.displayName, fontSize = 12.sp) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(AiProvider.OLLAMA, AiProvider.OPENAI, AiProvider.CUSTOM).forEach { provider ->
                            val isSelected = selectedProvider == provider
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedProvider = provider
                                    if (provider != AiProvider.CUSTOM) {
                                        baseUrl = provider.defaultBaseUrl
                                        model = provider.defaultModel
                                    }
                                    testResult = null
                                },
                                label = { Text(provider.displayName, fontSize = 12.sp) }
                            )
                        }
                    }
                    Text(
                        text = selectedProvider.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider()

                // 2. Base URL
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = {
                        baseUrl = it
                        testResult = null
                    },
                    label = { Text("接口 Base URL") },
                    placeholder = { Text("https://api.deepseek.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 3. Model Name
                OutlinedTextField(
                    value = model,
                    onValueChange = {
                        model = it
                        testResult = null
                    },
                    label = { Text("模型名称 (Model)") },
                    placeholder = { Text("deepseek-chat") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 4. API Key
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                        testResult = null
                    },
                    label = { Text(if (selectedProvider == AiProvider.OLLAMA) "API Key (本地 Ollama 无需填写)" else "API Key") },
                    placeholder = { Text(if (selectedProvider == AiProvider.OLLAMA) "可选，本地默认留空" else "sk-...") },
                    singleLine = true,
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Text(if (showApiKey) "🙈" else "👁️", fontSize = 16.sp)
                        }
                    },
                    supportingText = {
                        Text(
                            "🔒 凭据仅安全保存在本地 SQLite 数据库中，杜绝任何外部泄露",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // 5. Temperature Slider
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("创造力发散度 (Temperature)", style = MaterialTheme.typography.bodyMedium)
                        Text(String.format("%.2f", temperature), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = temperature,
                        onValueChange = { temperature = it },
                        valueRange = 0.0f..1.5f,
                        steps = 14
                    )
                }

                // 6. Test Result Feedback Card
                if (testResult != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (testSuccess) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = testResult!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (testSuccess) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Test button
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            isTesting = true
                            testResult = null
                            val testConfig = AiConfig(
                                provider = selectedProvider,
                                baseUrl = baseUrl,
                                apiKey = apiKey,
                                model = model,
                                temperature = temperature
                            )
                            val res = AiAssistantEngine.testConnection(testConfig)
                            if (res.isSuccess) {
                                testSuccess = true
                                testResult = res.getOrNull()
                            } else {
                                testSuccess = false
                                testResult = "❌ 连接失败: ${res.exceptionOrNull()?.message}"
                            }
                            isTesting = false
                        }
                    },
                    enabled = !isTesting
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("探测中...")
                    } else {
                        Text("⚡ 测试连接")
                    }
                }

                // Save button
                Button(
                    onClick = {
                        val newConfig = AiConfig(
                            provider = selectedProvider,
                            baseUrl = baseUrl,
                            apiKey = apiKey,
                            model = model,
                            temperature = temperature
                        )
                        scope.launch {
                            AiAssistantEngine.saveConfig(newConfig)
                            onSaved(newConfig)
                            onDismissRequest()
                        }
                    }
                ) {
                    Text("保存并应用")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("取消")
            }
        }
    )
}
