package zed.rainxch.githubstore.feature.details.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.compose.components.MarkdownComponents
import com.mikepenz.markdown.model.ImageTransformer
import com.mikepenz.markdown.model.markdownDimens
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import zed.rainxch.githubstore.core.presentation.theme.GithubStoreTheme
import zed.rainxch.githubstore.feature.details.presentation.components.AppHeader
import zed.rainxch.githubstore.feature.details.presentation.components.SmartInstallButton
import zed.rainxch.githubstore.feature.details.presentation.utils.rememberMarkdownColors
import zed.rainxch.githubstore.feature.details.presentation.utils.rememberMarkdownTypography

@Composable
fun DetailsRoot(
    onNavigateBack: () -> Unit,
    viewModel: DetailsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    DetailsScreen(
        state = state,
        onAction = { action ->
            when (action) {
                DetailsAction.OnNavigateBackClick -> {
                    onNavigateBack()
                }

                else -> {
                    viewModel.onAction(action)
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    state: DetailsState,
    onAction: (DetailsAction) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {}, // Kept empty for cleaner look, title is in content
                navigationIcon = {
                    IconButton(onClick = { onAction(DetailsAction.OnNavigateBackClick) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->

        // Error / Loading States
        if (state.isLoading) {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            return@Scaffold
        }

        if (state.errorMessage != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Error loading details", style = MaterialTheme.typography.titleMedium)
                    Text(state.errorMessage, color = MaterialTheme.colorScheme.error)
                    Button(onClick = { onAction(DetailsAction.Retry) }) { Text("Retry") }
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {

            // 1. Header Section
            item {
                if (state.repository != null) {
                    AppHeader(state.repository, state.stats)
                }
            }

            // 2. Primary Action (Install)
            item {
                SmartInstallButton(
                    isDownloading = state.isDownloading,
                    isInstalling = state.isInstalling,
                    progress = state.downloadProgressPercent,
                    primaryAsset = state.primaryAsset,
                    state = state,
                    onClick = { onAction(DetailsAction.InstallPrimary) }
                )

                // Secondary Links
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(onClick = { onAction(DetailsAction.OpenRepoInBrowser) }) {
                        Text("View Source")
                    }
                    TextButton(onClick = { onAction(DetailsAction.OpenAuthorInBrowser) }) {
                        Text("Author Profile")
                    }
                }
            }

            // 3. What's New (Latest Release)
            if (state.latestRelease != null) {
                item {
                    Text(
                        "What's New",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    state.latestRelease.tagName,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    state.latestRelease.publishedAt.take(10),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(8.dp))

                            Markdown(
                                content = state.latestRelease.description ?: "No release notes.",
                                colors = rememberMarkdownColors(),
                                typography = rememberMarkdownTypography(),
                                modifier = Modifier.fillMaxWidth(),
                                flavour = GFMFlavourDescriptor(),
                                imageTransformer = Coil3ImageTransformerImpl,
                            )
                        }
                    }
                }
            }

            // 4. README (The star of the show)
            if (!state.readmeMarkdown.isNullOrBlank()) {
                item {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "About this app",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                item {
                    // MARKDOWN RENDERER
                    // We wrap it in a surface to ensure background consistency
                    Surface(
                        color = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onBackground
                    ) {
                        Markdown(
                            content = state.readmeMarkdown,
                            colors = rememberMarkdownColors(),
                            typography = rememberMarkdownTypography(),
                            modifier = Modifier.fillMaxWidth(),
                            flavour = GFMFlavourDescriptor(),
                            imageTransformer = Coil3ImageTransformerImpl,
                        )
                    }
                }
            }

            // 5. Technical Logs (Optional, good for debugging your feature)
            if (state.installLogs.isNotEmpty()) {
                item {
                    HorizontalDivider()
                    Text(
                        "Logs",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                items(state.installLogs) { log ->
                    Text(
                        text = "> ${log.result}: ${log.assetName}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = if (log.result.startsWith("Error")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun Preview() {
    GithubStoreTheme {
        DetailsScreen(
            state = DetailsState(),
            onAction = {}
        )
    }
}