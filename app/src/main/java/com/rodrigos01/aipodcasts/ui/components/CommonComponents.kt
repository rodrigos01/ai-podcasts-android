package com.rodrigos01.aipodcasts.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.rodrigos01.aipodcasts.R
import com.rodrigos01.aipodcasts.player.PodcastAudioController
import com.rodrigos01.aipodcasts.ui.theme.ExpressiveShapes
import com.rodrigos01.aipodcasts.ui.theme.GeneratingAmber
import com.rodrigos01.aipodcasts.ui.theme.ReadyGreen
import com.rodrigos01.aipodcasts.ui.theme.StreamableBlue
import com.rodrigos01.aipodcasts.ui.theme.ErrorRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressiveTopAppBar(
    title: String,
    canNavigateBack: Boolean = false,
    onNavigateBack: () -> Unit = {},
    actions: @Composable () -> Unit = {}
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            if (canNavigateBack) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back)
                    )
                }
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground
        )
    )
}

@Composable
fun StatusBadge(status: String) {
    val (bgColor, textColor, labelRes) = when (status.lowercase()) {
        "ready" -> Triple(ReadyGreen.copy(alpha = 0.18f), ReadyGreen, R.string.episode_status_ready)
        "streamable" -> Triple(StreamableBlue.copy(alpha = 0.18f), StreamableBlue, R.string.episode_status_streamable)
        "generating" -> Triple(GeneratingAmber.copy(alpha = 0.18f), GeneratingAmber, R.string.episode_status_generating)
        else -> Triple(ErrorRed.copy(alpha = 0.18f), ErrorRed, R.string.episode_status_failed)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alphaAnim by if (status.equals("generating", ignoreCase = true)) {
        infiniteTransition.animateFloat(
            initialValue = 0.5f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "generatingPulse"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    }

    Surface(
        color = bgColor,
        shape = ExpressiveShapes.extraSmall,
        modifier = Modifier.alpha(alphaAnim)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(textColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(labelRes),
                color = textColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun VoiceChip(voiceName: String, persona: String? = null) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.GraphicEq,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (!persona.isNullOrBlank()) "$voiceName • $persona" else voiceName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    actionButtonText: String? = null,
    onActionClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(72.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionButtonText != null) {
            Spacer(modifier = Modifier.height(20.dp))
            androidx.compose.material3.Button(
                onClick = onActionClick,
                shape = ExpressiveShapes.medium
            ) {
                Text(text = actionButtonText, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private const val DISMISS_THRESHOLD_FRACTION = 0.35f
private const val PLAYING_COMMIT_FACTOR = 1.3f
private const val PLAYING_RESISTANCE = 0.25f

@UnstableApi
@Composable
fun MiniPlayerBar(
    audioController: PodcastAudioController,
    onOpenFullPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentEpisode by audioController.currentEpisode.collectAsState()
    val isPlaying by audioController.isPlaying.collectAsState()
    val isBuffering by audioController.isBuffering.collectAsState()
    val podcastTitle by audioController.currentPodcastTitle.collectAsState()
    val positionMs by audioController.currentPositionMs.collectAsState()
    val durationMs by audioController.durationMs.collectAsState()

    AnimatedVisibility(
        visible = currentEpisode != null,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        val episode = currentEpisode ?: return@AnimatedVisibility

        val haptics = LocalHapticFeedback.current
        val coroutineScope = rememberCoroutineScope()
        var widthPx by remember { mutableFloatStateOf(0f) }
        var dragX by remember { mutableFloatStateOf(0f) }
        val threshold = widthPx * DISMISS_THRESHOLD_FRACTION
        // A playing mini player resists at the threshold and needs a deliberate extra pull;
        // a paused one just goes.
        val commitDistance = if (isPlaying) threshold * PLAYING_COMMIT_FACTOR else threshold

        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .onSizeChanged { widthPx = it.width.toFloat() }
                .graphicsLayer {
                    translationX = dragX
                    alpha = if (widthPx > 0f) {
                        (1f - abs(dragX) / (widthPx * 1.2f)).coerceIn(0.2f, 1f)
                    } else 1f
                }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        val resisted = isPlaying && abs(dragX) >= threshold && dragX * delta > 0
                        dragX += if (resisted) delta * PLAYING_RESISTANCE else delta
                    },
                    onDragStopped = {
                        if (threshold > 0f && abs(dragX) >= commitDistance) {
                            if (isPlaying) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            val target = if (dragX > 0) widthPx * 1.2f else -widthPx * 1.2f
                            coroutineScope.launch {
                                animate(dragX, target) { value, _ -> dragX = value }
                                audioController.stop()
                            }
                        } else {
                            coroutineScope.launch {
                                animate(dragX, 0f) { value, _ -> dragX = value }
                            }
                        }
                    }
                )
                .clickable { onOpenFullPlayer() },
            shape = ExpressiveShapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(44.dp),
                        shape = ExpressiveShapes.small,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = episode.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = podcastTitle.ifBlank { stringResource(R.string.app_name) },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(32.dp)
                                .padding(4.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        IconButton(onClick = { audioController.togglePlayPause() }) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) stringResource(R.string.player_pause) else stringResource(R.string.player_play),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                }

                val barModifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                if (durationMs > 0) {
                    val progress = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = barModifier,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                } else if (isPlaying || isBuffering) {
                    // Length unknown while the episode is still generating: only animate while
                    // audio is actually flowing, otherwise the bar keeps moving under a paused player.
                    LinearProgressIndicator(
                        modifier = barModifier,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { 0f },
                        modifier = barModifier,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
operator fun PaddingValues.plus(other: PaddingValues): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = this.calculateStartPadding(layoutDirection) + other.calculateStartPadding(layoutDirection),
        top = this.calculateTopPadding() + other.calculateTopPadding(),
        end = this.calculateEndPadding(layoutDirection) + other.calculateEndPadding(layoutDirection),
        bottom = this.calculateBottomPadding() + other.calculateBottomPadding()
    )
}
