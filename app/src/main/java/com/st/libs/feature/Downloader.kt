package com.st.libs.feature

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.st.downloader.*
import kotlinx.coroutines.launch
import rxhttp.st.libs.RxHttp
import rxhttp.toOkResponse

val url =
    "http://rki1aq2h4.sabkt.gdipper.com/__UNI__4E72113.wgt"
//    "https://anquan.obs.cn-north-1.myhuaweicloud.com/apk/20221012/app-release.apk"

@Composable
fun TestDownload(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current

    ProvideDownloader(downloader = Downloader(ctx) {
        RxHttp.get(it)
            .toOkResponse().await()
    }) {
        val scope = rememberCoroutineScope()
        val downloader = LocalDownloader.current
        val downLoadHandler =
            downloader.download(url) {
                listener {
                    success { println("下载成功") }
                    failure { println("下载失败:${it.message}") }
                    process { process, current, total ->
                        println("process = [${process}], current = [${current}], total = [${total}]")
                    }
                }
            }


        val state = downLoadHandler.collectAsState()
        Button(
            onClick = { scope.launch { downLoadHandler.start() } },
            modifier = modifier,
            enabled = state !is DownloadStatus.Downloading
        ) {
            Text(text = "开始下载")
            when (state) {
                is DownloadStatus.DownloadFail -> Text(text = "下载失败了:${state.throwable.message}")
                is DownloadStatus.DownloadSuccess -> Text(text = "下载完成")
                is DownloadStatus.Downloading -> Text(text = "下载中(${state.process}), ${state.current}/${state.total}")
                null -> Spacer(modifier = Modifier.size(10.dp))
            }
        }
    }
}