package com.awser.counter

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
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
import android.view.WindowInsets
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
    private lateinit var vibrationButton: Button
    private lateinit var bannerAds: BannerAdController
    private lateinit var adPrivacyButton: Button
    private var adPrivacyRequired = false
    private var resizeCounter: (() -> Unit)? = null
    private var progressAnimator: ObjectAnimator? = null
    private var palette = Palette(true)
    private val numberFormat = NumberFormat.getIntegerInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = CounterStore(this)
        bannerAds = BannerAds.create(this) { required ->
            runOnUiThread {
                adPrivacyRequired = required
                if (::adPrivacyButton.isInitialized) adPrivacyButton.visibility = if (required) View.VISIBLE else View.GONE
                resizeCounter?.invoke()
            }
        }
        renderScreen()
        bannerAds.start()
    }

    private fun renderScreen() {
        palette = Palette(store.darkTheme)
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
        root = FrameLayout(this).apply { background = QuietBackground(store.darkTheme) }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            clipToPadding = false
        }
        val metrics = resources.displayMetrics
        val wide = metrics.widthPixels > metrics.heightPixels && metrics.widthPixels / metrics.density >= 580f
        val content = LinearLayout(this).apply {
            orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            gravity = if (wide) Gravity.CENTER_VERTICAL else Gravity.CENTER_HORIZONTAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        val infoColumn = if (wide) LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, 0, dp(20), 0) } else content
        val counterColumn = if (wide) LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL } else content
        fun measureDeviceWidth(usableWidth: Float, usableHeight: Float): Float = if (wide) {
            min((usableHeight - 58f) * 360f / 418f, 360f).coerceAtLeast(190f)
        } else {
            val chromeHeight = 260f + if (adPrivacyRequired) 36f else 0f
            min(min(usableWidth - 24f, 400f), (usableHeight - chromeHeight) * 360f / 418f).coerceAtLeast(190f)
        }
        val deviceWidth = measureDeviceWidth(metrics.widthPixels / metrics.density, metrics.heightPixels / metrics.density)
        if (wide) {
            content.addView(infoColumn, LinearLayout.LayoutParams(0, -2, 1f))
            content.addView(counterColumn, LinearLayout.LayoutParams(dp(deviceWidth), -2))
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val titleGroup = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleGroup.addView(label("Tasbih Counter", 21f, palette.foreground).apply {
            gravity = Gravity.START
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }, marginParams(-1, -2))
        titleGroup.addView(label("Z I K R", 9f, palette.muted), marginParams(-1, -2, top = 4))
        header.addView(titleGroup, LinearLayout.LayoutParams(0, -2, 1f))
        themeButton = iconButton("theme", "Theme", "theme") {
            counter.pauseAnimations()
            progressAnimator?.cancel()
            store.setDarkTheme(!store.darkTheme)
            renderScreen()
        }.apply {
            contentDescription = if (store.darkTheme) "Switch to light theme" else "Switch to dark theme"
        }
        lightButton = iconButton("light", "Display light", "light") {
            store.setDisplayLightEnabled(!store.displayLightEnabled)
            counter.setDisplayLightEnabled(store.displayLightEnabled)
            refreshSettings()
        }
        header.addView(themeButton, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(8) })
        header.addView(lightButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        infoColumn.addView(header, marginParams(-1, -2))

        val topControls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        vibrationButton = iconButton("vibration", "Haptics", "vibration") {
            store.setVibrationEnabled(!store.vibrationEnabled)
            refreshSettings()
            if (store.vibrationEnabled) counter.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
        goalButton = iconButton("goal", "Target ${store.goal}", "goal") { showGoalDialog() }
        val rename = iconButton("rename", "Rename", "rename") { showRenameDialog() }
        listOf(vibrationButton, goalButton, rename).forEach { button ->
            topControls.addView(FrameLayout(this).apply {
                addView(button, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER))
            }, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        infoColumn.addView(topControls, marginParams(-1, -2, top = 10))
        if (!wide) content.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))

        counterNameLabel = label("", 14f, palette.muted).apply {
            tag = "counter_name_label"
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }
        infoColumn.addView(counterNameLabel, marginParams(-1, -2, top = 12))

        counter = CounterView(this).apply {
            setDisplayLightEnabled(store.displayLightEnabled)
            onIncrement = {
                val previous = store.count
                val updated = store.increment()
                refreshCounter(true)
                if (store.vibrationEnabled && updated != previous) {
                    performHapticFeedback(if (updated == store.goal.toLong()) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.KEYBOARD_TAP)
                }
                if (updated != previous && updated == store.goal.toLong()) {
                    announceForAccessibility("Target reached. Count $updated.")
                    Toast.makeText(this@MainActivity, "Target reached · ${numberFormat.format(updated)}", Toast.LENGTH_SHORT).show()
                }
            }
            onReset = { showResetDialog() }
        }
        counterColumn.addView(counter, marginParams(dp(deviceWidth), dp(deviceWidth * 418f / 360f), top = 6))
        countLabel = label("", 11f, palette.muted).apply {
            tag = "count_label"
            gravity = Gravity.CENTER
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_NONE
        }
        counterColumn.addView(countLabel, marginParams(-1, -2, top = 4))
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            tag = "progress"
            max = 1000
            progressTintList = ColorStateList.valueOf(palette.accent)
            progressBackgroundTintList = ColorStateList.valueOf(palette.border)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        counterColumn.addView(progress, marginParams(-1, dp(3), top = 8))
        if (!wide) content.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        val footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        footer.addView(actionButton("save", "Save count") { showSaveDialog() }, LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginEnd = dp(10) })
        footer.addView(actionButton("history", "Saved counts") { showHistoryDialog() }, LinearLayout.LayoutParams(0, dp(50), 1f))
        infoColumn.addView(footer, marginParams(-1, -2, top = 12))
        val privacyRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        privacyRow.addView(label("Saved on your device", 10f, palette.muted).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, -2, 1f))
        adPrivacyButton = actionButton("ad_privacy", "Ad privacy") { bannerAds.showPrivacyOptions() }.apply {
            visibility = if (adPrivacyRequired) View.VISIBLE else View.GONE
        }
        privacyRow.addView(adPrivacyButton, LinearLayout.LayoutParams(dp(104), dp(48)))
        infoColumn.addView(privacyRow, marginParams(-1, -2, top = 6))
        scroll.addView(content, FrameLayout.LayoutParams(-1, -2))
        val bannerContainer = FrameLayout(this).apply { tag = "ad_banner_container" }
        val screen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(bannerContainer, LinearLayout.LayoutParams(-1, -2))
        }
        root.addView(screen, FrameLayout.LayoutParams(-1, -1))
        resizeCounter = {
            if (scroll.width > 0 && scroll.height > 0) {
                val width = measureDeviceWidth(scroll.width / metrics.density, scroll.height / metrics.density)
                val deviceParams = counter.layoutParams
                if (deviceParams.width != dp(width) || deviceParams.height != dp(width * 418f / 360f)) {
                    deviceParams.width = dp(width)
                    deviceParams.height = dp(width * 418f / 360f)
                    counter.layoutParams = deviceParams
                    if (wide) counterColumn.layoutParams = counterColumn.layoutParams.apply { this.width = dp(width) }
                }
            }
        }
        scroll.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> resizeCounter?.invoke() }
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
    }

    private fun refreshSettings() {
        vibrationButton.contentDescription = "Haptics ${if (store.vibrationEnabled) "on" else "off"}. Toggle haptics"
        vibrationButton.setCompoundDrawablesWithIntrinsicBounds(Glyph("vibration", if (store.vibrationEnabled) palette.accent else palette.muted, dp(21)), null, null, null)
        vibrationButton.background = circularButtonBackground(store.vibrationEnabled)
        goalButton.contentDescription = "Change target. Current target ${store.goal}"
        lightButton.contentDescription = if (store.displayLightEnabled) "Turn display light off" else "Turn display light on"
        lightButton.setCompoundDrawablesWithIntrinsicBounds(Glyph("light", if (store.displayLightEnabled) palette.accent else palette.muted, dp(21)), null, null, null)
        lightButton.background = circularButtonBackground(store.displayLightEnabled)
        if (Build.VERSION.SDK_INT >= 26) {
            vibrationButton.tooltipText = "Haptics ${if (store.vibrationEnabled) "on" else "off"}"
            goalButton.tooltipText = "Target ${numberFormat.format(store.goal)}"
            themeButton.tooltipText = themeButton.contentDescription
            lightButton.tooltipText = lightButton.contentDescription
        }
    }

    private fun refreshCounterName() {
        counterNameLabel.text = store.counterName
        counterNameLabel.contentDescription = "Counter name: ${store.counterName}"
    }

    private fun refreshCounter(animate: Boolean) {
        counter.update(store.count, store.darkTheme)
        countLabel.text = "${numberFormat.format(store.count)} total  ·  Target ${numberFormat.format(store.goal)}"
        countLabel.contentDescription = "Count ${store.count}. Target ${store.goal}"
        val nextProgress = (min(store.count.toDouble() / store.goal, 1.0) * 1000).toInt()
        progressAnimator?.cancel()
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
            .setPositiveButton("Reset") { _, _ -> store.reset(); refreshCounter(false) }.create())
    }

    private fun showSaveDialog() {
        val input = input(store.counterName, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES).apply {
            tag = "session_name"
            hint = "Count name"
            filters = arrayOf(InputFilter.LengthFilter(60))
            selectAll()
        }
        val box = LinearLayout(dialogContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(10), dp(24), dp(8))
            addView(label("${numberFormat.format(store.count)} total · Your counter keeps its current count.", 14f, palette.muted))
            addView(input, marginParams(-1, -2, top = 12))
        }
        showDialog(AlertDialog.Builder(dialogContext()).setTitle("Save count").setView(box)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                store.saveSession(input.text.toString().trim().ifEmpty { store.counterName })
                Toast.makeText(this, "Count saved", Toast.LENGTH_SHORT).show()
            }.create())
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
            row.addView(label("${numberFormat.format(session.count)} total · Target ${numberFormat.format(session.goal)}", 13f, palette.accent), marginParams(-1, -2, top = 5))
            row.addView(label(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(session.timestamp)), 11f, palette.muted), marginParams(-1, -2, top = 4))
            list.addView(row, marginParams(-1, -2, top = 9))
        }
        showDialog(dialog)
    }

    private fun showSessionDialog(session: CounterStore.Session) {
        showDialog(AlertDialog.Builder(dialogContext()).setTitle(session.label)
            .setMessage("${numberFormat.format(session.count)} total · Target ${numberFormat.format(session.goal)}\n\nRestore replaces the current count, name and target.")
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Delete") { _, _ ->
                showDialog(AlertDialog.Builder(dialogContext()).setTitle("Delete saved count?").setMessage(session.label)
                    .setNegativeButton("Cancel", null).setPositiveButton("Delete") { _, _ -> store.deleteSession(session.id); showHistoryDialog() }.create())
            }
            .setPositiveButton("Restore") { _, _ ->
                store.restoreSession(session.id)
                refreshSettings(); refreshCounter(false); refreshCounterName()
                Toast.makeText(this, "Saved count restored", Toast.LENGTH_SHORT).show()
            }.create())
    }

    private fun showGoalDialog() {
        showDialog(AlertDialog.Builder(dialogContext()).setTitle("Choose your target")
            .setItems(arrayOf("25", "50", "100", "Custom target")) { _, which ->
                val presets = intArrayOf(25, 50, 100)
                if (which < 3) setGoal(presets[which]) else showCustomGoal()
            }.setNegativeButton("Cancel", null).create())
    }

    private fun showCustomGoal() {
        val input = input(store.goal.toString(), InputType.TYPE_CLASS_NUMBER).apply {
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
            else { setGoal(value); dialog.dismiss() }
        }
    }

    private fun setGoal(value: Int) {
        store.setGoal(value)
        refreshSettings()
        refreshCounter(false)
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
            else { store.setCounterName(name); refreshCounterName(); dialog.dismiss() }
        }
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
        setCompoundDrawablesWithIntrinsicBounds(Glyph(icon, palette.foreground, dp(21)), null, null, null)
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
        minimumWidth = 0
        minWidth = 0
        setPadding(dp(10), 0, dp(10), 0)
        background = buttonBackground(true)
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
        return RippleDrawable(ColorStateList.valueOf(Color.argb(40, 140, 220, 174)), shape, mask)
    }

    private fun buttonBackground(outlined: Boolean): Drawable {
        val shape = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(14).toFloat()
            setColor(palette.surface)
            setStroke(dp(1), if (outlined) palette.accentBorder else palette.border)
        }
        return RippleDrawable(ColorStateList.valueOf(Color.argb(45, 126, 235, 170)), shape, null)
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

    override fun onPause() {
        counter.pauseAnimations()
        progressAnimator?.cancel()
        bannerAds.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::bannerAds.isInitialized) bannerAds.resume()
    }

    override fun onDestroy() {
        resizeCounter = null
        if (::bannerAds.isInitialized) bannerAds.destroy()
        super.onDestroy()
    }

    private class Palette(dark: Boolean) {
        val background = Color.parseColor(if (dark) "#202222" else "#F3F4F0")
        val surface = Color.parseColor(if (dark) "#141616" else "#FFFFFF")
        val dialog = Color.parseColor(if (dark) "#272B29" else "#FFFFFF")
        val foreground = Color.parseColor(if (dark) "#F4F5F2" else "#222B25")
        val muted = Color.parseColor(if (dark) "#A9B2AC" else "#647168")
        val accent = Color.parseColor(if (dark) "#74DFA5" else "#23754C")
        val accentBorder = Color.parseColor(if (dark) "#48775B" else "#7CA58A")
        val border = Color.parseColor(if (dark) "#343A36" else "#DCE2DB")
    }

    private class QuietBackground(private val dark: Boolean) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun draw(canvas: Canvas) {
            val width = bounds.width().toFloat()
            val height = bounds.height().toFloat()
            canvas.drawColor(Color.parseColor(if (dark) "#202222" else "#F3F4F0"))
            paint.shader = RadialGradient(width / 2f, height * .48f, width * .85f,
                Color.parseColor(if (dark) "#292D2B" else "#E9EEE8"), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawRect(bounds, paint)
            paint.shader = null
            paint.style = Paint.Style.FILL
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
        @Deprecated("Deprecated in Android") override fun getOpacity() = PixelFormat.OPAQUE
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
            }
            canvas.restore()
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
        @Deprecated("Deprecated in Android") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}
