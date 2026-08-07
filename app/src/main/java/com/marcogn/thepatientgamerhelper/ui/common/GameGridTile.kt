package com.marcogn.thepatientgamerhelper.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

private const val PLACEHOLDER_ASPECT_RATIO = 2f / 3f

/**
 * Grid-mode tile shared by the library and the backlog list detail screen (Fase 8). A cover
 * renders at its own natural aspect ratio (`ContentScale.FillWidth`, no forced height) so square
 * and portrait box-art sit next to each other without wasted vertical padding above/below the
 * shorter ones — meant to be used inside a staggered grid (`LazyVerticalStaggeredGrid`), not a
 * uniform-row `LazyVerticalGrid`, otherwise every row still stretches to its tallest tile. Only the
 * no-cover placeholder falls back to a fixed ratio, since there's no intrinsic image size to derive
 * a shape from.
 */
@Composable
fun GameGridTile(
    title: String,
    subtitle: String?,
    coverImagePath: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Column {
            if (coverImagePath != null) {
                AsyncImage(
                    model = coverImagePath,
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(PLACEHOLDER_ASPECT_RATIO)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.SportsEsports,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(0.4f).aspectRatio(1f),
                    )
                }
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
