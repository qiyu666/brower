package com.qiyu.browser.model

/** 一条对话消息。role: "system" / "user" / "assistant"；image 为可选图片（data URL，多模态）。 */
data class ChatMessage(
    val role: String,
    val content: String,
    val image: String? = null
)
