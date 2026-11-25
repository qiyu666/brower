package com.qiyu.browser.model

/** 一条对话消息。role: "system" / "user" / "assistant" */
data class ChatMessage(
    val role: String,
    val content: String
)
