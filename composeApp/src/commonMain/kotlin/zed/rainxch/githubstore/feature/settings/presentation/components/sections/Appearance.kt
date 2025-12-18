package zed.rainxch.githubstore.feature.settings.presentation.components.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.ui.tooling.preview.Preview
import zed.rainxch.githubstore.core.presentation.model.AppTheme
import zed.rainxch.githubstore.core.presentation.theme.isDynamicColorAvailable
import zed.rainxch.githubstore.feature.settings.presentation.SettingsAction

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun LazyListScope.appearance(
    selectedThemeColor: AppTheme,
    onThemeColorSelected: (AppTheme) -> Unit,
) {
    item {
        Text(
            text = "APPEARANCE",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.outline,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp)
        )

        Spacer(Modifier.height(8.dp))

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = "Theme Color",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(Modifier.height(8.dp))

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val availableThemes = if (isDynamicColorAvailable()) {
                        AppTheme.entries
                    } else {
                        AppTheme.entries.filter { it != AppTheme.DYNAMIC }
                    }

                    items(availableThemes) { theme ->
                        Column(
                            modifier = Modifier
                                .clickable(onClick = {
                                    onThemeColorSelected(theme)
                                }),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(50.dp)
                                    .clip(
                                        if (selectedThemeColor == theme) {
                                            MaterialShapes.Cookie9Sided.toShape()
                                        } else CircleShape
                                    )
                                    .background(
                                        color = theme.primaryColor
                                            ?: MaterialTheme.colorScheme.primary
                                    )
                                    .then(
                                        if (theme == AppTheme.DYNAMIC) {
                                            Modifier.border(
                                                2.dp,
                                                MaterialTheme.colorScheme.outline,
                                                CircleShape
                                            )
                                        } else Modifier
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selectedThemeColor == theme) {
                                    Icon(
                                        imageVector = Icons.Default.Done,
                                        contentDescription = "Selected color : ${theme.displayName}",
                                        modifier = Modifier.size(24.dp),
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }

                            Text(
                                text = theme.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}