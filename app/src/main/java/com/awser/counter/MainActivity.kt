package com.awser.counter

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputFilter
import android.text.InputType
import android.text.TextUtils
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import kotlin.math.min
import kotlin.math.max
import kotlin.math.ceil

class MainActivity : Activity() {
    private lateinit var store: CounterStore
    private lateinit var counter: CounterView
    private lateinit var root: FrameLayout
    private lateinit var progress: ProgressBar
    private lateinit var countLabel: TextView
    private lateinit var goalButton: Button
    private lateinit var counterNameLabel: TextView
    private lateinit var themeButton: Button
    private lateinit var lightButton: Button
    private lateinit var colorThemeButton: Button
    private lateinit var vibrationButton: Button
    private lateinit var bannerAds: BannerAdController
    private lateinit var adPrivacyButton: Button
    private lateinit var adStatusRow: View
    private lateinit var adStatusLabel: TextView
    private lateinit var adRetryButton: Button
    private lateinit var adDetailsButton: Button
    private var adStatus = BannerAdStatus("")
    private var adPrivacyRequired = false
    private var adaptiveContent: AdaptiveContent? = null
    private var renderedDarkTheme: Boolean? = null
    private var renderedColorThemeId: String? = null
    private var progressAnimator: ObjectAnimator? = null
    private var palette = Palette(true, CounterThemes.resolve(CounterThemes.DEFAULT_ID))
    private val numberFormat = NumberFormat.getIntegerInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = CounterStore(this)
        bannerAds = BannerAds.create(this) { required ->
            runOnUiThread {
                adPrivacyRequired = required
                if (::adPrivacyButton.isInitialized) adPrivacyButton.visibility = if (required) View.VISIBLE else View.GONE
                adaptiveContent?.requestLayout()
            }
        }
        bannerAds.setStatusListener { status ->
            runOnUiThread {
                adStatus = status
                refreshAdStatus()
            }
        }
        renderScreen()
        bannerAds.start()
    }

    private fun renderScreen() {
        val colorTheme = CounterThemes.resolve(store.colorThemeId)
        palette = Palette(store.darkTheme, colorTheme)
        renderedDarkTheme = store.darkTheme
        renderedColorThemeId = colorTheme.id
        window.statusBarColor = palette.background
        window.navigationBarColor = palette.background
        @Suppress("DEPRECATION")
        val lightBars = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            if (Build.VERSION.SDK_INT >= 26) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = if (store.darkTheme) 0 else lightBars
        if (Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        root = FrameLayout(this).apply {
            tag = "counter_screen"
            background = QuietBackground(store.darkTheme, colorTheme)
        }
        val topPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val titleGroup = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleGroup.addView(label("Tasbih Counter", 21f, palette.foreground).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }, marginParams(-1, -2))
        titleGroup.addView(label("Z I K R", 9f, palette.accent), marginParams(-1, -2, top = 4))
        val headerControls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        themeButton = iconButton("theme", "Theme", "theme") {
            if (persistAction { store.setDarkTheme(!store.darkTheme) }) {
                counter.pauseAnimations()
                progressAnimator?.cancel()
                renderScreen()
            }
        }.apply {
            contentDescription = if (store.darkTheme) "Switch to light theme" else "Switch to dark theme"
        }
        lightButton = iconButton("light", "Display light", "light") {
            if (persistAction { store.setDisplayLightEnabled(!store.displayLightEnabled) }) {
                counter.setDisplayLightEnabled(store.displayLightEnabled)
                refreshSettings()
            }
        }
        colorThemeButton = iconButton("theme_colors", "Theme colors", "palette") { showColorThemeDialog() }
        headerControls.addView(themeButton, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(8) })
        headerControls.addView(lightButton, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(8) })
        headerControls.addView(colorThemeButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        topPanel.addView(AdaptiveRow(titleGroup, headerControls, equal = false, gap = dp(12)).apply {
            tag = "counter_header"
        }, marginParams(-1, -2))

        val topControls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        vibrationButton = iconButton("vibration", "Haptics", "vibration") {
            if (persistAction { store.setVibrationEnabled(!store.vibrationEnabled) }) {
                refreshSettings()
                if (store.vibrationEnabled) counter.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        }
        goalButton = iconButton("goal", "Set target", "goal") { showGoalDialog() }
        val rename = iconButton("rename", "Rename", "rename") { showRenameDialog() }
        listOf(vibrationButton, goalButton, rename).forEach { button ->
            topControls.addView(FrameLayout(this).apply {
                addView(button, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER))
            }, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        topPanel.addView(topControls, marginParams(-1, -2, top = 10))
        counterNameLabel = label("", 14f, palette.muted).apply {
            tag = "counter_name_label"
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }
        val quranButton = actionButton("quran", "Quran") {
            startActivity(Intent(this, QuranActivity::class.java))
        }.apply { contentDescription = "Open Quran. Read Yaseen, Ayat-e-Karima and Ayatul Kursi without ads" }
        topPanel.addView(AdaptiveRow(counterNameLabel, quranButton, equal = false, gap = dp(12)),
            marginParams(-1, -2, top = 12))

        counter = CounterView(this).apply {
            setColorTheme(colorTheme)
            setDisplayLightEnabled(store.displayLightEnabled)
            onIncrement = increment@ {
                val previous = store.count
                var updated = previous
                if (!persistAction { updated = store.increment() }) return@increment
                refreshCounter(true)
                val reachedTarget = store.goal > 0 && updated != previous && updated == store.goal.toLong()
                if (store.vibrationEnabled && updated != previous) {
                    performHapticFeedback(if (reachedTarget) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.KEYBOARD_TAP)
                }
                if (reachedTarget) {
                    announceForAccessibility("Target reached. Count $updated.")
                    Toast.makeText(this@MainActivity, "Target reached · ${numberFormat.format(updated)}", Toast.LENGTH_SHORT).show()
                }
            }
            onReset = { showResetDialog() }
        }
        val heroPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(counter, marginParams(dp(300), dp(300f * 418f / 360f), top = 6))
        }
        countLabel = label("", 11f, palette.muted).apply {
            tag = "count_label"
            gravity = Gravity.CENTER
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_NONE
        }
        heroPanel.addView(countLabel, marginParams(-1, -2, top = 4))
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            tag = "progress"
            max = 1000
            progressTintList = ColorStateList.valueOf(palette.accent)
            progressBackgroundTintList = ColorStateList.valueOf(palette.border)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        heroPanel.addView(progress, marginParams(-1, dp(3), top = 8))

        val bottomPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val save = actionButton("save", "Save count") { showSaveDialog() }
        val history = actionButton("history", "Saved counts") { showHistoryDialog() }
        bottomPanel.addView(AdaptiveRow(save, history, equal = true, gap = dp(10)).apply {
            tag = "counter_footer"
        }, marginParams(-1, -2))
        val privacyLabel = label("Saved on your device", 10f, palette.muted).apply { gravity = Gravity.CENTER }
        adPrivacyButton = actionButton("ad_privacy", "Ad privacy") { bannerAds.showPrivacyOptions() }.apply {
            visibility = if (adPrivacyRequired) View.VISIBLE else View.GONE
        }
        bottomPanel.addView(AdaptiveRow(privacyLabel, adPrivacyButton, equal = false, gap = dp(8)), marginParams(-1, -2, top = 6))

        val content = AdaptiveContent(topPanel, heroPanel, bottomPanel).apply { tag = "adaptive_content" }
        adaptiveContent = content
        val scroll = object : ScrollView(this) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                content.setViewport(MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight,
                    MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom)
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }.apply {
            tag = "counter_scroll"
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            addView(content, FrameLayout.LayoutParams(-1, -2))
        }
        val bannerContainer = FrameLayout(this).apply { tag = "ad_banner_container" }
        adStatusLabel = label("", 12f, palette.muted).apply { tag = "ad_status" }
        adRetryButton = actionButton("ad_retry", "Retry") { bannerAds.retry() }
        adDetailsButton = iconButton("ad_details", "Ad details", "details") {
            if (adStatus.detail.isNotBlank()) {
                showDialog(AlertDialog.Builder(dialogContext()).setTitle("Ad details")
                    .setMessage(adStatus.detail).setPositiveButton("Done", null).create())
            }
        }
        val adStatusControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(adDetailsButton, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(8) })
            addView(adRetryButton, LinearLayout.LayoutParams(-2, -2))
        }
        adStatusRow = AdaptiveRow(adStatusLabel, adStatusControls, equal = false, gap = dp(8)).apply {
            tag = "ad_status_row"
            setPadding(dp(12), dp(4), dp(12), dp(4))
        }
        val screen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(adStatusRow, LinearLayout.LayoutParams(-1, -2))
            addView(bannerContainer, LinearLayout.LayoutParams(-1, -2))
        }
        root.addView(screen, FrameLayout.LayoutParams(-1, -1))
        if (Build.VERSION.SDK_INT >= 35) {
            root.setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }
        setContentView(root)
        bannerAds.attach(bannerContainer)
        root.requestApplyInsets()
        refreshSettings()
        refreshCounter(false)
        refreshCounterName()
        refreshAdStatus()
    }

    private fun refreshAdStatus() {
        if (!::adStatusRow.isInitialized) return
        adStatusLabel.text = adStatus.message
        adStatusRow.visibility = if (adStatus.message.isBlank()) View.GONE else View.VISIBLE
        adRetryButton.visibility = if (adStatus.canRetry) View.VISIBLE else View.GONE
        adDetailsButton.visibility = if (adStatus.detail.isNotBlank()) View.VISIBLE else View.GONE
        (adRetryButton.parent as View).visibility = if (adStatus.canRetry || adStatus.detail.isNotBlank()) View.VISIBLE else View.GONE
    }

    private fun refreshSettings() {
        vibrationButton.contentDescription = "Haptics ${if (store.vibrationEnabled) "on" else "off"}. Toggle haptics"
        vibrationButton.setCompoundDrawablesWithIntrinsicBounds(Glyph("vibration", if (store.vibrationEnabled) palette.accent else palette.muted, dp(21)), null, null, null)
        vibrationButton.background = circularButtonBackground(store.vibrationEnabled)
        goalButton.contentDescription = if (store.goal > 0) "Change target. Current target ${store.goal}" else "Set an optional target. No target selected"
        lightButton.contentDescription = if (store.displayLightEnabled) "Turn display light off" else "Turn display light on"
        lightButton.setCompoundDrawablesWithIntrinsicBounds(Glyph("light", if (store.displayLightEnabled) palette.accent else palette.muted, dp(21)), null, null, null)
        lightButton.background = circularButtonBackground(store.displayLightEnabled)
        colorThemeButton.contentDescription = "Theme colors. ${CounterThemes.resolve(store.colorThemeId).name} selected"
        colorThemeButton.background = circularButtonBackground(true)
        if (Build.VERSION.SDK_INT >= 26) {
            vibrationButton.tooltipText = "Haptics ${if (store.vibrationEnabled) "on" else "off"}"
            goalButton.tooltipText = if (store.goal > 0) "Target ${numberFormat.format(store.goal)}" else "Set target"
            themeButton.tooltipText = themeButton.contentDescription
            lightButton.tooltipText = lightButton.contentDescription
            colorThemeButton.tooltipText = colorThemeButton.contentDescription
        }
    }

    private fun refreshCounterName() {
        counterNameLabel.text = store.counterName
        counterNameLabel.contentDescription = "Counter name: ${store.counterName}"
    }

    private fun refreshCounter(animate: Boolean) {
        counter.update(store.count, store.darkTheme)
        progressAnimator?.cancel()
        val hasTarget = store.goal > 0
        countLabel.text = countSummary(store.count, store.goal)
        countLabel.contentDescription = if (hasTarget) "Count ${store.count}. Target ${store.goal}" else "Count ${store.count}"
        progress.visibility = if (hasTarget) View.VISIBLE else View.GONE
        if (!hasTarget) {
            progress.progress = 0
            return
        }
        val nextProgress = (min(store.count.toDouble() / store.goal, 1.0) * 1000).toInt()
        val animationsEnabled = if (Build.VERSION.SDK_INT >= 26) ValueAnimator.areAnimatorsEnabled()
            else Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        if (animate && animationsEnabled) {
            progressAnimator = ObjectAnimator.ofInt(progress, "progress", progress.progress, nextProgress).apply { duration = 220; start() }
        } else progress.progress = nextProgress
    }

    private fun showResetDialog() {
        if (store.count == 0L) return
        showDialog(AlertDialog.Builder(dialogContext()).setTitle("Start a new count?")
            .setMessage("Your count of ${numberFormat.format(store.count)} will be reset. Saved counts will stay on this device.")
            .setNegativeButton("Keep counting", null)
            .setPositiveButton("Reset") { _, _ -> if (persistAction { store.reset() }) refreshCounter(false) }.create())
    }

    private fun showSaveDialog() {
        val input = input(store.counterName, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES).apply {
            tag = "session_name"
            hint = "Count name (optional)"
            filters = arrayOf(InputFilter.LengthFilter(60))
            selectAll()
        }
        val box = LinearLayout(dialogContext()).apply {
            orientation = LinearLayout.VERTICAL
            // Naming is optional; opening Save must not need an on-screen keyboard.
            isFocusableInTouchMode = true
            descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
            setPadding(dp(24), dp(10), dp(24), dp(8))
            addView(label("${numberFormat.format(store.count)} total · Your counter keeps its current count.", 14f, palette.muted))
            addView(input, marginParams(-1, -2, top = 12))
        }
        val scroll = ScrollView(dialogContext()).apply { addView(box) }
        val dialog = AlertDialog.Builder(dialogContext()).setTitle("Save count").setView(scroll)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                if (persistAction { store.saveSession(input.text.toString().trim().ifEmpty { store.counterName }) }) {
                    Toast.makeText(this, "Count saved", Toast.LENGTH_SHORT).show()
                }
            }.create()
        showDialog(dialog)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        box.requestFocus()
    }

    private fun showHistoryDialog() {
        val sessions = store.sessions()
        val list = LinearLayout(dialogContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(4), dp(22), dp(16))
        }
        if (sessions.isEmpty()) {
            list.addView(label("No saved counts yet.\n\nTap Save count to keep a count here. Everything stays on this device.", 15f, palette.muted))
        }
        val scroll = ScrollView(dialogContext()).apply { addView(list) }
        val dialog = AlertDialog.Builder(dialogContext()).setTitle("Saved counts").setView(scroll).setPositiveButton("Done", null).create()
        sessions.forEach { session ->
            val row = LinearLayout(dialogContext()).apply {
                tag = "history_row_${session.id}"
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = buttonBackground(false)
                isClickable = true
                isFocusable = true
                contentDescription = "${session.label}, count ${session.count}. Restore or delete saved count"
                setOnClickListener { dialog.dismiss(); showSessionDialog(session) }
            }
            row.addView(label(session.label, 16f, palette.foreground).apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) })
            row.addView(label(countSummary(session.count, session.goal), 13f, palette.accent), marginParams(-1, -2, top = 5))
            row.addView(label(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(session.timestamp)), 11f, palette.muted), marginParams(-1, -2, top = 4))
            list.addView(row, marginParams(-1, -2, top = 9))
        }
        showDialog(dialog)
    }

    private fun showSessionDialog(session: CounterStore.Session) {
        showDialog(AlertDialog.Builder(dialogContext()).setTitle(session.label)
            .setMessage("${countSummary(session.count, session.goal)}\n\nRestore replaces the current counter with this saved count.")
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Delete") { _, _ ->
                showDialog(AlertDialog.Builder(dialogContext()).setTitle("Delete saved count?").setMessage(session.label)
                    .setNegativeButton("Cancel", null).setPositiveButton("Delete") { _, _ ->
                        if (persistAction { store.deleteSession(session.id) }) showHistoryDialog()
                    }.create())
            }
            .setPositiveButton("Restore") { _, _ ->
                if (persistAction { store.restoreSession(session.id) }) {
                    refreshSettings(); refreshCounter(false); refreshCounterName()
                    Toast.makeText(this, "Saved count restored", Toast.LENGTH_SHORT).show()
                }
            }.create())
    }

    private fun showGoalDialog() {
        showDialog(AlertDialog.Builder(dialogContext()).setTitle("Choose your target")
            .setItems(arrayOf("No target", "25", "50", "100", "Custom target")) { _, which ->
                val presets = intArrayOf(0, 25, 50, 100)
                if (which < presets.size) setGoal(presets[which]) else showCustomGoal()
            }.setNegativeButton("Cancel", null).create())
    }

    private fun showCustomGoal() {
        val input = input(if (store.goal > 0) store.goal.toString() else "", InputType.TYPE_CLASS_NUMBER).apply {
            tag = "custom_goal"
            filters = arrayOf(InputFilter.LengthFilter(9))
            selectAll()
        }
        val box = FrameLayout(dialogContext()).apply { setPadding(dp(24), dp(8), dp(24), 0); addView(input) }
        val dialog = AlertDialog.Builder(dialogContext()).setTitle("Custom target").setMessage("Choose a target from 1 to 999,999,999.")
            .setView(box).setNegativeButton("Cancel", null).setPositiveButton("Set target", null).create()
        showDialog(dialog)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val value = input.text.toString().toIntOrNull()
            if (value == null || value !in 1..999999999) input.error = "Enter a number from 1 to 999,999,999"
            else if (setGoal(value)) dialog.dismiss()
        }
    }

    private fun setGoal(value: Int): Boolean {
        if (!persistAction { store.setGoal(value) }) return false
        refreshSettings()
        refreshCounter(false)
        return true
    }

    private fun showRenameDialog() {
        val input = input(store.counterName, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES).apply {
            tag = "counter_name"
            hint = "Counter name"
            filters = arrayOf(InputFilter.LengthFilter(60))
            selectAll()
        }
        val box = FrameLayout(dialogContext()).apply { setPadding(dp(24), dp(8), dp(24), 0); addView(input) }
        val dialog = AlertDialog.Builder(dialogContext()).setTitle("Rename counter").setView(box)
            .setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
        showDialog(dialog)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = input.text.toString().trim()
            if (name.isEmpty()) input.error = "Enter a counter name"
            else if (persistAction { store.setCounterName(name) }) { refreshCounterName(); dialog.dismiss() }
        }
    }

    private fun showColorThemeDialog() {
        val selectedId = CounterThemes.resolve(store.colorThemeId).id
        val list = LinearLayout(dialogContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(4), dp(20), dp(14))
        }
        lateinit var dialog: AlertDialog
        CounterThemes.all.forEach { theme ->
            val selected = theme.id == selectedId
            val accent = if (store.darkTheme) theme.darkAccent else theme.lightAccent
            val option = Button(dialogContext()).apply {
                tag = "color_theme_${theme.id}"
                text = theme.name + if (selected) "  ✓" else ""
                isAllCaps = false
                isSingleLine = false
                maxLines = Int.MAX_VALUE
                isSelected = selected
                textSize = 16f
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setTextColor(palette.foreground)
                minimumWidth = 0
                minWidth = 0
                minimumHeight = dp(48)
                minHeight = dp(48)
                setPadding(dp(14), dp(12), dp(14), dp(12))
                compoundDrawablePadding = dp(12)
                setCompoundDrawablesRelativeWithIntrinsicBounds(ColorSwatch(accent, dp(26)), null, null, null)
                val background = GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    setColor(palette.surface)
                    setStroke(dp(if (selected) 2 else 1), if (selected) accent else palette.border)
                }
                this.background = RippleDrawable(ColorStateList.valueOf(accentWithAlpha(40)), background, null)
                contentDescription = "${theme.name} color theme${if (selected) ", selected" else ""}"
                setOnClickListener {
                    if (persistAction { store.setColorThemeId(theme.id) }) {
                        dialog.dismiss()
                        counter.pauseAnimations()
                        progressAnimator?.cancel()
                        renderScreen()
                    }
                }
            }
            list.addView(option, marginParams(-1, -2, top = 7))
        }
        val scroll = ScrollView(dialogContext()).apply {
            tag = "theme_colors_list"
            addView(list)
        }
        dialog = AlertDialog.Builder(dialogContext()).setTitle("Theme colors").setView(scroll).setNegativeButton("Cancel", null).create()
        showDialog(dialog)
    }

    private fun iconButton(viewTag: String, title: String, icon: String, action: () -> Unit): Button = Button(this).apply {
        tag = viewTag
        text = ""
        isAllCaps = false
        gravity = Gravity.CENTER
        contentDescription = title
        minimumWidth = 0
        minWidth = 0
        minimumHeight = 0
        minHeight = 0
        setPadding(dp(12), 0, dp(12), 0)
        setCompoundDrawablesWithIntrinsicBounds(Glyph(icon, palette.accent, dp(21)), null, null, null)
        compoundDrawablePadding = 0
        background = circularButtonBackground(false)
        if (Build.VERSION.SDK_INT >= 26) tooltipText = title
        setOnClickListener { action() }
    }

    private fun actionButton(viewTag: String, title: String, action: () -> Unit): Button = Button(this).apply {
        tag = viewTag
        text = title
        textSize = 14f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        isAllCaps = false
        setTextColor(palette.foreground)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = buttonBackground(true)
        minimumWidth = dp(48)
        minWidth = dp(48)
        minimumHeight = dp(48)
        minHeight = dp(48)
        contentDescription = title
        setOnClickListener { action() }
    }

    private fun circularButtonBackground(selected: Boolean): Drawable {
        val shape = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(palette.surface)
            setStroke(dp(1), if (selected) palette.accentBorder else palette.border)
        }
        val mask = GradientDrawable().apply { this.shape = GradientDrawable.OVAL; setColor(Color.WHITE) }
        return RippleDrawable(ColorStateList.valueOf(accentWithAlpha(40)), shape, mask)
    }

    private fun buttonBackground(outlined: Boolean): Drawable {
        val shape = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(14).toFloat()
            setColor(palette.surface)
            setStroke(dp(1), if (outlined) palette.accentBorder else palette.border)
        }
        return RippleDrawable(ColorStateList.valueOf(accentWithAlpha(45)), shape, null)
    }

    private fun input(value: String, inputKind: Int) = EditText(dialogContext()).apply {
        setText(value)
        inputType = inputKind
        setSingleLine()
        textSize = 17f
        setTextColor(palette.foreground)
        setHintTextColor(palette.muted)
        backgroundTintList = ColorStateList.valueOf(palette.accent)
        setPadding(0, dp(10), 0, dp(10))
    }

    private fun label(value: String, size: Float, color: Int) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        includeFontPadding = false
    }

    private fun dialogContext(): Context = ContextThemeWrapper(this, if (store.darkTheme) android.R.style.Theme_Material_Dialog_Alert else android.R.style.Theme_Material_Light_Dialog_Alert)

    private fun showDialog(dialog: AlertDialog) {
        dialog.show()
        dialog.window?.setBackgroundDrawable(GradientDrawable().apply { cornerRadius = dp(24).toFloat(); setColor(palette.dialog) })
        listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL).forEach { dialog.getButton(it)?.setTextColor(palette.accent) }
    }

    private fun marginParams(width: Int, height: Int, top: Int = 0) = LinearLayout.LayoutParams(width, height).apply { topMargin = dp(top) }
    private fun dp(value: Int) = dp(value.toFloat())
    private fun dp(value: Float) = (value * resources.displayMetrics.density + .5f).toInt()
    private fun accentWithAlpha(alpha: Int) = Color.argb(alpha, Color.red(palette.accent), Color.green(palette.accent), Color.blue(palette.accent))

    private fun countSummary(count: Long, goal: Int): String = if (goal > 0) {
        "${numberFormat.format(count)} total  ·  Target ${numberFormat.format(goal)}"
    } else "${numberFormat.format(count)} total"

    private fun persistAction(action: () -> Unit): Boolean = try {
        action()
        true
    } catch (_: IllegalStateException) {
        Toast.makeText(this, "Count could not be saved. Free device storage and try again.", Toast.LENGTH_LONG).show()
        false
    }

    override fun onPause() {
        counter.pauseAnimations()
        progressAnimator?.cancel()
        bannerAds.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::store.isInitialized && ::counter.isInitialized) {
            if (renderedDarkTheme != store.darkTheme || renderedColorThemeId != CounterThemes.resolve(store.colorThemeId).id) {
                counter.pauseAnimations()
                progressAnimator?.cancel()
                renderScreen()
            } else {
                counter.setColorTheme(CounterThemes.resolve(store.colorThemeId))
                counter.setDisplayLightEnabled(store.displayLightEnabled)
                refreshSettings()
                refreshCounterName()
                refreshCounter(false)
            }
        }
        if (::bannerAds.isInitialized) bannerAds.resume()
    }

    override fun onDestroy() {
        adaptiveContent = null
        if (::bannerAds.isInitialized) bannerAds.destroy()
        super.onDestroy()
    }

    /** Reflows native text instead of forcing labels into fixed-height buttons. */
    private inner class AdaptiveRow(
        private val first: View,
        private val second: View,
        private val equal: Boolean,
        private val gap: Int,
    ) : LinearLayout(this@MainActivity) {
        init {
            gravity = Gravity.CENTER_VERTICAL
            addView(first)
            addView(second)
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val available = View.MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
            fun naturalWidth(view: View): Int {
                if (view.visibility == View.GONE) return 0
                if (view is Button) return max(max(view.minimumWidth, view.minWidth),
                    ceil(view.paint.measureText(view.text.toString())).toInt() + view.paddingLeft + view.paddingRight + dp(8))
                view.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                return view.measuredWidth
            }
            val firstWidth = naturalWidth(first)
            val secondWidth = naturalWidth(second)
            val secondVisible = second.visibility != View.GONE
            val needed = if (equal) max(firstWidth, secondWidth) * 2 + gap else firstWidth + secondWidth + gap
            val stacked = secondVisible && needed > available
            val wantedOrientation = if (stacked) VERTICAL else HORIZONTAL
            if (orientation != wantedOrientation) orientation = wantedOrientation
            fun params(view: View, index: Int) {
                val wanted = LayoutParams(
                    if (stacked) { if (index == 0 || equal) -1 else -2 }
                    else if (equal || index == 0 && secondVisible) 0 else if (index == 0) -1 else -2,
                    -2,
                    if (!stacked && secondVisible && (equal || index == 0)) 1f else 0f,
                ).apply {
                    if (index == 1 && secondVisible) {
                        if (stacked) topMargin = gap else marginStart = gap
                    }
                    gravity = if (stacked && index == 1 && !equal) Gravity.END else Gravity.CENTER_VERTICAL
                }
                val current = view.layoutParams as? LayoutParams
                if (current == null || current.width != wanted.width || current.height != wanted.height || current.weight != wanted.weight ||
                    current.topMargin != wanted.topMargin || current.marginStart != wanted.marginStart || current.gravity != wanted.gravity) {
                    view.layoutParams = wanted
                }
            }
            params(first, 0)
            params(second, 1)
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    /** Uses the inset-adjusted scroll viewport; size changes never recreate app state. */
    private inner class AdaptiveContent(
        private val topPanel: View,
        private val heroPanel: View,
        private val bottomPanel: View,
    ) : ViewGroup(this@MainActivity) {
        private var viewportWidth = 0
        private var viewportHeight = 0
        private val topBounds = Rect()
        private val heroBounds = Rect()
        private val bottomBounds = Rect()

        init {
            addView(topPanel)
            addView(heroPanel)
            addView(bottomPanel)
        }

        fun setViewport(width: Int, height: Int) {
            val nextWidth = width.coerceAtLeast(0)
            val nextHeight = height.coerceAtLeast(0)
            if (nextWidth != viewportWidth || nextHeight != viewportHeight) {
                viewportWidth = nextWidth
                viewportHeight = nextHeight
                // ScrollView can reuse its child's unspecified-height measure spec.
                // Mark this measure dirty without scheduling another parent traversal.
                forceLayout()
            }
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = if (View.MeasureSpec.getMode(widthMeasureSpec) == View.MeasureSpec.UNSPECIFIED) viewportWidth
                else View.MeasureSpec.getSize(widthMeasureSpec)
            val horizontalPadding = min(dp(12), ((width - dp(190)) / 2).coerceAtLeast(0))
            val verticalPadding = dp(12)
            val availableWidth = (width - 2 * horizontalPadding).coerceAtLeast(1)
            val availableHeight = (viewportHeight - 2 * verticalPadding).coerceAtLeast(0)
            val wide = availableWidth >= dp(680)
            val gap = dp(12)
            val columnGap = dp(20)
            val infoWidth = if (wide) min(dp(420), max(dp(250), (availableWidth * .42f).toInt())) else availableWidth
            val heroWidthLimit = if (wide) availableWidth - infoWidth - columnGap else availableWidth
            fun measurePanel(view: View, panelWidth: Int) {
                view.forceLayout()
                view.measure(View.MeasureSpec.makeMeasureSpec(panelWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            }
            measurePanel(topPanel, infoWidth)
            measurePanel(bottomPanel, infoWidth)
            val heroBudget = if (wide) availableHeight else availableHeight - topPanel.measuredHeight - bottomPanel.measuredHeight - 2 * gap
            val preferredHeroWidth = min(heroWidthLimit, dp(440)).coerceAtLeast(1)
            val minimumHeroWidth = min(dp(190), preferredHeroWidth)
            var heroWidth = preferredHeroWidth
            fun measureHero() {
                counter.layoutParams.width = heroWidth
                counter.layoutParams.height = (heroWidth * 418f / 360f).toInt()
                counter.forceLayout()
                measurePanel(heroPanel, heroWidth)
            }
            measureHero()
            for (pass in 0 until 2) {
                val chromeHeight = heroPanel.measuredHeight - counter.measuredHeight
                val fittedWidth = ((heroBudget - chromeHeight).coerceAtLeast(0) * 360f / 418f).toInt()
                val nextWidth = min(preferredHeroWidth, max(minimumHeroWidth, fittedWidth))
                if (nextWidth == heroWidth) break
                heroWidth = nextWidth
                measureHero()
            }
            val neededHeight = if (wide) max(topPanel.measuredHeight + gap + bottomPanel.measuredHeight, heroPanel.measuredHeight)
                else topPanel.measuredHeight + heroPanel.measuredHeight + bottomPanel.measuredHeight + 2 * gap
            val height = max(viewportHeight, neededHeight + 2 * verticalPadding)
            setMeasuredDimension(View.resolveSize(width, widthMeasureSpec), View.resolveSize(height, heightMeasureSpec))
            val usableHeight = measuredHeight - 2 * verticalPadding
            if (wide) {
                val infoHeight = topPanel.measuredHeight + gap + bottomPanel.measuredHeight
                val infoTop = verticalPadding + max(0, (usableHeight - infoHeight) / 2)
                topBounds.set(horizontalPadding, infoTop, horizontalPadding + infoWidth, infoTop + topPanel.measuredHeight)
                bottomBounds.set(horizontalPadding, topBounds.bottom + gap, horizontalPadding + infoWidth, topBounds.bottom + gap + bottomPanel.measuredHeight)
                val heroLeft = horizontalPadding + infoWidth + columnGap + (heroWidthLimit - heroWidth) / 2
                val heroTop = verticalPadding + max(0, (usableHeight - heroPanel.measuredHeight) / 2)
                heroBounds.set(heroLeft, heroTop, heroLeft + heroWidth, heroTop + heroPanel.measuredHeight)
            } else {
                topBounds.set(horizontalPadding, verticalPadding, horizontalPadding + infoWidth, verticalPadding + topPanel.measuredHeight)
                val bottomTop = measuredHeight - verticalPadding - bottomPanel.measuredHeight
                bottomBounds.set(horizontalPadding, bottomTop, horizontalPadding + infoWidth, bottomTop + bottomPanel.measuredHeight)
                val heroTop = topBounds.bottom + gap + max(0, (bottomTop - gap - topBounds.bottom - gap - heroPanel.measuredHeight) / 2)
                val heroLeft = (width - heroWidth) / 2
                heroBounds.set(heroLeft, heroTop, heroLeft + heroWidth, heroTop + heroPanel.measuredHeight)
            }
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            topPanel.layout(topBounds.left, topBounds.top, topBounds.right, topBounds.bottom)
            heroPanel.layout(heroBounds.left, heroBounds.top, heroBounds.right, heroBounds.bottom)
            bottomPanel.layout(bottomBounds.left, bottomBounds.top, bottomBounds.right, bottomBounds.bottom)
        }
    }

    private class Palette(dark: Boolean, theme: CounterThemes.Theme) {
        val accent = if (dark) theme.darkAccent else theme.lightAccent
        val background = blend(Color.parseColor(if (dark) "#202222" else "#F3F4F0"), accent, .025f)
        val surface = blend(Color.parseColor(if (dark) "#141616" else "#FFFFFF"), accent, .025f)
        val dialog = blend(Color.parseColor(if (dark) "#272B29" else "#FFFFFF"), accent, .025f)
        val foreground = Color.parseColor(if (dark) "#F4F5F2" else "#222B25")
        val muted = Color.parseColor(if (dark) "#A9B2AC" else "#647168")
        val accentBorder = blend(surface, accent, .6f)
        val border = blend(Color.parseColor(if (dark) "#343A36" else "#DCE2DB"), accent, .12f)
        val glow = blend(background, accent, if (dark) .08f else .045f)

        private fun blend(base: Int, overlay: Int, fraction: Float): Int {
            fun channel(from: Int, to: Int) = (from + (to - from) * fraction).toInt()
            return Color.rgb(channel(Color.red(base), Color.red(overlay)), channel(Color.green(base), Color.green(overlay)), channel(Color.blue(base), Color.blue(overlay)))
        }
    }

    private class QuietBackground(dark: Boolean, theme: CounterThemes.Theme) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val palette = Palette(dark, theme)
        override fun draw(canvas: Canvas) {
            val width = bounds.width().toFloat()
            val height = bounds.height().toFloat()
            canvas.drawColor(palette.background)
            paint.shader = RadialGradient(width / 2f, height * .48f, width * .85f,
                palette.glow, Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawRect(bounds, paint)
            paint.shader = null
            paint.style = Paint.Style.FILL
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
        @Deprecated("Deprecated in Android") override fun getOpacity() = PixelFormat.OPAQUE
    }

    private class ColorSwatch(private val color: Int, private val size: Int) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun getIntrinsicWidth() = size
        override fun getIntrinsicHeight() = size
        override fun draw(canvas: Canvas) {
            val cx = bounds.exactCenterX()
            val cy = bounds.exactCenterY()
            val radius = min(bounds.width(), bounds.height()) / 2f - 1f
            paint.style = Paint.Style.FILL
            paint.color = color
            canvas.drawCircle(cx, cy, radius, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            paint.color = Color.argb(70, 140, 140, 140)
            canvas.drawCircle(cx, cy, radius, paint)
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
        @Deprecated("Deprecated in Android") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    /** Small procedural icons preserve APK size and stay sharp on every screen. */
    private class Glyph(private val icon: String, private val color: Int, private val size: Int) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun getIntrinsicWidth() = size
        override fun getIntrinsicHeight() = size
        override fun draw(canvas: Canvas) {
            canvas.save()
            canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
            canvas.scale(bounds.width() / 24f, bounds.height() / 24f)
            paint.color = color
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.7f
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            when (icon) {
                "theme" -> {
                    canvas.drawCircle(12f, 12f, 4f, paint)
                    for (i in 0..7) { canvas.save(); canvas.rotate(i * 45f, 12f, 12f); canvas.drawLine(12f, 2f, 12f, 4f, paint); canvas.restore() }
                }
                "light" -> {
                    canvas.drawCircle(12f, 9f, 5f, paint)
                    canvas.drawLine(9f, 14f, 9f, 18f, paint); canvas.drawLine(15f, 14f, 15f, 18f, paint)
                    canvas.drawLine(9f, 18f, 15f, 18f, paint); canvas.drawLine(10f, 21f, 14f, 21f, paint)
                    canvas.drawLine(12f, 1f, 12f, 0f, paint)
                    canvas.drawLine(3f, 9f, 1f, 9f, paint); canvas.drawLine(21f, 9f, 23f, 9f, paint)
                }
                "palette" -> {
                    val outline = Path().apply {
                        moveTo(12f, 2f)
                        cubicTo(6f, 2f, 2f, 6f, 2f, 12f)
                        cubicTo(2f, 18f, 6f, 22f, 12f, 22f)
                        cubicTo(15f, 22f, 17f, 19f, 15f, 17f)
                        cubicTo(14f, 15f, 17f, 14f, 19f, 14f)
                        cubicTo(24f, 14f, 23f, 3f, 12f, 2f)
                        close()
                    }
                    canvas.drawPath(outline, paint)
                    paint.style = Paint.Style.FILL
                    listOf(7f to 8f, 12f to 6f, 17f to 8f, 6f to 13f).forEach { (x, y) -> canvas.drawCircle(x, y, 1.2f, paint) }
                }
                "vibration" -> {
                    canvas.drawRoundRect(RectF(8f, 4f, 16f, 20f), 1.5f, 1.5f, paint)
                    canvas.drawLine(4f, 8f, 4f, 16f, paint); canvas.drawLine(1f, 10f, 1f, 14f, paint)
                    canvas.drawLine(20f, 8f, 20f, 16f, paint); canvas.drawLine(23f, 10f, 23f, 14f, paint)
                }
                "goal" -> { canvas.drawCircle(12f, 12f, 9f, paint); canvas.drawCircle(12f, 12f, 5f, paint); canvas.drawCircle(12f, 12f, 1f, paint) }
                "rename" -> {
                    val path = Path().apply {
                        moveTo(4f, 16f); lineTo(16f, 4f); lineTo(20f, 8f)
                        lineTo(8f, 20f); lineTo(3f, 21f); close()
                    }
                    canvas.drawPath(path, paint)
                    canvas.drawLine(13f, 7f, 17f, 11f, paint)
                }
                "save" -> {
                    val path = Path().apply { moveTo(4f, 3f); lineTo(17f, 3f); lineTo(21f, 7f); lineTo(21f, 21f); lineTo(3f, 21f); lineTo(3f, 3f); close() }
                    canvas.drawPath(path, paint); canvas.drawRect(7f, 3f, 16f, 10f, paint); canvas.drawRect(7f, 14f, 17f, 21f, paint)
                }
                "history" -> {
                    val left = Path().apply { moveTo(12f, 5f); cubicTo(8f, 2f, 4f, 3f, 2f, 4f); lineTo(2f, 20f); cubicTo(6f, 18f, 9f, 19f, 12f, 21f); close() }
                    val right = Path().apply { moveTo(12f, 5f); cubicTo(16f, 2f, 20f, 3f, 22f, 4f); lineTo(22f, 20f); cubicTo(18f, 18f, 15f, 19f, 12f, 21f); close() }
                    canvas.drawPath(left, paint); canvas.drawPath(right, paint)
                }
                "details" -> {
                    canvas.drawCircle(12f, 12f, 9f, paint)
                    canvas.drawLine(12f, 10f, 12f, 17f, paint)
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(12f, 6.5f, 1f, paint)
                }
            }
            canvas.restore()
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
        @Deprecated("Deprecated in Android") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}
