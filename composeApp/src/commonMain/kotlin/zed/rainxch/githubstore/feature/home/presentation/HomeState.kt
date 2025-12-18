package zed.rainxch.githubstore.feature.home.presentation

import zed.rainxch.githubstore.core.domain.model.GithubRepoSummary
import zed.rainxch.githubstore.feature.home.presentation.model.HomeCategory

data class HomeState(
    val repos: List<GithubRepoSummary> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
    val hasMorePages: Boolean = true,
    val currentCategory: HomeCategory = HomeCategory.TRENDING
)