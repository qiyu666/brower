package com.qiyu.browser.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

/** 小组件站点数据的存取（SharedPreferences + JSON，最多 8 条） */
object SitesWidgetStore {
    private const val PREFS = "sites_widget"
    private const val KEY_SITES = "sites"
    const val MAX_SITES = 8

    data class Site(val url: String, val label: String)

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(context: Context): List<Site> {
        val raw = prefs(context).getString(KEY_SITES, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Site(o.optString("url", ""), o.optString("label", ""))
            }.filter { it.url.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(context: Context, sites: List<Site>) {
        val arr = JSONArray()
        sites.take(MAX_SITES).forEach { s ->
            arr.put(org.json.JSONObject().put("url", s.url).put("label", s.label))
        }
        prefs(context).edit().putString(KEY_SITES, arr.toString()).apply()
    }
}
