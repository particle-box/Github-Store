package zed.rainxch.githubstore.feature.search.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import zed.rainxch.githubstore.core.domain.model.GithubRepoSummary
import zed.rainxch.githubstore.feature.home.data.model.GithubRepoNetworkModel
import zed.rainxch.githubstore.feature.home.data.model.GithubRepoSearchResponse
import zed.rainxch.githubstore.feature.home.data.model.toSummary
import zed.rainxch.githubstore.feature.home.domain.repository.PaginatedRepos
import zed.rainxch.githubstore.feature.search.domain.model.SearchPlatformType
import zed.rainxch.githubstore.feature.search.domain.repository.SearchRepository

class SearchRepositoryImpl(
    private val githubNetworkClient: HttpClient,
) : SearchRepository {
    private class LruCache<K, V>(private val maxSize: Int) {
        private val map = LinkedHashMap<K, V?>()
        private val order = ArrayDeque<K>()
        fun get(key: K): V? {
            val value = map[key]
            if (value != null || map.containsKey(key)) {
                order.remove(key)
                order.addLast(key)
            }
            return value
        }
        fun put(key: K, value: V?) {
            map[key] = value
            order.remove(key)
            order.addLast(key)
            while (order.size > maxSize) {
                val oldest = order.removeFirst()
                map.remove(oldest)
            }
        }
        fun contains(key: K): Boolean = map.containsKey(key)
    }

    private val releaseCheckCache = LruCache<String, GithubRepoSummary>(maxSize = 500)
    private val cacheMutex = Mutex()

    override fun searchRepositories(
        query: String,
        searchPlatformType: SearchPlatformType,
        page: Int
    ): Flow<PaginatedRepos> = channelFlow {
        val perPage = 30
        val searchQuery = buildSearchQuery(query, searchPlatformType)

        try {
            val response: GithubRepoSearchResponse =
                githubNetworkClient.get("/search/repositories") {
                    parameter("q", searchQuery)
                    parameter("per_page", perPage)
                    parameter("page", page)
                }.body()

            val total = response.totalCount
            val baseHasMore = (page * perPage) < total && response.items.isNotEmpty()

            if (page == 1) {
                val tunedTargetCount = 24
                val tunedMinFirstEmit = 4
                val tunedVerifyConcurrency = 12
                val tunedPerCheckTimeoutMs = 1400L
                val tunedMaxBackfillPages = 3
                val tunedEarlyFallbackTimeoutMs = 0L
                val tunedCandidatesPerPage = 50
                val rawFallbackFirstItems = emptyList<GithubRepoSummary>()

                val strict = runStrictFirstRender(
                    firstPageItems = response.items,
                    searchQuery = searchQuery,
                    perPage = perPage,
                    startPage = page,
                    searchPlatformType = searchPlatformType,
                    targetCount = tunedTargetCount,
                    minFirstEmit = tunedMinFirstEmit,
                    verifyConcurrency = tunedVerifyConcurrency,
                    perCheckTimeoutMs = tunedPerCheckTimeoutMs,
                    maxBackfillPages = tunedMaxBackfillPages,
                    earlyFallbackTimeoutMs = tunedEarlyFallbackTimeoutMs,
                    rawFallbackItems = rawFallbackFirstItems,
                    candidatesPerPage = tunedCandidatesPerPage
                ) { growingVerified ->
                    if (growingVerified.isNotEmpty()) {
                        send(
                            PaginatedRepos(
                                repos = growingVerified,
                                hasMore = true,
                                nextPageIndex = page + 1,
                                totalCount = total
                            )
                        )
                    }
                }

                send(
                    PaginatedRepos(
                        repos = strict.verified,
                        hasMore = strict.hasMore,
                        nextPageIndex = strict.nextPageIndex,
                        totalCount = total
                    )
                )
            } else {
                if (response.items.isNotEmpty()) {
                    val semaphore = Semaphore(10)
                    val timeoutMs = 2000L

                    val deferredChecks = coroutineScope {
                        response.items.map { repo ->
                            async {
                                try {
                                    semaphore.withPermit {
                                        withTimeoutOrNull(timeoutMs) {
                                            checkRepoHasInstallersCached(repo, searchPlatformType)
                                        }
                                    }
                                } catch (e: CancellationException) {
                                    null
                                }
                            }
                        }
                    }

                    val filtered = buildList<GithubRepoSummary> {
                        for (i in response.items.indices) {
                            currentCoroutineContext().ensureActive()
                            val result = try {
                                deferredChecks[i].await()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                null
                            }
                            if (result != null) add(result)
                        }
                    }

                    if (filtered.isNotEmpty() || !baseHasMore) {
                        send(
                            PaginatedRepos(
                                repos = filtered,
                                hasMore = baseHasMore,
                                nextPageIndex = page + 1,
                                totalCount = total
                            )
                        )
                    } else {
                        send(
                            PaginatedRepos(
                                repos = emptyList(),
                                hasMore = true,
                                nextPageIndex = page + 1,
                                totalCount = total
                            )
                        )
                    }
                } else {
                    send(
                        PaginatedRepos(
                            repos = emptyList(),
                            hasMore = false,
                            nextPageIndex = page + 1,
                            totalCount = total
                        )
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        }
    }.flowOn(Dispatchers.IO)

    private fun buildSearchQuery(
        userQuery: String,
        searchPlatformType: SearchPlatformType
    ): String {
        val clean = userQuery.trim()
        val q = if (clean.isBlank()) {
            "stars:>100"
        } else {
            if (clean.any { it.isWhitespace() }) "\"$clean\"" else clean
        }
        val scope = " in:name,description,readme"
        val common = " archived:false fork:false"

        val platformHints = when (searchPlatformType) {
            SearchPlatformType.All -> ""
            SearchPlatformType.Android -> " (topic:android OR apk in:name,description,readme)"
            SearchPlatformType.Windows -> " (topic:windows OR exe in:name,description,readme OR msi in:name,description,readme)"
            SearchPlatformType.Macos -> " (topic:macos OR dmg in:name,description,readme OR pkg in:name,description,readme)"
            SearchPlatformType.Linux -> " (topic:linux OR appimage in:name,description,readme OR deb in:name,description,readme)"
        }

        return ("$q$scope$common" + platformHints).trim()
    }

    private data class StrictResult(
        val verified: List<GithubRepoSummary>,
        val hasMore: Boolean,
        val nextPageIndex: Int
    )

    private suspend fun runStrictFirstRender(
        firstPageItems: List<GithubRepoNetworkModel>,
        searchQuery: String,
        perPage: Int,
        startPage: Int,
        searchPlatformType: SearchPlatformType,
        targetCount: Int,
        minFirstEmit: Int,
        verifyConcurrency: Int,
        perCheckTimeoutMs: Long,
        maxBackfillPages: Int,
        earlyFallbackTimeoutMs: Long,
        rawFallbackItems: List<GithubRepoSummary>,
        candidatesPerPage: Int,
        onEarlyEmit: suspend (growingVerified: List<GithubRepoSummary>) -> Unit
    ): StrictResult {
        return coroutineScope {
            var lastFetchedPage = startPage
            val verified = mutableListOf<GithubRepoSummary>()
            var emittedOnce = false

            val fallbackJob = if (rawFallbackItems.isNotEmpty() && earlyFallbackTimeoutMs > 0) {
                launch {
                    delay(earlyFallbackTimeoutMs)
                    if (!emittedOnce) {
                        emittedOnce = true
                        onEarlyEmit(rawFallbackItems)
                    }
                }
            } else null

            suspend fun verifyBatch(items: List<GithubRepoNetworkModel>) {
                val semaphore = Semaphore(verifyConcurrency)

                val deferred = coroutineScope {
                    items.map { repo ->
                        async {
                            try {
                                semaphore.withPermit {
                                    withTimeoutOrNull(perCheckTimeoutMs) {
                                        checkRepoHasInstallersCached(repo, searchPlatformType)
                                    }
                                }
                            } catch (e: CancellationException) {
                                null
                            }
                        }
                    }
                }

                for (i in items.indices) {
                    currentCoroutineContext().ensureActive()
                    val res = try {
                        deferred[i].await()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) { null }
                    if (res != null) {
                        verified.add(res)
                        if (!emittedOnce && (verified.size >= minFirstEmit)) {
                            emittedOnce = true
                            fallbackJob?.cancel()
                            onEarlyEmit(verified.toList())
                        }
                        if (verified.size >= targetCount) return
                    }
                }
            }

            verifyBatch(firstPageItems.take(candidatesPerPage))

            var hasMore = true
            var nextPageIndex = startPage + 1
            var pagesFetched = 0
            while (verified.size < targetCount && hasMore && pagesFetched < maxBackfillPages) {
                val nextPage = lastFetchedPage + 1
                val resp: GithubRepoSearchResponse =
                    githubNetworkClient.get("/search/repositories") {
                        parameter("q", searchQuery)
                        parameter("per_page", perPage)
                        parameter("page", nextPage)
                    }.body()

                if (resp.items.isEmpty()) {
                    hasMore = false
                    nextPageIndex = nextPage
                    break
                }

                verifyBatch(resp.items.take(candidatesPerPage))

                lastFetchedPage = nextPage
                pagesFetched++

                hasMore = (lastFetchedPage * perPage) < resp.totalCount && resp.items.isNotEmpty()
                nextPageIndex = lastFetchedPage + 1
            }

            if (!emittedOnce) {
                fallbackJob?.cancel()
                if (verified.isNotEmpty()) {
                    onEarlyEmit(verified.toList())
                }
            }

            StrictResult(
                verified = verified.toList(),
                hasMore = hasMore,
                nextPageIndex = nextPageIndex
            )
        }
    }

    private fun calculateScore(
        repo: GithubRepoNetworkModel,
        targetPlatform: SearchPlatformType
    ): Int {
        if (targetPlatform == SearchPlatformType.All) return 10

        var score = 5
        val topics = repo.topics.orEmpty().map { it.lowercase() }
        val language = repo.language?.lowercase()
        val desc = repo.description?.lowercase() ?: ""

        when (targetPlatform) {
            SearchPlatformType.Android -> {
                if (topics.contains("android")) score += 10
                if (topics.contains("mobile")) score += 5
                if (language in setOf("kotlin", "java")) score += 5
                if (desc.contains("android") || desc.contains("apk")) score += 3
            }
            SearchPlatformType.Windows -> {
                if (topics.any { it in setOf("windows", "desktop", "electron") }) score += 10
                if (language in setOf("c#", "c++", "rust")) score += 5
                if (desc.contains("windows") || desc.contains("desktop")) score += 3
            }
            SearchPlatformType.Macos -> {
                if (topics.any { it in setOf("macos", "desktop", "electron") }) score += 10
                if (language in setOf("swift", "objective-c", "c++")) score += 5
                if (desc.contains("macos") || desc.contains("mac")) score += 3
            }
            SearchPlatformType.Linux -> {
                if (topics.any { it in setOf("linux", "desktop", "electron") }) score += 10
                if (language in setOf("rust", "c++", "c")) score += 5
                if (desc.contains("linux")) score += 3
            }
            SearchPlatformType.All -> score = 10
        }

        return score
    }

    private suspend fun checkRepoHasInstallers(
        repo: GithubRepoNetworkModel,
        targetPlatform: SearchPlatformType
    ): GithubRepoSummary? {
        fun assetMatchesForPlatform(nameRaw: String, platform: SearchPlatformType): Boolean {
            val name = nameRaw.lowercase()
            return when (platform) {
                SearchPlatformType.All -> name.endsWith(".apk") ||
                    name.endsWith(".msi") || name.endsWith(".exe") ||
                    name.endsWith(".dmg") || name.endsWith(".pkg") ||
                    name.endsWith(".appimage") || name.endsWith(".deb") || name.endsWith(".rpm")
                SearchPlatformType.Android -> name.endsWith(".apk")
                SearchPlatformType.Windows -> name.endsWith(".exe") || name.endsWith(".msi")
                SearchPlatformType.Macos -> name.endsWith(".dmg") || name.endsWith(".pkg")
                SearchPlatformType.Linux -> name.endsWith(".appimage") || name.endsWith(".deb") || name.endsWith(".rpm")
            }
        }

        return try {
            val latest: GithubReleaseNetworkModel? = githubNetworkClient
                .get("/repos/${repo.owner.login}/${repo.name}/releases/latest") {
                    header("Accept", "application/vnd.github.v3+json")
                }
                .body()

            val latestMatches = latest != null && latest.assets.any { asset -> assetMatchesForPlatform(asset.name, targetPlatform) }
            if (latestMatches) repo.toSummary() else null
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun checkRepoHasInstallersCached(
        repo: GithubRepoNetworkModel,
        targetPlatform: SearchPlatformType
    ): GithubRepoSummary? {
        val key = "${repo.owner.login}/${repo.name}:LATEST_PLATFORM_${targetPlatform.name}"
        val cached = cacheMutex.withLock {
            if (releaseCheckCache.contains(key)) releaseCheckCache.get(key) else null
        }
        if (cached != null || cacheMutex.withLock { releaseCheckCache.contains(key) && releaseCheckCache.get(key) == null }) {
            return cached
        }

        val result = checkRepoHasInstallers(repo, targetPlatform)
        cacheMutex.withLock {
            releaseCheckCache.put(key, result)
        }
        return result
    }

    @Serializable
    private data class GithubReleaseNetworkModel(
        val draft: Boolean? = null,
        val prerelease: Boolean? = null,
        val assets: List<AssetNetworkModel>
    )

    @Serializable
    private data class AssetNetworkModel(
        val name: String
    )
}