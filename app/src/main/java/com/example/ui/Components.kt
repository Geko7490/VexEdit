package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.example.data.AppLanguage
import com.example.data.AspectRatio
import com.example.data.Strings
import com.example.engine.ExportConfig
import com.example.ui.theme.*

fun formatTimecode(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val frames = ((timeMs % 1000) * 30 / 1000).toInt()
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return String.format(java.util.Locale.US, "%02d:%02d:%02d:%02d", hours, minutes, seconds, frames)
}

fun formatDurationMinutesSeconds(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val seconds = totalSeconds % 60
    val minutes = totalSeconds / 60
    return String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
}

@Composable
fun VexBottomBar(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    lang: AppLanguage
) {
    Surface(
        color = VexBackground,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .border(width = 0.5.dp, color = VexBorder)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabs = listOf(
                Triple(Screen.HOME, Icons.Default.Home, Strings.get("nav_home", lang)),
                Triple(Screen.PROJECTS, Icons.Default.Folder, Strings.get("nav_projects", lang)),
                Triple(Screen.STUDIO, Icons.Default.MovieCreation, Strings.get("nav_studio", lang)),
                Triple(Screen.SETTINGS, Icons.Default.Settings, Strings.get("nav_settings", lang))
            )

            tabs.forEach { (screen, icon, label) ->
                val isSelected = currentScreen == screen ||
                        (screen == Screen.STUDIO && currentScreen in listOf(
                            Screen.AUDIO_STUDIO, Screen.EFFECTS_LAB, Screen.KEYFRAME_EDITOR, Screen.IMPORT_MEDIA
                        ))
                val color = if (isSelected) VexAccent else VexTextSecondary

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onNavigate(screen) }
                        .testTag("nav_${screen.name.lowercase()}")
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = color,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = label,
                        color = color,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
fun NewProjectDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onCreate: (name: String, ratio: AspectRatio) -> Unit,
    lang: AppLanguage
) {
    if (!isOpen) return

    var projectName by remember { mutableStateOf("Untitled Edit") }
    var selectedRatio by remember { mutableStateOf(AspectRatio.RATIO_9_16) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = VexPanel),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .border(1.dp, VexBorder, RoundedCornerShape(12.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = Strings.get("new_project", lang),
                    color = VexTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = Strings.get("project_name", lang),
                    color = VexTextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = VexTextPrimary,
                        unfocusedTextColor = VexTextPrimary,
                        focusedBorderColor = VexAccent,
                        unfocusedBorderColor = VexBorder,
                        focusedContainerColor = VexBackground,
                        unfocusedContainerColor = VexBackground
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("project_name_input")
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = Strings.get("aspect_ratio", lang),
                    color = VexTextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AspectRatio.values().forEach { ratio ->
                        val isSelected = selectedRatio == ratio
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) VexAccent else VexPanelElevated)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) VexAccentLight else VexBorder,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable { selectedRatio = ratio }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = ratio.label,
                                color = if (isSelected) Color.White else VexTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(Strings.get("cancel", lang), color = VexTextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onCreate(projectName, selectedRatio)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = VexAccent),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.testTag("create_project_confirm_btn")
                    ) {
                        Text(Strings.get("create_project", lang), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ExportDialog(
    isOpen: Boolean,
    isExporting: Boolean,
    progress: Float,
    result: com.example.engine.ExportResult?,
    onDismiss: () -> Unit,
    onStartExport: (ExportConfig) -> Unit,
    lang: AppLanguage
) {
    if (!isOpen) return
    val context = LocalContext.current

    var resolution by remember { mutableStateOf("720p") }
    var quality by remember { mutableStateOf("High") }
    var includeAudio by remember { mutableStateOf(true) }

    Dialog(onDismissRequest = { if (!isExporting) onDismiss() }) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = VexPanel),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .border(1.dp, VexBorder, RoundedCornerShape(12.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = Strings.get("export_project", lang),
                    color = VexTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (isExporting) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = Strings.get("exporting", lang),
                            color = VexTextPrimary,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = VexAccent,
                            trackColor = VexPanelElevated
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            color = VexAccent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (result != null) {
                    if (result.success) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = VexMeterGreen)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = Strings.get("export_success", lang),
                                    color = VexTextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${Strings.get("export_ready_message", lang)} (${result.fileSizeFormatted})",
                                color = VexTextSecondary,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            result.outputFile?.let { file ->
                                Button(
                                    onClick = {
                                        try {
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "video/mp4"
                                                putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file))
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Share Video"))
                                        } catch (e: Exception) {
                                            // Handle fallback
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = VexAccent),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(Strings.get("share_video", lang), color = Color.White)
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "Error: ${result.errorMessage ?: "Export failed"}",
                            color = VexMeterRed,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = VexPanelElevated),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(Strings.get("close", lang), color = VexTextPrimary)
                    }
                } else {
                    // Export Settings Form
                    Text(text = Strings.get("resolution", lang), color = VexTextSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("720p", "1080p", "4K").forEach { res ->
                            val isSel = resolution == res
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSel) VexAccent else VexPanelElevated)
                                    .clickable { resolution = res }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(res, color = if (isSel) Color.White else VexTextSecondary, fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(text = Strings.get("include_audio", lang), color = VexTextSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Switch(
                            checked = includeAudio,
                            onCheckedChange = { includeAudio = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = VexAccent,
                                uncheckedTrackColor = VexPanelElevated
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (includeAudio) "Enabled" else "Muted", color = VexTextPrimary, fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(Strings.get("cancel", lang), color = VexTextSecondary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                onStartExport(
                                    ExportConfig(
                                        resolution = resolution,
                                        includeAudio = includeAudio
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = VexAccent),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.testTag("start_export_btn")
                        ) {
                            Text(Strings.get("start_export", lang), color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
