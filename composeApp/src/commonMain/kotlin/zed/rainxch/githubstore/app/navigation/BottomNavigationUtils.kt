package zed.rainxch.githubstore.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import githubstore.composeapp.generated.resources.Res
import githubstore.composeapp.generated.resources.favourites
import githubstore.composeapp.generated.resources.installed_apps
import githubstore.composeapp.generated.resources.search_repositories_hint
import githubstore.composeapp.generated.resources.settings_title
import org.jetbrains.compose.resources.StringResource

data class BottomNavigationItem(
    val titleRes: StringResource,
    val iconRes: ImageVector,
    val screen: GithubStoreGraph
)

object BottomNavigationUtils {
    fun items(): List<BottomNavigationItem> {
        return listOf(
            BottomNavigationItem(
                titleRes = Res.string.search_repositories_hint,
                iconRes = Icons.Outlined.Search,
                screen = GithubStoreGraph.SearchScreen
            ),
            BottomNavigationItem(
                titleRes = Res.string.favourites,
                iconRes = Icons.Outlined.Favorite,
                screen = GithubStoreGraph.FavouritesScreen
            ),
            BottomNavigationItem(
                titleRes = Res.string.installed_apps,
                iconRes = Icons.Outlined.Apps,
                screen = GithubStoreGraph.AppsScreen
            ),
            BottomNavigationItem(
                titleRes = Res.string.settings_title,
                iconRes = Icons.Outlined.Settings,
                screen = GithubStoreGraph.SettingsScreen
            )
        )
    }

    fun allowedScreens(): List<GithubStoreGraph> {
        return listOf(GithubStoreGraph.HomeScreen)
    }
}