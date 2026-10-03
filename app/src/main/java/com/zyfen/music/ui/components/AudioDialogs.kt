package com.zyfen.music.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.zyfen.music.data.local.PlaylistEntity
import com.zyfen.music.data.media.Song
import com.zyfen.music.playback.AudioEffectsManager
import kotlin.math.*

// -------------------------------------------------------------
// 1. RAINBOW CIRCULAR COLOR PICKER
// -------------------------------------------------------------
@Composable
fun RainbowColorPickerDialog(
    initialColorHex: String,
    onDismiss: () -> Unit,
    onColorSelected: (String) -> Unit
) {
    var selectedHue by remember { mutableStateOf(0f) }
    var selectedSaturation by remember { mutableStateOf(1f) }
    var selectedValue by remember { mutableStateOf(1f) }

    // Init from hex if possible
    LaunchedEffect(initialColorHex) {
        try {
            val c = android.graphics.Color.parseColor(initialColorHex)
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(c, hsv)
            selectedHue = hsv[0]
            selectedSaturation = hsv[1]
            selectedValue = hsv[2]
        } catch (_: Exception) {}
    }

    val currentColor = remember(selectedHue, selectedSaturation, selectedValue) {
        val hsv = floatArrayOf(selectedHue, selectedSaturation, selectedValue)
        Color(android.graphics.Color.HSVToColor(hsv))
    }

    val currentHex = remember(currentColor) {
        val r = (currentColor.red * 255).toInt()
        val g = (currentColor.green * 255).toInt()
        val b = (currentColor.blue * 255).toInt()
        String.format("#%02X%02X%02X", r, g, b)
    }

    val rainbowColors = listOf(
        Color(0xFFFF0000),
        Color(0xFFFF7F00),
        Color(0xFFFFFF00),
        Color(0xFF00FF00),
        Color(0xFF00FFFF),
        Color(0xFF0000FF),
        Color(0xFF8B00FF),
        Color(0xFFFF007F),
        Color(0xFFFF0000)
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Custom Accent Color",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Pick any color from the rainbow wheel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(20.dp))

                // Circular Rainbow Color Wheel
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    val center = Offset(size.width / 2f, size.height / 2f)
                                    val dx = offset.x - center.x
                                    val dy = offset.y - center.y
                                    var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                    if (angle < 0) angle += 360f
                                    selectedHue = angle
                                    val dist = sqrt(dx * dx + dy * dy)
                                    val maxR = size.width / 2f
                                    selectedSaturation = (dist / maxR).coerceIn(0.2f, 1f)
                                }
                            }
                            .pointerInput(Unit) {
                                detectDragGestures { change, _ ->
                                    val center = Offset(size.width / 2f, size.height / 2f)
                                    val dx = change.position.x - center.x
                                    val dy = change.position.y - center.y
                                    var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                    if (angle < 0) angle += 360f
                                    selectedHue = angle
                                    val dist = sqrt(dx * dx + dy * dy)
                                    val maxR = size.width / 2f
                                    selectedSaturation = (dist / maxR).coerceIn(0.2f, 1f)
                                }
                            }
                    ) {
                        val strokeWidth = 36.dp.toPx()
                        val radius = (size.minDimension - strokeWidth) / 2f
                        drawCircle(
                            brush = Brush.sweepGradient(rainbowColors),
                            radius = radius,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )

                        // Selector marker on the wheel
                        val rad = Math.toRadians(selectedHue.toDouble())
                        val markerX = (size.width / 2f) + radius * cos(rad).toFloat()
                        val markerY = (size.height / 2f) + radius * sin(rad).toFloat()
                        drawCircle(Color.White, radius = 12.dp.toPx(), center = Offset(markerX, markerY))
                        drawCircle(currentColor, radius = 8.dp.toPx(), center = Offset(markerX, markerY))
                    }

                    // Center preview circle with Hex code
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(currentColor)
                            .border(3.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            currentHex,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (selectedValue < 0.5f || (currentColor.red * 0.299 + currentColor.green * 0.587 + currentColor.blue * 0.114) < 0.5) Color.White else Color.Black
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Brightness Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.BrightnessLow, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(
                        value = selectedValue,
                        onValueChange = { selectedValue = it },
                        valueRange = 0.2f..1f,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = currentColor,
                            activeTrackColor = currentColor
                        )
                    )
                    Icon(Icons.Filled.BrightnessHigh, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(Modifier.height(16.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = { onColorSelected(currentHex) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = currentColor),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Apply Color", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 2. ADD TO PLAYLIST SHEET
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    song: Song?,
    playlists: List<PlaylistEntity>,
    onCreatePlaylist: (String) -> Unit,
    onAddToPlaylist: (playlistId: String, song: Song) -> Unit,
    onDismiss: () -> Unit
) {
    if (song == null) return

    var showCreateInput by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var addedMessage by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Add to Playlist",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        song.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Inline Feedback
            AnimatedVisibility(visible = addedMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            addedMessage ?: "",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Create New Playlist Row / Input
            if (showCreateInput) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text("Playlist Name") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Button(
                        onClick = {
                            if (newPlaylistName.isNotBlank()) {
                                onCreatePlaylist(newPlaylistName.trim())
                                newPlaylistName = ""
                                showCreateInput = false
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Create")
                    }
                }
                Spacer(Modifier.height(16.dp))
            } else {
                FilledTonalButton(
                    onClick = { showCreateInput = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("New Playlist")
                }
                Spacer(Modifier.height(12.dp))
            }

            // Playlists list
            if (playlists.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No playlists yet. Tap 'New Playlist' above to create one!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(playlists, key = { it.id }) { playlist ->
                        Card(
                            onClick = {
                                onAddToPlaylist(playlist.id, song)
                                addedMessage = "Added to ${playlist.name}!"
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.PlaylistAdd,
                                        null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        playlist.name,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "Tap to add song",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    Icons.Filled.AddCircleOutline,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 3. EQUALIZER & REVERB MODAL SHEET
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerReverbSheet(
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Reverb, 1: Equalizer
    val isReverbOn by AudioEffectsManager.isReverbEnabled.collectAsState()
    val currentReverb by AudioEffectsManager.currentReverbPreset.collectAsState()
    val isEqOn by AudioEffectsManager.isEqEnabled.collectAsState()
    val currentEq by AudioEffectsManager.currentEqPreset.collectAsState()
    val bass by AudioEffectsManager.bassStrength.collectAsState()
    val virtualizer by AudioEffectsManager.virtualizerStrength.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Audio Effects & Spatial",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Tab Selector
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Reverb & Spatial", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Equalizer", fontWeight = FontWeight.Bold) }
                )
            }

            Spacer(Modifier.height(16.dp))

            if (selectedTab == 0) {
                // REVERB SECTION
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Reverb Spatial Audio",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Immersive acoustic reflection for every song",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isReverbOn,
                        onCheckedChange = { AudioEffectsManager.setReverbEnabled(it) }
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Presets Grid
                Text(
                    "Acoustic Space Presets",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val rows = AudioEffectsManager.reverbPresets.chunked(2)
                    rows.forEach { rowPresets ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowPresets.forEach { preset ->
                                val active = isReverbOn && currentReverb.equals(preset, ignoreCase = true)
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            AudioEffectsManager.setReverbPreset(preset)
                                        },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = BorderStroke(1.dp, if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            preset,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (active) FontWeight.Bold else FontWeight.Normal),
                                            color = if (active) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (active) {
                                            Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Bass Boost & Virtualizer
                Text("Bass Boost (${bass / 10}%)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                Slider(
                    value = bass.toFloat(),
                    onValueChange = { AudioEffectsManager.setBassBoost(it.toInt()) },
                    valueRange = 0f..1000f
                )

                Text("3D Spatial Virtualizer (${virtualizer / 10}%)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                Slider(
                    value = virtualizer.toFloat(),
                    onValueChange = { AudioEffectsManager.setVirtualizer(it.toInt()) },
                    valueRange = 0f..1000f
                )

            } else {
                // EQUALIZER SECTION
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Equalizer Sound Profiles",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = isEqOn,
                        onCheckedChange = { AudioEffectsManager.setEqEnabled(it) }
                    )
                }

                Spacer(Modifier.height(12.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val rows = AudioEffectsManager.eqPresets.chunked(3)
                    rows.forEach { rowPresets ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowPresets.forEach { preset ->
                                val active = isEqOn && currentEq.equals(preset, ignoreCase = true)
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            AudioEffectsManager.setEqPreset(preset)
                                        },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ) {
                                    Text(
                                        preset,
                                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (active) FontWeight.Bold else FontWeight.Normal),
                                        color = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
