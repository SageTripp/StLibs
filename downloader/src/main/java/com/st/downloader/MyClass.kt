package com.st.downloader

import androidx.compose.runtime.*
import kotlinx.coroutines.flow.onEach
import okhttp3.Response


@Suppress("unused")
@Composable
fun rememberDownload(
    url: String,
    download: (suspend (url: String) -> Response)? = null,
    config: DownloadConfig.() -> Unit = {}
): DownloadStatus? {
    val downloader = LocalDownloader.current
    var state: DownloadStatus? by remember { mutableStateOf(null) }
    LaunchedEffect(url) {
        val handler = downloader.download(url) {
            this.config()
            config {
                if (null != download) {
                    request(download)
                }
            }
        }
        handler.flow.onEach {
            state = it
        }
    }
    return state
}

