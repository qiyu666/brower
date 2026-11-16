package com.qiyu.browser.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import com.qiyu.browser.R

/** 桌面小组件：4×2 常用网址，点击在本应用新标签打开 */
class SitesWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> appWidgetManager.updateAppWidget(id, buildViews(context, id)) }
    }

    /** 配置页保存后调用，刷新该实例 */
    fun refresh(context: Context, appWidgetId: Int) {
        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, buildViews(context, appWidgetId))
    }

    private fun buildViews(context: Context, appWidgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.sites_widget)
        val sites = SitesWidgetStore.get(context)
        for (i in 0 until SitesWidgetStore.MAX_SITES) {
            val cellId = context.resources.getIdentifier("wcell$i", "id", context.packageName)
            val site = sites.getOrNull(i)
            if (site != null) {
                views.setTextViewText(cellId, site.label.ifEmpty { site.url })
                views.setIntent(cellId, cellPendingIntent(context, appWidgetId, site.url))
            } else {
                views.setInt(cellId, "setVisibility", android.view.View.GONE)
            }
        }
        return views
    }

    private fun cellPendingIntent(context: Context, appWidgetId: Int, url: String): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            setPackage(context.packageName)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        return PendingIntent.getActivity(context, appWidgetId * 100 + url.hashCode() and 0xFF, intent, flags)
    }
}
