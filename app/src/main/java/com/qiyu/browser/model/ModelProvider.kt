package com.qiyu.browser.model

/** 一个模型供应商（OpenAI 兼容端点）。builtin=true 为内置，不可删除。 */
data class ModelProvider(
    val id: Long,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val builtin: Boolean = false
)
