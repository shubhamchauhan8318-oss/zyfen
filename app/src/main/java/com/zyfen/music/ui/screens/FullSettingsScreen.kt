package com.zyfen.music.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zyfen.music.ZyfenApp
import com.zyfen.music.ui.components.ScreenHeader
import com.zyfen.music.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun FullSettingsScreen(
    onBack: () -> Unit = {},
    vm: LibraryViewModel? = null
) {
    androidx.activity.compose.BackHandler { onBack() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsStore = ZyfenApp.container.settingsStore

    val currentThemeMode by settingsStore.themeMode.collectAsState(initial = "dark")
    val currentAccentHex by settingsStore.accentColor.collectAsState(initial = "#8B5CF6")
    val currentDynamicArt by settingsStore.dynamicArtwork.collectAsState(initial = false)
    val currentQuality by settingsStore.audioQuality.collectAsState(initial = "320")
    val currentEq by settingsStore.equalizerPreset.collectAsState(initial = "Balanced")
    val currentUiStyle by settingsStore.uiStyle.collectAsState(initial = "glass")
    val isReverbEnabled by com.zyfen.music.playback.AudioEffectsManager.isReverbEnabled.collectAsState()
    val currentReverbPreset by com.zyfen.music.playback.AudioEffectsManager.currentReverbPreset.collectAsState()

    var selectedSleepTimer by remember { mutableIntStateOf(0) }
    var showThemeModeDialog by remember { mutableStateOf(false) }
    var showAccentColorDialog by remember { mutableStateOf(false) }
    var showRainbowPicker by remember { mutableStateOf(false) }
    var showUiStyleDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showEqDialog by remember { mutableStateOf(false) }
    var showSleepDialog by remember { mutableStateOf(false) }

    val accent = LocalAccentColor.current

    val accentPresets = remember {
        listOf(
            "Purple" to "#8B5CF6",
            "Blue" to "#3B82F6",
            "Cyan" to "#06B6D4",
            "Emerald" to "#10B981",
            "Red" to "#EF4444",
            "Orange" to "#F97316",
            "Pink" to "#EC4899",
            "Amber" to "#F59E0B"
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        item {
            ScreenHeader(
                "Settings & Customization",
                leading = {
                    Surface(
                        onClick = onBack,
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            )
        }

        item {
            SettingsCategoryHeader("Appearance & Theme Engine")
        }

        // UI Design Style (Liquid Glassmorphism vs Classic Modern)
        item {
            val uiStyleLabel = if (currentUiStyle == "glass") {
                "Liquid Glassmorphism (Frost Glass & Dynamic Mesh)"
            } else {
                "Classic Modern (Solid Material 3 & AMOLED)"
            }
            GlassTile(
                icon = Icons.Filled.BlurOn,
                iconTint = accent,
                title = "UI Design Style",
                subtitle = uiStyleLabel,
                badgeText = if (currentUiStyle == "glass") "LIQUID" else "CLASSIC",
                onClick = { showUiStyleDialog = true }
            )
        }

        // Theme Mode (Spotify / Blush / Light / Dark / AMOLED / System)
        item {
            val modeLabel = when (currentThemeMode.lowercase()) {
                "spotify" -> "Spotify Pure Night (#121212 + Neon Green)"
                "blush", "pink", "pastel" -> "Pastel Blush & Rose Pink (Reference Style)"
                "light" -> "Clean Minimal Light"
                "amoled" -> "AMOLED Pure Pitch Black (#000000)"
                "system" -> "System Default (Auto)"
                else -> "Cosmic Slate Dark (Modern)"
            }
            GlassTile(
                icon = Icons.Filled.DarkMode,
                iconTint = accent,
                title = "Theme Atmosphere",
                subtitle = modeLabel,
                onClick = { showThemeModeDialog = true }
            )
        }

        // Accent Color Selection
        item {
            GlassTile(
                icon = Icons.Filled.Palette,
                iconTint = accent,
                title = "Accent Color",
                subtitle = "Active Theme Color: $currentAccentHex",
                onClick = { showAccentColorDialog = true },
                trailing = {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(accent)
                            .border(2.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                    )
                }
            )
        }

        // Quick Accent Palette Swatches inside a floating GlassCard
        item {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(20.dp),
                fillAlpha = 0.07f,
                contentPadding = PaddingValues(12.dp)
            ) {
                Text(
                    "Quick Accent Color Swatches",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        shadow = LiquidGlassTokens.SubtleTextShadow
                    ),
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Interactive Rainbow Color Wheel button
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { showRainbowPicker = true }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.sweepGradient(
                                            listOf(
                                                Color.Red, Color.Yellow, Color.Green,
                                                Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                                            )
                                        )
                                    )
                                    .border(1.5.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Colorize, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("Rainbow", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                    items(accentPresets) { (name, hex) ->
                        val color = parseHexColor(hex)
                        val isSelected = currentAccentHex.equals(hex, ignoreCase = true)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                scope.launch { settingsStore.setAccentColor(hex) }
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.3f),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(name, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                }
            }
        }

        // Dynamic Artwork Colors Switch
        item {
            GlassTile(
                icon = Icons.Filled.AutoAwesome,
                iconTint = accent,
                title = "Dynamic Artwork Tints",
                subtitle = "Subtly infuse player glass elements with album cover hue",
                trailing = {
                    Switch(
                        checked = currentDynamicArt,
                        onCheckedChange = { checked ->
                            scope.launch { settingsStore.setDynamicArtwork(checked) }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = accent,
                            uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                            uncheckedTrackColor = Color.White.copy(alpha = 0.15f)
                        )
                    )
                }
            )
        }

        item {
            Spacer(Modifier.height(14.dp))
            SettingsCategoryHeader("Audio & Streaming Engine")
        }

        item {
            val reverbLabel = if (isReverbEnabled) "$currentReverbPreset (Active Spatial Effect)" else "Disabled (Tap to turn on)"
            GlassTile(
                icon = Icons.Filled.GraphicEq,
                iconTint = accent,
                title = "Spatial Audio & Reverb",
                subtitle = reverbLabel,
                onClick = { showEqDialog = true }
            )
        }

        item {
            GlassTile(
                icon = Icons.Filled.Equalizer,
                iconTint = accent,
                title = "Equalizer & Sound Profiles",
                subtitle = "Active: $currentEq (5-Band Pro EQ & Spatial Reverb)",
                onClick = { showEqDialog = true }
            )
        }

        item {
            val qualityLabel = when (currentQuality) {
                "320" -> "Ultra High Fidelity (320 kbps)"
                "160" -> "Standard High Quality (160 kbps)"
                else -> "Data Saver (96 kbps)"
            }
            GlassTile(
                icon = Icons.Filled.HighQuality,
                iconTint = accent,
                title = "Streaming Quality",
                subtitle = qualityLabel,
                onClick = { showQualityDialog = true }
            )
        }

        item {
            val sleepLabel = if (selectedSleepTimer == 0) "Disabled" else "$selectedSleepTimer minutes remaining"
            GlassTile(
                icon = Icons.Filled.Bedtime,
                iconTint = accent,
                title = "Sleep Timer",
                subtitle = sleepLabel,
                onClick = { showSleepDialog = true }
            )
        }

        item {
            Spacer(Modifier.height(14.dp))
            SettingsCategoryHeader("Storage & Device Library")
        }

        item {
            GlassTile(
                icon = Icons.Filled.Refresh,
                iconTint = accent,
                title = "Rescan Device Audio",
                subtitle = "Scan device storage for new MP3, FLAC, WAV, and M4A audio",
                onClick = {
                    vm?.refresh()
                    Toast.makeText(context, "Scanning local device audio…", Toast.LENGTH_SHORT).show()
                }
            )
        }

        item {
            GlassTile(
                icon = Icons.Filled.CleaningServices,
                iconTint = accent,
                title = "Clear Streaming Cache",
                subtitle = "Free temporary stream audio and image cache",
                onClick = {
                    context.cacheDir.deleteRecursively()
                    Toast.makeText(context, "Streaming cache cleared!", Toast.LENGTH_SHORT).show()
                }
            )
        }

        item {
            Spacer(Modifier.height(14.dp))
            SettingsCategoryHeader("About Zyfen Music")
        }

        item {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(22.dp),
                fillAlpha = 0.08f,
                accentGlow = accent
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = accent.copy(alpha = 0.20f),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.50f)),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.MusicNote, null, tint = accent, modifier = Modifier.size(24.dp))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "ZYFEN MUSIC PRO",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                shadow = LiquidGlassTokens.TextShadow
                            ),
                            color = Color.White
                        )
                        Text(
                            "Version 1.7.0 • Liquid Glassmorphism Edition",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFD8B4FE)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Fluid animated mesh gradient canvas, frosted glass UI components, 320kbps audio engine, real-time spatial equalizer, Spotify playlist importer, and full offline player.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.5.sp,
                        shadow = LiquidGlassTokens.SubtleTextShadow
                    ),
                    color = Color.White.copy(alpha = 0.80f)
                )
            }
        }
    }

    // 1. Theme Atmosphere Dialog (GlassDialog)
    if (showThemeModeDialog) {
        val modes = listOf(
            "dark" to "Cosmic Obsidian (Deep Liquid Violet & Indigo)",
            "spotify" to "Spotify Pure Night (#121212 + Neon Accent)",
            "blush" to "Pastel Blush & Rose Glass",
            "amoled" to "AMOLED Pure Pitch Black Glass",
            "light" to "Frosted Crystal Light",
            "system" to "System Adaptive Default"
        )
        GlassDialog(
            onDismissRequest = { showThemeModeDialog = false },
            title = "Choose Theme Atmosphere",
            dismissButton = {
                GlassPillButton(text = "Close", onClick = { showThemeModeDialog = false })
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                modes.forEach { (key, label) ->
                    val isSelected = currentThemeMode.equals(key, ignoreCase = true)
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        fillAlpha = if (isSelected) 0.18f else 0.06f,
                        accentGlow = if (isSelected) accent else null,
                        onClick = {
                            scope.launch { settingsStore.setThemeMode(key) }
                            showThemeModeDialog = false
                        },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    scope.launch { settingsStore.setThemeMode(key) }
                                    showThemeModeDialog = false
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = accent,
                                    unselectedColor = Color.White.copy(alpha = 0.45f)
                                )
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                label,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    shadow = LiquidGlassTokens.SubtleTextShadow
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    // 2. UI Design Style Dialog
    if (showUiStyleDialog) {
        GlassDialog(
            onDismissRequest = { showUiStyleDialog = false },
            title = "UI Design Style",
            dismissButton = {
                GlassPillButton(text = "Close", onClick = { showUiStyleDialog = false })
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Option 1: Liquid Glassmorphism
                val isGlass = currentUiStyle == "glass"
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    fillAlpha = if (isGlass) 0.22f else 0.08f,
                    accentGlow = if (isGlass) accent else null,
                    onClick = {
                        scope.launch { settingsStore.setUiStyle("glass") }
                        showUiStyleDialog = false
                    },
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = isGlass,
                            onClick = {
                                scope.launch { settingsStore.setUiStyle("glass") }
                                showUiStyleDialog = false
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = accent,
                                unselectedColor = Color.White.copy(alpha = 0.5f)
                            )
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "Liquid Glassmorphism (Frost Glass Effect)",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isGlass) FontWeight.Bold else FontWeight.Normal,
                                    shadow = LiquidGlassTokens.SubtleTextShadow
                                )
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Dynamic fluid animated mesh gradient matching your chosen accent, frosted glass cards, floating pill navigation, and light refraction highlights.",
                                color = Color.White.copy(alpha = 0.75f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                // Option 2: Classic Modern (Solid Material 3)
                val isSolid = currentUiStyle != "glass"
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    fillAlpha = if (isSolid) 0.22f else 0.08f,
                    accentGlow = if (isSolid) accent else null,
                    onClick = {
                        scope.launch { settingsStore.setUiStyle("solid") }
                        showUiStyleDialog = false
                    },
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = isSolid,
                            onClick = {
                                scope.launch { settingsStore.setUiStyle("solid") }
                                showUiStyleDialog = false
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = accent,
                                unselectedColor = Color.White.copy(alpha = 0.5f)
                            )
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "Classic Modern (Solid Material 3)",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isSolid) FontWeight.Bold else FontWeight.Normal,
                                    shadow = LiquidGlassTokens.SubtleTextShadow
                                )
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Clean pitch AMOLED / dark solid surfaces, high contrast solid cards, and standard Material 3 layouts.",
                                color = Color.White.copy(alpha = 0.75f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }

    // 3. Custom Accent Color Dialog (GlassDialog)
    if (showAccentColorDialog) {
        var customHex by remember { mutableStateOf(currentAccentHex) }
        GlassDialog(
            onDismissRequest = { showAccentColorDialog = false },
            title = "Customize Accent Color",
            confirmButton = {
                GlassPillButton(
                    text = "Apply",
                    accentColor = accent,
                    selected = true,
                    onClick = {
                        scope.launch { settingsStore.setAccentColor(customHex.trim()) }
                        showAccentColorDialog = false
                        Toast.makeText(context, "Theme accent updated!", Toast.LENGTH_SHORT).show()
                    }
                )
            },
            dismissButton = {
                GlassPillButton(text = "Cancel", onClick = { showAccentColorDialog = false })
            }
        ) {
            Column {
                Text("Select a Preset:", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(accentPresets) { (name, hex) ->
                        val color = parseHexColor(hex)
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (customHex.equals(hex, ignoreCase = true)) 2.5.dp else 1.dp,
                                    color = if (customHex.equals(hex, ignoreCase = true)) Color.White else Color.White.copy(alpha = 0.3f),
                                    shape = CircleShape
                                )
                                .clickable { customHex = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (customHex.equals(hex, ignoreCase = true)) {
                                Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Or enter custom HEX Code:", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = customHex,
                    onValueChange = { customHex = it },
                    singleLine = true,
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(parseHexColor(customHex))
                                .border(1.dp, Color.White, CircleShape)
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = accent,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        focusedContainerColor = Color.White.copy(alpha = 0.08f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.05f)
                    ),
                    shape = RoundedCornerShape(14.dp)
                )
            }
        }
    }

    // 4. Rainbow Color Picker Dialog
    if (showRainbowPicker) {
        com.zyfen.music.ui.components.RainbowColorPickerDialog(
            initialColorHex = currentAccentHex,
            onDismiss = { showRainbowPicker = false },
            onColorSelected = { hex ->
                scope.launch { settingsStore.setAccentColor(hex) }
                showRainbowPicker = false
                Toast.makeText(context, "Accent color applied!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // 5. Streaming Quality Dialog (GlassDialog)
    if (showQualityDialog) {
        val qualities = listOf(
            "320" to "Ultra 320 kbps (Best Studio Fidelity)",
            "160" to "High 160 kbps (Balanced Data & Quality)",
            "96" to "Data Saver 96 kbps (Lowest Latency)"
        )
        GlassDialog(
            onDismissRequest = { showQualityDialog = false },
            title = "Streaming Quality",
            dismissButton = {
                GlassPillButton(text = "Close", onClick = { showQualityDialog = false })
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                qualities.forEach { (key, label) ->
                    val isSelected = currentQuality == key
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        fillAlpha = if (isSelected) 0.18f else 0.06f,
                        accentGlow = if (isSelected) accent else null,
                        onClick = {
                            scope.launch { settingsStore.setAudioQuality(key) }
                            showQualityDialog = false
                        },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    scope.launch { settingsStore.setAudioQuality(key) }
                                    showQualityDialog = false
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = accent,
                                    unselectedColor = Color.White.copy(alpha = 0.45f)
                                )
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                label,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    shadow = LiquidGlassTokens.SubtleTextShadow
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    // 6. Equalizer & Reverb Sheet
    if (showEqDialog) {
        com.zyfen.music.ui.components.EqualizerReverbSheet(
            onDismiss = { showEqDialog = false }
        )
    }

    // 7. Sleep Timer Dialog (GlassDialog)
    if (showSleepDialog) {
        val timerOptions = listOf(0 to "Off", 15 to "15 minutes", 30 to "30 minutes", 45 to "45 minutes", 60 to "60 minutes")
        GlassDialog(
            onDismissRequest = { showSleepDialog = false },
            title = "Sleep Timer",
            dismissButton = {
                GlassPillButton(text = "Close", onClick = { showSleepDialog = false })
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                timerOptions.forEach { (mins, label) ->
                    val isSelected = selectedSleepTimer == mins
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        fillAlpha = if (isSelected) 0.18f else 0.06f,
                        accentGlow = if (isSelected) accent else null,
                        onClick = {
                            selectedSleepTimer = mins
                            showSleepDialog = false
                            if (mins > 0) {
                                Toast.makeText(context, "Music will stop in $mins minutes", Toast.LENGTH_SHORT).show()
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    selectedSleepTimer = mins
                                    showSleepDialog = false
                                    if (mins > 0) {
                                        Toast.makeText(context, "Music will stop in $mins minutes", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = accent,
                                    unselectedColor = Color.White.copy(alpha = 0.45f)
                                )
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                label,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    shadow = LiquidGlassTokens.SubtleTextShadow
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsCategoryHeader(title: String) {
    val accent = LocalAccentColor.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = accent.copy(alpha = 0.20f),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.50f))
        ) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    shadow = LiquidGlassTokens.SubtleTextShadow
                ),
                color = Color.White,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun SettingsTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    GlassTile(
        icon = icon,
        title = title,
        subtitle = subtitle,
        onClick = onClick
    )
}
