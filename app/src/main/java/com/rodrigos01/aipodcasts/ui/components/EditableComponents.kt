package com.rodrigos01.aipodcasts.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rodrigos01.aipodcasts.R
import com.rodrigos01.aipodcasts.ui.voice.VoiceDesignController
import com.rodrigos01.aipodcasts.ui.voice.VoiceDesignSection
import com.rodrigos01.aipodcasts.ui.theme.ExpressiveShapes

/**
 * A labelled read-only text whose header carries an Edit/Done button that swaps the text for a
 * text field, so generated wizard content can be adjusted before it is sent back to the API.
 */
@Composable
fun EditableTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    labelColor: Color = MaterialTheme.colorScheme.secondary,
    enabled: Boolean = true,
    minLines: Int = 1
) {
    var editing by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = labelColor
            )
            TextButton(
                onClick = { editing = !editing },
                enabled = enabled,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(
                    text = stringResource(if (editing) R.string.action_done else R.string.action_edit),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
        if (editing && enabled) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                shape = ExpressiveShapes.small,
                minLines = minLines
            )
        } else {
            Text(
                text = value.ifBlank { "—" },
                style = textStyle,
                color = textColor
            )
        }
    }
}

/** Dialog for editing a host's or guest's name and persona. */
@Composable
fun PersonEditDialog(
    initialName: String,
    initialPersona: String,
    onDismiss: () -> Unit,
    onSave: (name: String, persona: String) -> Unit,
    onChooseVoice: (() -> Unit)? = null,
    hasPickedVoice: Boolean = false,
    voiceDesign: VoiceDesignController? = null
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var persona by rememberSaveable { mutableStateOf(initialPersona) }

    AlertDialog(
        onDismissRequest = { voiceDesign?.dismiss(); onDismiss() },
        title = { Text(stringResource(R.string.person_edit_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.podcast_edit_host_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = ExpressiveShapes.small
                )
                OutlinedTextField(
                    value = persona,
                    onValueChange = { persona = it },
                    label = { Text(stringResource(R.string.podcast_edit_host_persona)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    shape = ExpressiveShapes.small
                )
                val voiceSession = voiceDesign?.uiState?.collectAsState()?.value
                if (onChooseVoice != null && voiceSession == null) {
                    TextButton(onClick = onChooseVoice) {
                        Text(stringResource(R.string.voice_design_choose))
                    }
                    if (hasPickedVoice) {
                        Text(
                            text = stringResource(R.string.voice_design_picked),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                // The voice picker only appears once "Choose voice" is tapped, and lives in this dialog.
                if (voiceDesign != null) VoiceDesignSection(voiceDesign)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { voiceDesign?.dismiss(); onSave(name.trim(), persona.trim()) },
                enabled = name.isNotBlank() && persona.isNotBlank()
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = { voiceDesign?.dismiss(); onDismiss() }) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        shape = ExpressiveShapes.large
    )
}
