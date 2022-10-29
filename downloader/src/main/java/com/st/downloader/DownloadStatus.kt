package com.st.downloader

import android.net.Uri

sealed interface DownloadStatus {
    data class Downloading(val process: Float, val current: Long, val total: Long) : DownloadStatus
    data class DownloadFail(val throwable: Throwable) : DownloadStatus
    data class DownloadSuccess(val file: Uri) : DownloadStatus
}