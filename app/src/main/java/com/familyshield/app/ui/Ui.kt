package com.familyshield.app.ui

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 부모님용 큰 글씨 화면 조각들. 글자 크기·색은 여기 한 곳에서만 정한다 */
object Ui {
    val GREEN = Color.rgb(0x1B, 0x7F, 0x3A)
    val RED = Color.rgb(0xB3, 0x26, 0x1E)
    val GRAY = Color.rgb(0x60, 0x60, 0x60)

    fun dp(context: Context, v: Int) = (v * context.resources.displayMetrics.density).toInt()

    fun title(context: Context, text: String) = TextView(context).apply {
        this.text = text
        textSize = 30f
        setTypeface(null, Typeface.BOLD)
        setPadding(0, dp(context, 8), 0, dp(context, 8))
    }

    fun heading(context: Context, text: String) = TextView(context).apply {
        this.text = text
        textSize = 23f
        setTypeface(null, Typeface.BOLD)
        setPadding(0, dp(context, 24), 0, dp(context, 4))
    }

    fun body(context: Context, text: String = "", size: Float = 20f) = TextView(context).apply {
        this.text = text
        textSize = size
        setLineSpacing(0f, 1.15f)
        setPadding(0, dp(context, 4), 0, dp(context, 4))
    }

    fun button(context: Context, text: String, onClick: () -> Unit) = Button(context).apply {
        this.text = text
        textSize = 22f
        isAllCaps = false
        minHeight = dp(context, 64)
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(context, 8) }
    }

    fun column(context: Context) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(context, 20), dp(context, 12), dp(context, 20), dp(context, 32))
    }

    /** 화면 전체를 세로 스크롤로 감싸고, 상태 표시줄·내비게이션 바와 겹치지 않게 한다 */
    fun setPage(activity: Activity, content: LinearLayout) {
        val scroll = ScrollView(activity).apply { addView(content) }
        scroll.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        activity.setContentView(scroll)
    }

    fun show(view: View, visible: Boolean) {
        view.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun time(millis: Long): String = SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA).format(Date(millis))

    fun clock(millis: Long): String = SimpleDateFormat("HH:mm", Locale.KOREA).format(Date(millis))
}
