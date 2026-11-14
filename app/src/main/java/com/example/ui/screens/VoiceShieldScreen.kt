package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.BlacklistedFolderEntity
import com.example.data.local.TrackEntity
import com.example.domain.FilteredTrackItem
import com.example.domain.LrcParser
import com.example.ui.FilterSettingsState
import com.example.ui.theme.CrimsonDanger
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MutedSage
import com.example.ui.theme.MutedSlateBlue
import com.example.ui.theme.OledCarbon
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioElevated
import com.example.ui.theme.StudioSlate
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarmAmber
import com.example.ui.theme.studioSwitchColors

@Composable
fun VoiceShieldScreen(
    filterSettings: FilterSettingsState,
    blacklistedFolders: List<BlacklistedFolderEntity>,
    blockedVoiceNotes: List<FilteredTrackItem>,
    hiddenTracks: List<TrackEntity>,
    onToggleStrictFormats: (Boolean) -> Unit,
    onToggleBlockVoiceNotes: (Boolean) -> Unit,
    onToggleFolderRule: (String, Boolean) -> Unit,
    onAddCustomFolderRule: (String, String) -> Unit,
    onRemoveCustomFolderRule: (String) -> Unit,
    onRestoreHiddenTrack: (TrackEntity) -> Unit
) {
    var showAddFolderModal by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(OledCarbon)
            .testTag("voice_shield_lazy_column"),
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 140.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Shield Overview Header Card
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.dp, StudioBorder, RoundedCornerShape(18.dp)),
                color = StudioSlate
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = MutedSage,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "WhatsApp & Voice-Note Shield",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary
                            )
                            Text(
                                text = "${blockedVoiceNotes.size} voice notes & non-music files excluded from playback",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MutedSage
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = StudioBorder)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Strict MP3 / M4A / FLAC Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Strict Music Containers Only (.MP3, .M4A, .FLAC)",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Automatically ignores .opus, .ogg, .amr, and .wav voice memos",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Switch(
                            checked = filterSettings.strictKnownFormatsOnly,
                            onCheckedChange = onToggleStrictFormats,
                            colors = studioSwitchColors(),
                            modifier = Modifier.testTag("strict_formats_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // WhatsApp PTT / AUD Voice Note Signature Heuristic Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Deep Voice-Note Signature Guard (PTT-* / AUD-*)",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Blocks WhatsApp voice notes even if saved as .m4a or over 15 minutes long",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Switch(
                            checked = filterSettings.blockWhatsAppVoiceNotes,
                            onCheckedChange = onToggleBlockVoiceNotes,
                            colors = studioSwitchColors(),
                            modifier = Modifier.testTag("whatsapp_signature_switch")
                        )
                    }
                }
            }
        }

        // Folder Blacklist Manager
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "FOLDER BLACKLIST RULES (${blacklistedFolders.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = MutedSlateBlue
                )
                Button(
                    onClick = { showAddFolderModal = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StudioElevated,
                        contentColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("add_custom_folder_rule_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Folder Path", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        items(blacklistedFolders, key = { it.pathPattern }) { folderRule ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, StudioBorder, RoundedCornerShape(14.dp)),
                color = StudioSlate
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOff,
                            contentDescription = null,
                            tint = if (folderRule.isEnabled) MutedSlateBlue else TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = folderRule.label,
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = folderRule.pathPattern,
                                style = MaterialTheme.typography.labelSmall,
                                color = MutedSlateBlue
                            )
                            Text(
                                text = folderRule.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!folderRule.isBuiltInRule) {
                            IconButton(
                                onClick = { onRemoveCustomFolderRule(folderRule.pathPattern) }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete Custom Rule",
                                    tint = CrimsonDanger
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = folderRule.isEnabled,
                            onCheckedChange = { onToggleFolderRule(folderRule.pathPattern, it) },
                            colors = studioSwitchColors()
                        )
                    }
                }
            }
        }

        // Blocked WhatsApp Voice Notes & Clutter Log
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "INTERCEPTED VOICE NOTES & CLUTTER (${blockedVoiceNotes.size})",
                style = MaterialTheme.typography.labelLarge,
                color = WarmAmber
            )
            Text(
                text = "Files below were detected on device storage and blocked from polluting your music library:",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }

        items(blockedVoiceNotes, key = { it.track.id }) { item ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, WarmAmber.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
                color = StudioSlate
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = "Blocked Voice Note",
                        tint = CrimsonDanger,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.track.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(CrimsonDanger.copy(alpha = 0.18f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = ".${item.track.extension.uppercase()} • ${LrcParser.formatTimestamp(item.track.durationMs)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CrimsonDanger
                                )
                            }
                        }
                        Text(
                            text = item.reason,
                            style = MaterialTheme.typography.bodyMedium,
                            color = WarmAmber
                        )
                        Text(
                            text = item.track.filePath,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Hidden Tracks Vault (Safe Library Exclusion Restore)
        if (hiddenTracks.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "HIDDEN FROM LIBRARY (${hiddenTracks.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = ElectricCyan
                )
            }
            items(hiddenTracks, key = { it.id }) { hidden ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, StudioBorder, RoundedCornerShape(14.dp)),
                    color = StudioSlate
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = hidden.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                            Text(
                                text = hidden.artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                        OutlinedButton(
                            onClick = { onRestoreHiddenTrack(hidden) },
                            modifier = Modifier.testTag("restore_hidden_track_${hidden.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Restore,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Restore", color = ElectricCyan)
                        }
                    }
                }
            }
        }
    }

    if (showAddFolderModal) {
        var pathPattern by remember { mutableStateOf("/whatsapp/media/") }
        var label by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddFolderModal = false },
            containerColor = StudioSlate,
            titleContentColor = TextPrimary,
            title = { Text("Add Folder Blacklist Rule") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Any audio file whose path contains this folder fragment will be excluded from your music library:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Rule Label (e.g. Viber Voice Notes)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pathPattern,
                        onValueChange = { pathPattern = it },
                        label = { Text("Folder Path Substring") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_folder_path_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddCustomFolderRule(pathPattern, label)
                        showAddFolderModal = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = OledCarbon
                    )
                ) {
                    Text("Add Rule", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddFolderModal = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}
