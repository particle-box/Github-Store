package zed.rainxch.githubstore.app.di

import org.koin.core.module.Module
import org.koin.dsl.module
import zed.rainxch.githubstore.feature.details.data.AndroidDownloader
import zed.rainxch.githubstore.feature.details.data.AndroidFileLocationsProvider
import zed.rainxch.githubstore.feature.details.data.AndroidInstaller
import zed.rainxch.githubstore.feature.install.Downloader
import zed.rainxch.githubstore.feature.install.FileLocationsProvider
import zed.rainxch.githubstore.feature.install.Installer

actual val platformModule: Module = module {
    single<Downloader> {
        AndroidDownloader(
            context = get(),
            files = get()
        )
    }

    single<Installer> {
        AndroidInstaller(
            context = get(),
            files = get()
        )
    }

    single<FileLocationsProvider> {
        AndroidFileLocationsProvider(context = get())
    }
}