@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package com.st.downloader

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.URLUtil
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.Response
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream

typealias SuccessInvoker = (file: Uri) -> Unit
typealias FailureInvoker = (Throwable) -> Unit
typealias ProcessInvoker = (process: Float, current: Long, total: Long) -> Unit

fun Context.download(
    url: String,
    config: DownloadConfig.() -> Unit = {}
): Flow<DownloadStatus> {
    return download(url, DownloadConfig().apply(config))
}

fun Context.download(
    url: String,
    downloadConfig: DownloadConfig
): Flow<DownloadStatus> = flow {
    try {
        val response =
            downloadConfig.executor.invoker?.invoke(url) ?: throw RuntimeException("请配置下载方法")
        val body = response.body ?: throw RuntimeException("下载出错")
        val length = body.contentLength()

        val ios = body.byteStream()
        var uri: Uri? = null
        var outFile: File? = null
        val ops = downloadConfig.run {
            this.uri?.let {
                uri = it
                contentResolver.openOutputStream(it)
            } ?: run {
                outFile = fileName.createFile(response, this@download)
                FileOutputStream(outFile)
            }
        }

        var currentLength: Long = 0
        val bufferSize = 1024 * 8
        val buffer = ByteArray(bufferSize)
        val bufferedInputStream = BufferedInputStream(ios, bufferSize)
        var readLength: Int
        ios.use {
            ops.use {
                bufferedInputStream.use {
                    while (bufferedInputStream.read(buffer, 0, bufferSize)
                            .also { readLength = it } != -1
                    ) {
                        ops.write(buffer, 0, readLength)
                        currentLength += readLength
                        val process = currentLength.toFloat() / length
                        val current = currentLength
                        emit(DownloadStatus.Downloading(process, current, length))
                        downloadConfig.listener.process?.invoke(process, current, length)
                    }
                }
            }
        }

        val outUri = uri ?: outFile!!.toUri()
        emit(DownloadStatus.DownloadSuccess(outUri))
        downloadConfig.listener.success?.invoke(outUri)

    } catch (e: Exception) {
        e.printStackTrace()
        emit(DownloadStatus.DownloadFail(e))
        downloadConfig.listener.failure?.invoke(e)
    }
}.flowOn(Dispatchers.IO)

private fun String?.createFile(
    response: Response,
    context: Context
): File {
    val fileName = this ?: run {
        val url = response.request.url.toString()
        val contentDisposition = response.headers["Content-Disposition"]
        val mimeType = response.body?.contentType()?.type
        URLUtil.guessFileName(url, contentDisposition, mimeType)
    }
    return File("${context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)}${File.separator}$fileName")
}


class DownloadConfig {
    internal var fileName: String? = null
    internal var uri: Uri? = null
    internal var file: File? = null

    internal val listener: DownloadListener = DownloadListener()
    internal val executor: DownloadConfigure = DownloadConfigure()

    fun destination(uri: Uri) {
        this.uri = uri
    }

    fun destination(name: String) {
        fileName = name
    }

    fun destination(file: File) {
        this.file = file
    }

    fun listener(block: DownloadListener.() -> Unit) {
        listener.block()
    }

    fun config(block: DownloadConfigure.() -> Unit) {
        executor.block()
    }

    fun from(config: DownloadConfig) {
        file = config.file
        fileName = config.fileName
        uri = config.uri
    }
}

class DownloadListener {
    internal var success: SuccessInvoker? = null
    internal var failure: FailureInvoker? = null
    internal var process: ProcessInvoker? = null

    fun success(block: SuccessInvoker) {
        success = block
    }

    fun failure(block: FailureInvoker) {
        failure = block
    }

    fun process(block: ProcessInvoker) {
        process = block
    }
}

class DownloadConfigure {
    internal var invoker: (suspend (url: String) -> Response)? = null

    fun request(block: (suspend (url: String) -> Response)) {
        invoker = block
    }

}