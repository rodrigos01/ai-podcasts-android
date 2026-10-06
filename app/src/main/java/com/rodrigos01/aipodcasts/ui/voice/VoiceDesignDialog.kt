package com.rodrigos01.aipodcasts.ui.voice

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rodrigos01.aipodcasts.R
import com.rodrigos01.aipodcasts.data.model.DesignedVoice
import com.rodrigos01.aipodcasts.ui.theme.ExpressiveShapes

/** Shows the voice picker for [controller] while it has an open session; renders nothing otherwise. */
@Composable
fun VoiceDesignDialog(controller: VoiceDesignController) {
    val state by controller.uiState.collectAsState()
    val preview by controller.previewPlayer.state.collectAsState()
    val current = state ?: return

    AlertDialog(
        onDismissRequest = controller::dismiss,
        title = { Text(stringResource(R.string.voice_design_title, current.personName)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = current.prompt,
                    onValueChange = controller::onPromptChanged,
                    label = { Text(stringResource(R.string.voice_design_prompt_label)) },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    shape = ExpressiveShapes.small,
                    enabled = !current.isDesigning
                )

                val hasCandidates = current.candidates.isNotEmpty()
                val designButtonLabel = stringResource(
                    if (hasCandidates) R.string.voice_design_regenerate else R.string.voice_design_generate
                )
                if (hasCandidates) {
                    OutlinedButton(
                        onClick = controller::design,
                        enabled = current.prompt.isNotBlank() && !current.isDesigning,
                        modifier = Modifier.fillMaxWidth(),
                        shape = ExpressiveShapes.small
                    ) { DesignButtonContent(current.isDesigning, designButtonLabel) }
                } else {
                    Button(
                        onClick = controller::design,
                        enabled = current.prompt.isNotBlank() && !current.isDesigning,
                        modifier = Modifier.fillMaxWidth(),
                        shape = ExpressiveShapes.small
                    ) { DesignButtonContent(current.isDesigning, designButtonLabel) }
                }

                if (current.isDesigning) {
                    Text(
                        text = stringResource(R.string.voice_design_wait),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                current.currentVoice?.let { voice ->
                    VoiceRow(
                        label = stringResource(R.string.voice_design_current),
                        voice = voice,
                        isSelected = current.selectedVoiceId == voice.voiceId,
                        enabled = !current.isDesigning,
                        preview = preview,
                        controller = controller
                    )
                }
                current.candidates.forEachIndexed { index, voice ->
                    VoiceRow(
                        label = stringResource(R.string.voice_design_candidate, index + 1),
                        voice = voice,
                        isSelected = current.selectedVoiceId == voice.voiceId,
                        enabled = !current.isDesigning,
                        preview = preview,
                        controller = controller
                    )
                }

                val error = current.errorMessage
                    ?: if (preview.failed) stringResource(R.string.voice_design_preview_failed) else null
                if (error != null) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = controller::confirm, enabled = current.selectedVoiceId != null) {
                Text(stringResource(R.string.voice_design_use))
            }
        },
        dismissButton = {
            TextButton(onClick = controller::dismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        shape = ExpressiveShapes.large
    )
}

@Composable
private fun DesignButtonContent(isDesigning: Boolean, label: String) {
    if (isDesigning) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(R.string.voice_design_designing))
    } else {
        Text(label)
    }
}

@Composable
private fun VoiceRow(
    label: String,
    voice: DesignedVoice,
    isSelected: Boolean,
    enabled: Boolean,
    preview: VoicePreviewPlayer.State,
    controller: VoiceDesignController
) {
    val isActive = preview.voiceId == voice.voiceId
    val isLoading = isActive && preview.phase == VoicePreviewPlayer.Phase.Loading
    val isPlaying = isActive && preview.phase == VoicePreviewPlayer.Phase.Playing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { controller.select(voice.voiceId) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = { controller.select(voice.voiceId) },
            enabled = enabled
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = { controller.togglePreview(voice) }) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = stringResource(
                        if (isPlaying) R.string.voice_design_pause else R.string.voice_design_play
                    )
                )
            }
        }
    }
}
