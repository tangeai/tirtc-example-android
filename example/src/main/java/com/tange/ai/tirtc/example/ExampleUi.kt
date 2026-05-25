package com.tange.ai.tirtc.example

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView

internal fun Context.page(content: LinearLayout.() -> Unit): ScrollView {
    val root =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), statusBarInset() + dp(18), dp(24), dp(32))
            setBackgroundColor(ExampleTheme.background)
            content()
        }
    return ScrollView(this).apply {
        setBackgroundColor(ExampleTheme.background)
        addView(root)
    }
}

internal fun Context.frameScreen(
    top: View,
    stage: FrameLayout,
    overlay: View,
    bottom: View,
): FrameLayout {
    return FrameLayout(this).apply {
        setBackgroundColor(ExampleTheme.videoBackground)
        addView(stage, FrameLayout.LayoutParams(match(), match()))
        addView(top, FrameLayout.LayoutParams(match(), wrap(), Gravity.TOP))
        addView(
            overlay,
            FrameLayout.LayoutParams(match(), wrap(), Gravity.TOP).apply {
                topMargin = statusBarInset() + dp(78)
                leftMargin = dp(18)
                rightMargin = dp(18)
            },
        )
        addView(bottom, FrameLayout.LayoutParams(match(), wrap(), Gravity.BOTTOM))
    }
}

internal fun LinearLayout.header(
    title: String,
    primaryAction: Pair<String, () -> Unit>,
    secondaryAction: Pair<String, () -> Unit>,
) {
    val row =
        LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            addView(
                TextView(context).apply {
                    text = title
                    setTextColor(ExampleTheme.brandText)
                    textSize = 22f
                    typeface = Typeface.DEFAULT_BOLD
                },
                LinearLayout.LayoutParams(0, wrap(), 1f),
            )
            addView(context.chipButton(primaryAction.first, primaryAction.second), context.chipLayoutParams())
            addView(context.chipButton(secondaryAction.first, secondaryAction.second), context.chipLayoutParams())
        }
    addViewWithMargin(row, bottom = 20)
}

internal fun LinearLayout.navigationHeader(
    title: String,
    back: () -> Unit,
) {
    val row =
        LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            addView(context.chipButton("返回", back), context.chipLayoutParams())
            addView(
                TextView(context).apply {
                    text = title
                    setTextColor(ExampleTheme.primary)
                    textSize = 18f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.END
                },
                LinearLayout.LayoutParams(0, wrap(), 1f),
            )
        }
    addViewWithMargin(row, bottom = 20)
}

internal fun Context.playerTopBar(
    remoteId: String,
    onCommand: () -> Unit,
    onUploadLogs: () -> Unit,
): View {
    return LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(16), statusBarInset() + dp(12), dp(12), dp(10))
        setBackgroundColor(ExampleTheme.background)
        addView(
            TextView(context).apply {
                text = remoteId
                setTextColor(ExampleTheme.primary)
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
            },
            LinearLayout.LayoutParams(0, wrap(), 1f),
        )
        addView(chipButton("发送命令", onCommand), chipLayoutParams())
        addView(chipButton("上传日志", onUploadLogs), chipLayoutParams())
    }
}

internal fun Context.deviceTopBar(
    secret: String,
    onCommand: () -> Unit,
    onUploadLogs: () -> Unit,
): View {
    return LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(16), statusBarInset() + dp(12), dp(12), dp(10))
        setBackgroundColor(ExampleTheme.background)
        addView(
            TextView(context).apply {
                text = "设备端 $secret"
                setTextColor(ExampleTheme.primary)
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
            },
            LinearLayout.LayoutParams(0, wrap(), 1f),
        )
        addView(chipButton("发送命令", onCommand), chipLayoutParams())
        addView(chipButton("上传日志", onUploadLogs), chipLayoutParams())
    }
}

internal fun Context.bottomControls(
    bubble: TextView,
    actionText: String,
    action: () -> Unit,
): View {
    return LinearLayout(this).apply {
        gravity = Gravity.END
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(16), dp(20), dp(24))
        addView(bubble)
        addViewWithMargin(primaryButton(actionText, action), top = 12)
    }
}

internal fun Context.videoPanel(label: String): FrameLayout {
    return FrameLayout(this).apply {
        setBackgroundColor(ExampleTheme.videoBackground)
        addView(
            TextView(context).apply {
                text = label
                setTextColor(ExampleTheme.foreground)
                textSize = 16f
                gravity = Gravity.CENTER
            },
            FrameLayout.LayoutParams(match(), match()),
        )
    }
}

internal fun Context.deviceStatusSurface(content: TextView): View {
    return surface {
        setPadding(dp(16), dp(12), dp(16), dp(12))
        addView(sectionTitle("设备端状态"))
        addView(content)
    }
}

internal fun Context.qrGuide(text: String): View {
    return surface {
        addView(sectionTitle("二维码内容格式"))
        addView(
            TextView(context).apply {
                this.text = text
                setTextColor(ExampleTheme.textSecondary)
                textSize = 14f
                setPadding(0, dp(2), 0, dp(2))
            },
        )
    }
}

internal fun Context.scannerPanel(scannerView: View): FrameLayout {
    val side = (resources.displayMetrics.widthPixels - dp(48)).coerceAtMost(dp(390))
    return FrameLayout(this).apply {
        layoutParams =
            LinearLayout.LayoutParams(match(), side).apply {
                bottomMargin = dp(14)
            }
        background = rounded(ExampleTheme.videoBackground, radius = 30, strokeColor = ExampleTheme.inputBorder)
        clipToOutline = true
        addView(scannerView, FrameLayout.LayoutParams(match(), match()))
        addView(
            TextView(context).apply {
                text = "对准二维码"
                gravity = Gravity.CENTER
                setTextColor(ExampleTheme.foreground)
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                background = rounded(0x66000000, radius = 16, strokeColor = 0x00000000)
                setPadding(dp(12), dp(6), dp(12), dp(6))
            },
            FrameLayout.LayoutParams(wrap(), wrap(), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = dp(16)
            },
        )
    }
}

internal fun Context.fieldBlock(
    label: String,
    editText: EditText,
): View {
    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(inputLabel(label))
        addView(editText)
    }
}

internal fun Context.twoColumnFields(
    firstLabel: String,
    first: EditText,
    secondLabel: String,
    second: EditText,
): View {
    return LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(fieldBlock(firstLabel, first), LinearLayout.LayoutParams(0, wrap(), 1f))
        addView(space(dp(16)))
        addView(fieldBlock(secondLabel, second), LinearLayout.LayoutParams(0, wrap(), 1f))
    }
}

internal fun Context.spinnerBlock(
    label: String,
    spinner: Spinner,
): View {
    return surface {
        addView(sectionTitle(label))
        addView(spinner)
    }
}

internal fun Context.editText(
    placeholder: String,
    value: String = "",
    multiLine: Boolean = false,
    isSecret: Boolean = false,
    viewId: Int = View.NO_ID,
): EditText {
    return EditText(this).apply {
        id = viewId
        hint = placeholder
        setText(value)
        setTextColor(ExampleTheme.textPrimary)
        setHintTextColor(ExampleTheme.textHint)
        setSingleLine(!multiLine)
        textSize = 13f
        minHeight = if (multiLine) dp(118) else dp(58)
        minLines = if (multiLine) 3 else 1
        gravity = if (multiLine) Gravity.TOP or Gravity.START else Gravity.CENTER_VERTICAL or Gravity.START
        setPadding(dp(20), dp(12), dp(20), dp(12))
        inputType =
            when {
                isSecret -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                multiLine -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                else -> InputType.TYPE_CLASS_TEXT
            }
        background = rounded(ExampleTheme.surface, radius = 20, strokeColor = 0x00000000)
    }
}

internal fun Context.spinner(
    values: List<String>,
    selected: Int,
): Spinner {
    return Spinner(this).apply {
        adapter = ArrayAdapter(this@spinner, android.R.layout.simple_spinner_dropdown_item, values)
        setSelection(selected)
        onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long,
                ) = Unit

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
    }
}

internal fun Context.surface(content: LinearLayout.() -> Unit): LinearLayout {
    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(14), dp(18), dp(14))
        background = rounded(ExampleTheme.surface, radius = 24, strokeColor = ExampleTheme.inputBorder)
        content()
    }
}

internal fun Context.primaryButton(
    text: String,
    action: () -> Unit,
): TextView {
    return TextView(this).apply {
        this.text = text
        gravity = Gravity.CENTER
        minHeight = dp(56)
        setTextColor(ExampleTheme.foreground)
        textSize = 16f
        typeface = Typeface.DEFAULT_BOLD
        background = rounded(ExampleTheme.primary, radius = 28, strokeColor = ExampleTheme.primary)
        setPadding(dp(18), dp(14), dp(18), dp(14))
        setOnClickListener { action() }
    }
}

internal fun Context.outlinedButton(
    text: String,
    action: () -> Unit,
): TextView {
    return TextView(this).apply {
        this.text = text
        gravity = Gravity.CENTER
        minHeight = dp(48)
        setTextColor(ExampleTheme.primary)
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        background = rounded(ExampleTheme.surface, radius = 24, strokeColor = ExampleTheme.primary)
        setPadding(dp(16), dp(10), dp(16), dp(10))
        setOnClickListener { action() }
    }
}

internal fun Context.chipButton(
    text: String,
    action: () -> Unit,
): TextView {
    return TextView(this).apply {
        this.text = text
        gravity = Gravity.CENTER
        textSize = 13f
        minHeight = dp(38)
        minWidth = dp(76)
        typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(14), 0, dp(14), 0)
        setTextColor(ExampleTheme.primary)
        background = rounded(ExampleTheme.surface, radius = 28, strokeColor = ExampleTheme.primary)
        setOnClickListener { action() }
    }
}

internal fun Context.linkButton(
    text: String,
    action: () -> Unit,
): TextView {
    return TextView(this).apply {
        this.text = text
        gravity = Gravity.CENTER
        setTextColor(ExampleTheme.textSecondary)
        textSize = 13f
        typeface = Typeface.defaultFromStyle(Typeface.ITALIC)
        setPadding(dp(8), dp(14), dp(8), dp(14))
        setOnClickListener { action() }
    }
}

internal fun Context.sectionTitle(text: String): TextView {
    return TextView(this).apply {
        this.text = text
        setTextColor(ExampleTheme.textPrimary)
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, 0, 0, dp(8))
    }
}

internal fun Context.inputLabel(text: String): TextView {
    return TextView(this).apply {
        this.text = text
        setTextColor(ExampleTheme.textSecondary)
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(20), 0, dp(20), dp(4))
    }
}

internal fun Context.body(text: String): TextView {
    return TextView(this).apply {
        this.text = text
        setTextColor(ExampleTheme.textSecondary)
        textSize = 14f
    }
}

internal fun Context.streamBubbleView(text: String): TextView {
    return TextView(this).apply {
        this.text = text
        setTextColor(ExampleTheme.textPrimary)
        textSize = 13f
        background = rounded(ExampleTheme.surface, radius = 18, strokeColor = ExampleTheme.primary)
        setPadding(dp(14), dp(10), dp(14), dp(10))
    }
}

internal fun Context.space(width: Int): View {
    return View(this).apply {
        layoutParams = LinearLayout.LayoutParams(width, 1)
    }
}

internal fun LinearLayout.addViewWithMargin(
    view: View,
    top: Int = 0,
    bottom: Int = 14,
) {
    addView(
        view,
        LinearLayout.LayoutParams(match(), wrap()).apply {
            topMargin = top
            bottomMargin = bottom
        },
    )
}

private fun Context.rounded(
    color: Int,
    radius: Int,
    strokeColor: Int,
): GradientDrawable {
    return GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        setStroke(1, strokeColor)
    }
}

internal fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

private fun Context.statusBarInset(): Int {
    val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
    return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else dp(28)
}

private fun Context.chipLayoutParams(): LinearLayout.LayoutParams {
    return LinearLayout.LayoutParams(wrap(), dp(48)).apply {
        leftMargin = dp(8)
    }
}

private fun match(): Int = ViewGroup.LayoutParams.MATCH_PARENT

private fun wrap(): Int = ViewGroup.LayoutParams.WRAP_CONTENT
