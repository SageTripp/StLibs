package com.st.downloader

import android.content.Context
import androidx.compose.runtime.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.launchIn
import okhttp3.Response

class Downloader(
    private val ctx: Context,
    private val api: suspend (url: String) -> Response
) {
    fun download(
        url: String,
        config: DownloadConfig.() -> Unit = {}
    ): DownloaderHandler {
        val downloadConfig = DownloadConfig().apply(config).apply {
            config {
                if (null == invoker) {
                    request(api)
                }
            }
        }
        return DownloaderHandlerImpl(url, downloadConfig, ctx)
    }
}

interface DownloaderHandler {
    val flow: SharedFlow<DownloadStatus>
    suspend fun start()
}

class DownloaderHandlerImpl(
    private val url: String,
    private val config: DownloadConfig,
    private val ctx: Context
) : DownloaderHandler {
    override val flow: MutableSharedFlow<DownloadStatus> = MutableSharedFlow()

    override suspend fun start() {
        coroutineScope {
            val oriConfig = config
            ctx.download(url, config/*.apply {
                listener {
                    success {
                        oriConfig.listener.success?.invoke(it)
                        flow.tryEmit(DownloadStatus.DownloadSuccess(it))
                    }
                    failure {
                        oriConfig.listener.failure?.invoke(it)
                        flow.tryEmit(DownloadStatus.DownloadFail(it))
                    }
                    process { process, current, total ->
                        oriConfig.listener.process?.invoke(process, current, total)
                        flow.tryEmit(DownloadStatus.Downloading(process, current, total))
                    }
                }
            }*/).launchIn(this)
        }
    }
}

@Composable
fun DownloaderHandler.collectAsState(): DownloadStatus? {
    var state: DownloadStatus? by remember { mutableStateOf(null) }
    LaunchedEffect(Unit) {
        flow.collectLatest { state = it }
    }
    return state
}

val LocalDownloader = staticCompositionLocalOf<Downloader> {
    error("CompositionLocal LocalDownloader not present")
}

@Composable
fun ProvideDownloader(
    downloader: Downloader,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalDownloader provides downloader) {
        content()
    }
}