package com.qiyu.browser.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class MdSeg(val isCode: Boolean, val content: String)

fun splitCodeBlocks(text: String): List<MdSeg> {
    val out = ArrayList<MdSeg>()
    var inCode = false
    var buf = StringBuilder()
    for (line in text.split("\n")) {
        val t = line.trimStart()
        if (t.startsWith("```")) {
            if (!inCode) { if (buf.isNotEmpty()) { out.add(MdSeg(false, buf.toString())); buf = StringBuilder() }; inCode = true }
            else { out.add(MdSeg(true, buf.toString().trimEnd('\n'))); buf = StringBuilder(); inCode = false }
        } else { buf.append(line).append('\n') }
    }
    if (inCode) out.add(MdSeg(true, buf.toString().trimEnd('\n')))
    else if (buf.isNotEmpty()) out.add(MdSeg(false, buf.toString()))
    return out
}

fun cleanInline(s: String): String = s.lines().joinToString("\n") { ln ->
    var x = Regex("^\\s*#{1,6}\\s+").replace(ln, "")
    x = Regex("^\\s*[-*+]\\s+").replace(x, "• ")
    x.replace("**", "").replace("__", "").replace("`", "")
}

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val segs = splitCodeBlocks(text)
    Column(modifier) {
        segs.forEach { seg ->
            if (seg.isCode) {
                val c = seg.content.trim()
                if (c.isNotEmpty()) {
                    Text(
                        c,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                }
            } else {
                val t = cleanInline(seg.content).trim()
                if (t.isNotEmpty()) {
                    Text(t, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 1.dp))
                }
            }
        }
    }
}
