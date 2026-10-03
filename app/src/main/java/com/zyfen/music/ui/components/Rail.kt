package com.zyfen.music.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zyfen.music.ui.theme.ZyfenText
import com.zyfen.music.ui.theme.ZyfenTextDisabled
import com.zyfen.music.ui.theme.ZyfenTextSecondary

/** Swaps measured width/height so rotated(-90) content occupies a vertical strip. */
private fun Modifier.vertical() = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(maxWidth = Int.MAX_VALUE))
    layout(placeable.height, placeable.width) {
        placeable.place(
            x = -(placeable.width / 2 - placeable.height / 2),
            y = -(placeable.height / 2 - placeable.width / 2)
        )
    }
}

data class RailTab(val label: String, val icon: ImageVector)

@Composable
fun ZyfenNavigationRail(
    tabs: List<RailTab>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    onTopIconClick: () -> Unit,
    topIcon: ImageVector
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(28.dp))
        Icon(
            topIcon,
            contentDescription = "Settings",
            tint = ZyfenTextSecondary,
            modifier = Modifier
                .padding(12.dp)
                .size(22.dp)
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onTopIconClick)
        )
        Spacer(Modifier.height(12.dp))

        tabs.forEachIndexed { index, tab ->
            val selected = index == selectedIndex
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(onClick = { onSelected(index) })
                    .padding(horizontal = 8.dp)
            ) {
                Icon(
                    tab.icon,
                    contentDescription = null,
                    tint = ZyfenText,
                    modifier = Modifier
                        .vertical()
                        .rotate(-90f)
                        .size(16.dp)
                        .graphicsLayer { alpha = if (selected) 1f else 0f }
                )
                Text(
                    tab.label,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = if (selected) ZyfenText else ZyfenTextDisabled,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .vertical()
                        .rotate(-90f)
                        .padding(horizontal = 14.dp)
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
