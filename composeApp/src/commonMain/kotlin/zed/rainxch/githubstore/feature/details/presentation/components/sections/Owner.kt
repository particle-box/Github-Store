package zed.rainxch.githubstore.feature.details.presentation.components.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import githubstore.composeapp.generated.resources.Res
import githubstore.composeapp.generated.resources.ic_github
import io.github.fletchmckee.liquid.liquefiable
import org.jetbrains.compose.resources.painterResource
import zed.rainxch.githubstore.core.domain.model.GithubUserProfile
import zed.rainxch.githubstore.feature.details.presentation.DetailsAction
import zed.rainxch.githubstore.feature.details.presentation.utils.LocalTopbarLiquidState

fun LazyListScope.author(
    author: GithubUserProfile?,
    onAction: (DetailsAction) -> Unit
) {
    item {
        val liquidState = LocalTopbarLiquidState.current

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Author",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .padding(bottom = 12.dp)
                .liquefiable(liquidState),
            fontWeight = FontWeight.Bold,
        )

        OutlinedCard(
            onClick = {
                author?.id?.toInt()?.let { authorId ->
                    onAction(DetailsAction.OpenAuthorInApp(authorId))
                }
            },
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
            ),
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = author?.avatarUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .liquefiable(liquidState),
                    contentScale = ContentScale.Crop,
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    author?.login?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.liquefiable(liquidState)
                        )
                    }

                    author?.bio?.let { bio ->
                        Text(
                            text = bio,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 2,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.liquefiable(liquidState)
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    author?.htmlUrl?.let {
                        Row(
                            modifier = Modifier.clickable {
                                onAction(DetailsAction.OpenAuthorInBrowser)
                            },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_github),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )

                            Text(
                                text = "Profile",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                if(false) { // For now invisible
                    IconButton(
//                        shapes = IconButtonDefaults.shapes(),
                        onClick = {
                            author?.id?.toInt()?.let { authorId ->
                                onAction(DetailsAction.OpenAuthorInApp(authorId))
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Navigate Back",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}