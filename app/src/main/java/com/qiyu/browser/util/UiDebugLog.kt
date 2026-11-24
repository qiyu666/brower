package com.qiyu.browser.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * UI 状态调试日志（最小版）：环形缓冲 300 条 + 文件（filesDir/qiyu_ui.log，限 512KB）。
 * 开发者面板一键复制到剪贴板，用于定位偶发状态问题。
 */
object UiDebugLog {
    private const val MAX_BUFFER = 300
    private const val MAX_FILE_BYTES = 512 * 1024
    private val lock = Any()
    private val buffer = ArrayDeque<String>()
    private var file: File? = null
    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun log(context: Context, line: String) {
        val stamp = timeFmt.format(Date())
        val entry = "[$stamp] $line"
        synchronized(lock) {
            buffer.addLast(entry)
            while (buffer.size > MAX_BUFFER) buffer.removeFirst()
            try {
                val f = file ?: File(context.filesDir, "qiyu_ui.log").also { file = it }
                if (f.length() > MAX_FILE_BYTES) {
                    val text = f.readText()
                    f.writeText(text.substring(text.length / 2))
                }
                f.appendText(entry + "\n")
            } catch (e: Exception) {}
        }
    }

    fun dump(): String {
        val out = ArrayList<String>()
        synchronized(lock) {
            out.addAll(buffer)
        }
        return if (out.isEmpty()) "（暂无日志）" else out.joinToString("\n")
    }
}
