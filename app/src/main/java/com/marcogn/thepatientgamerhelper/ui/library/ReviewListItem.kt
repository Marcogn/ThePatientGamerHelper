package com.marcogn.thepatientgamerhelper.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marcogn.thepatientgamerhelper.domain.model.Review
import com.marcogn.thepatientgamerhelper.ui.common.CoverThumbnail
import com.marcogn.thepatientgamerhelper.ui.common.RatingBadge
import com.marcogn.thepatientgamerhelper.ui.common.displayName

@Composable
fun ReviewListItem(review: Review, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverThumbnail(review.coverImagePath)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = review.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitleFor(review),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = review.status.displayName(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }

            RatingBadge(rating = review.rating)
        }
    }
}

private fun subtitleFor(review: Review): String {
    val parts = buildList {
        if (review.platforms.isNotEmpty()) add(review.platforms.joinToString { it.name })
        if (review.genres.isNotEmpty()) add(review.genres.joinToString { it.name })
    }
    return if (parts.isEmpty()) "—" else parts.joinToString(" · ")
}
