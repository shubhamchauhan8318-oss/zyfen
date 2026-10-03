package com.zyfen.music.ui.components

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zyfen.music.ui.theme.ZyfenNeon
import com.zyfen.music.ui.theme.ZyfenPurple
import com.zyfen.music.ui.theme.ZyfenSurface
import com.zyfen.music.ui.theme.ZyfenText
import com.zyfen.music.ui.theme.ZyfenTextSecondary
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePlaylistCoverDialog(
    playlistId: String,
    currentArtworkUrl: String?,
    songArtworks: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onSaveCover: (String?) -> Unit
) {
    val context = LocalContext.current
    var showUrlDialog by remember { mutableStateOf(false) }
    var inputUrl by remember { mutableStateOf("") }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val localPath = copyImageToAppStorage(context, playlistId, uri)
            onSaveCover(localPath)
            onDismiss()
        }
    }

    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("Enter Image URL", color = ZyfenText) },
            text = {
                OutlinedTextField(
                    value = inputUrl,
                    onValueChange = { inputUrl = it },
                    label = { Text("https://example.com/cover.jpg") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (inputUrl.isNotBlank()) {
                        onSaveCover(inputUrl.trim())
                    }
                    showUrlDialog = false
                    onDismiss()
                }) {
                    Text("Apply", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) {
                    Text("Cancel", color = ZyfenTextSecondary)
                }
            },
            containerColor = ZyfenSurface
        )
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ZyfenSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Text(
                "Change Playlist Photo",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = ZyfenText
            )
            Spacer(Modifier.height(14.dp))

            ListItem(
                headlineContent = { Text("Choose from Phone Gallery", color = ZyfenText) },
                supportingContent = { Text("Select any photo or album cover from your device", color = ZyfenTextSecondary) },
                leadingContent = {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                modifier = Modifier.clickable {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
            )

            ListItem(
                headlineContent = { Text("Enter Web Image URL", color = ZyfenText) },
                supportingContent = { Text("Paste link to any online artwork or Spotify image", color = ZyfenTextSecondary) },
                leadingContent = {
                    Icon(Icons.Filled.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                modifier = Modifier.clickable {
                    inputUrl = currentArtworkUrl.orEmpty()
                    showUrlDialog = true
                },
                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
            )

            val validArtworks = songArtworks.filter { it.isNotBlank() }.distinct()
            if (validArtworks.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Or choose track artwork:",
                    style = MaterialTheme.typography.labelMedium,
                    color = ZyfenTextSecondary
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(validArtworks.take(16)) { art ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .size(64.dp)
                                .clickable {
                                    onSaveCover(art)
                                    onDismiss()
                                }
                        ) {
                            Artwork(art, Modifier.fillMaxSize(), corner = 12)
                        }
                    }
                }
            }

            if (!currentArtworkUrl.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                ListItem(
                    headlineContent = { Text("Reset to Default Generated Cover", color = MaterialTheme.colorScheme.error) },
                    leadingContent = {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    },
                    modifier = Modifier.clickable {
                        onSaveCover(null)
                        onDismiss()
                    },
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                )
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

fun copyImageToAppStorage(context: Context, playlistId: String, uri: Uri): String {
    return try {
        val coversDir = File(context.filesDir, "playlist_covers").apply { if (!exists()) mkdirs() }
        val targetFile = File(coversDir, "cover_${playlistId.replace(Regex("[^a-zA-Z0-9_-]"), "_")}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        }
        Uri.fromFile(targetFile).toString()
    } catch (_: Exception) {
        uri.toString()
    }
}
