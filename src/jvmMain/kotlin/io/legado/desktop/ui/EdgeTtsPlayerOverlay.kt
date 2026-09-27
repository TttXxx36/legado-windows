package io.legado.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.desktop.engine.tts.*
import io.legado.desktop.ui.theme.LegadoIcons

@Composable
fun EdgeTtsPlayerOverlay(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playState by EdgeTtsEngine.playState.collectAsState()

    var showVoiceMenu by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showTimerMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .wrapContentWidth()
            .padding(16.dp)
            .shadow(16.dp, RoundedCornerShape(32.dp)),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 8.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Voice selector pill
            Box {
                Surface(
                    onClick = { showVoiceMenu = true },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.height(32.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("🎙️", fontSize = 13.sp)
                        Text(
                            text = playState.activeVoice.name,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                DropdownMenu(
                    expanded = showVoiceMenu,
                    onDismissRequest = { showVoiceMenu = false }
                ) {
                    EdgeTtsEngine.VOICES.forEach { voice ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        "${voice.name} (${voice.gender})",
                                        fontWeight = if (voice.id == playState.activeVoice.id) FontWeight.Bold else FontWeight.Normal
                                    )
                                    Text(voice.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            onClick = {
                                EdgeTtsEngine.setVoice(voice)
                                showVoiceMenu = false
                            }
                        )
                    }
                }
            }

            // 2. Playback sentence count & buffering indicator
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (playState.isBuffering) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                }
                Text(
                    text = if (playState.totalSentences > 0) "${playState.currentSentenceIndex + 1}/${playState.totalSentences} 句" else "就绪",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 3. Playback Controls: Prev | Play/Pause | Next
            IconButton(
                onClick = { EdgeTtsEngine.previousSentence() },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(LegadoIcons.NavigateBefore, contentDescription = "上一句", modifier = Modifier.size(20.dp))
            }

            IconButton(
                onClick = {
                    if (playState.isPlaying) {
                        EdgeTtsEngine.pause()
                    } else {
                        EdgeTtsEngine.resume()
                    }
                },
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    if (playState.isPlaying) LegadoIcons.Pause else LegadoIcons.PlayArrow,
                    contentDescription = if (playState.isPlaying) "暂停" else "播放",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }

            IconButton(
                onClick = { EdgeTtsEngine.nextSentence() },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(LegadoIcons.NavigateNext, contentDescription = "下一句", modifier = Modifier.size(20.dp))
            }

            // 4. Speed Rate Button
            Box {
                TextButton(
                    onClick = { showSpeedMenu = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("${playState.speedRate}x", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                DropdownMenu(
                    expanded = showSpeedMenu,
                    onDismissRequest = { showSpeedMenu = false }
                ) {
                    listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f).forEach { rate ->
                        DropdownMenuItem(
                            text = { Text("${rate}x 语速", fontWeight = if (rate == playState.speedRate) FontWeight.Bold else FontWeight.Normal) },
                            onClick = {
                                EdgeTtsEngine.setSpeedRate(rate)
                                showSpeedMenu = false
                            }
                        )
                    }
                }
            }

            // 5. Sleep Timer Button (🌙)
            Box {
                FilledTonalButton(
                    onClick = { showTimerMenu = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    val label = if (playState.remainingSeconds > 0) {
                        val m = playState.remainingSeconds / 60
                        val s = playState.remainingSeconds % 60
                        "🌙 %02d:%02d".format(m, s)
                    } else if (playState.timerMode == SleepTimerMode.CHAPTER_END) {
                        "🌙 章末停"
                    } else {
                        "🌙 定时"
                    }
                    Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }

                DropdownMenu(
                    expanded = showTimerMenu,
                    onDismissRequest = { showTimerMenu = false }
                ) {
                    SleepTimerMode.values().forEach { mode ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    mode.displayName,
                                    fontWeight = if (mode == playState.timerMode) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                EdgeTtsEngine.setSleepTimer(mode)
                                showTimerMenu = false
                            }
                        )
                    }
                }
            }

            // 6. Dismiss Button
            IconButton(
                onClick = {
                    EdgeTtsEngine.stop()
                    onDismiss()
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(LegadoIcons.Close, contentDescription = "关闭朗读", modifier = Modifier.size(16.dp))
            }
        }
    }
}
