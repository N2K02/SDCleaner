package eu.darken.sdmse.widget

import android.content.Context
import dagger.hilt.android.EntryPointAccessors
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Keep the render-state observer dormant when no home widget is installed.
 * Refresh registration after add/update and release it on deletion.
 */
class SdmHomeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SdmHomeWidget()

    private fun refreshSubscriptions(context: Context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext, WidgetEntryPoint::class.java
        ).widgetRefreshCoordinator().onWidgetsChanged()
    }

    override fun onUpdate(context: Context, manager: android.appwidget.AppWidgetManager, ids: IntArray) {
        super.onUpdate(context, manager, ids)
        refreshSubscriptions(context)
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        super.onDeleted(context, ids)
        refreshSubscriptions(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        refreshSubscriptions(context)
    }
}
