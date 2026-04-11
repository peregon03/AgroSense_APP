package com.example.agrosense.ui.screens

import android.content.Context
import android.widget.TextView
import com.example.agrosense.R
import com.example.agrosense.ui.viewmodel.DateRange
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import java.text.SimpleDateFormat
import java.util.*

class CustomMarkerView(
    context: Context,
    private val unit: String,
    private val range: DateRange
) : MarkerView(context, R.layout.marker_chart) {

    private val tvValue: TextView = findViewById(R.id.tvMarkerValue)
    private val tvDate:  TextView = findViewById(R.id.tvMarkerDate)

    private val UTC   = TimeZone.getTimeZone("UTC")
    private val TZ_CO = TimeZone.getTimeZone("America/Bogota")

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        e ?: return
        tvValue.text = "%.2f %s".format(e.y, unit)
        val fmt = when (range) {
            DateRange.TODAY   -> SimpleDateFormat("HH:mm", Locale.getDefault()).apply { timeZone = TZ_CO }
            DateRange.WEEK    -> SimpleDateFormat("EEE dd HH:mm", Locale.getDefault()).apply { timeZone = TZ_CO }
            DateRange.MONTH   -> SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).apply { timeZone = TZ_CO }
            DateRange.QUARTER -> SimpleDateFormat("dd/MM/yy", Locale.getDefault()).apply { timeZone = TZ_CO }
        }
        tvDate.text = try { fmt.format(Date(e.x.toLong() * 1000L)) } catch (_: Exception) { "" }
        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF = MPPointF(-(width / 2f), -height.toFloat() - 12f)
}
