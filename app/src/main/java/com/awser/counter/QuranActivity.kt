package com.awser.counter

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Reads the bundled Quran text locally. This screen has no advertising controller. */
class QuranActivity : Activity() {
    private lateinit var scroll: ScrollView
    private lateinit var readingContent: LinearLayout
    private lateinit var choiceButtons: Map<String, Button>
    private var readingId = "yaseen"
    private var dark = true
    private var backgroundColor = Color.BLACK
    private var foreground = Color.WHITE
    private var muted = Color.LTGRAY
    private var surface = Color.DKGRAY
    private var border = Color.GRAY
    private var accent = Color.WHITE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = CounterStore(this)
        dark = preferences.darkTheme
        val theme = CounterThemes.resolve(preferences.colorThemeId)
        backgroundColor = Color.parseColor(if (dark) "#202222" else "#F3F4F0")
        foreground = Color.parseColor(if (dark) "#F4F5F2" else "#222B25")
        muted = Color.parseColor(if (dark) "#A9B2AC" else "#647168")
        surface = Color.parseColor(if (dark) "#141616" else "#FFFFFF")
        border = Color.parseColor(if (dark) "#343A36" else "#DCE2DB")
        accent = if (dark) theme.darkAccent else theme.lightAccent
        readingId = savedInstanceState?.getString(READING_STATE)?.takeIf { id -> QuranContent.readings.any { it.id == id } } ?: "yaseen"
        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor
        @Suppress("DEPRECATION")
        val lightBars = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            if (Build.VERSION.SDK_INT >= 26) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = if (dark) 0 else lightBars
        if (Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }

        val root = LinearLayout(this).apply {
            tag = "quran_screen"
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            setBackgroundColor(backgroundColor)
            setPadding(dp(12), dp(12), dp(12), 0)
        }
        val back = button("quran_back", "Back").apply {
            contentDescription = "Back to counter"
            setOnClickListener { finish() }
        }
        val title = label("Quran", 24f, foreground).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            accessibilityHeadingCompat()
        }
        root.addView(AdaptiveHeader(back, title), LinearLayout.LayoutParams(-1, -2))

        val choices = AdaptiveChoices()
        choiceButtons = QuranContent.readings.associate { reading ->
            val choice = button("quran_choice_${reading.id}", reading.title).apply {
                setOnClickListener {
                    if (readingId != reading.id) {
                        readingId = reading.id
                        showReading()
                        scroll.scrollTo(0, 0)
                    }
                }
            }
            choices.addView(choice)
            reading.id to choice
        }
        root.addView(choices, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12); bottomMargin = dp(8) })
        readingContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(24))
        }
        scroll = ScrollView(this).apply {
            tag = "quran_scroll"
            isFillViewport = true
            addView(readingContent, FrameLayout.LayoutParams(-1, -2))
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        if (Build.VERSION.SDK_INT >= 35) {
            root.setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(dp(12) + bars.left, dp(12) + bars.top, dp(12) + bars.right, bars.bottom)
                insets
            }
        }
        setContentView(root)
        root.requestApplyInsets()
        showReading()
        val savedScroll = savedInstanceState?.getInt(SCROLL_STATE, 0) ?: 0
        if (savedScroll > 0) scroll.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
            override fun onLayoutChange(view: View, left: Int, top: Int, right: Int, bottom: Int,
                oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
                if (scroll.height > 0 && readingContent.height > 0) {
                    scroll.scrollTo(0, savedScroll)
                    scroll.removeOnLayoutChangeListener(this)
                }
            }
        })
    }

    private fun showReading() {
        val reading = QuranContent.readings.first { it.id == readingId }
        val verses = QuranContent.load(this, reading.id)
        choiceButtons.forEach { (id, choice) ->
            choice.isSelected = id == readingId
            choice.background = buttonBackground(id == readingId)
            choice.contentDescription = QuranContent.readings.first { it.id == id }.title + if (id == readingId) ", selected" else ""
        }
        readingContent.removeAllViews()
        readingContent.addView(label(reading.title, 20f, foreground).apply {
            tag = "quran_reading_title"
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            accessibilityHeadingCompat()
        }, params(-1, -2))
        readingContent.addView(label(reading.reference, 13f, accent).apply {
            tag = "quran_reference"
            textDirection = View.TEXT_DIRECTION_LTR
        }, params(-1, -2, 6))
        readingContent.addView(label(if (verses.size == 1) "1 verse" else "${verses.size} verses", 12f, muted).apply {
            tag = "quran_verse_count"
        }, params(-1, -2, 6))
        QuranContent.basmala(this, reading.id)?.let { text ->
            readingContent.addView(arabicText(text).apply {
                tag = "quran_basmala"
                gravity = Gravity.CENTER
            }, params(-1, -2, 24))
        }
        verses.forEach { verse ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(16), dp(14), dp(12))
                background = GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat()
                    setColor(surface)
                    setStroke(dp(1), border)
                }
            }
            card.addView(arabicText(verse.text).apply { tag = "quran_verse_${verse.number}" }, params(-1, -2))
            card.addView(label("Verse ${verse.number}", 12f, accent).apply {
                tag = "quran_verse_number_${verse.number}"
                gravity = Gravity.RIGHT
                textDirection = View.TEXT_DIRECTION_LTR
                contentDescription = "Verse ${verse.number}. ${reading.reference}"
            }, params(-1, -2, 12))
            readingContent.addView(card, params(-1, -2, 14))
        }
        val attribution = QuranContent.ATTRIBUTION
        val linkStart = attribution.indexOf(QuranContent.SOURCE_URL)
        val linkedAttribution = SpannableString(attribution).apply {
            setSpan(URLSpan(QuranContent.SOURCE_URL), linkStart, linkStart + QuranContent.SOURCE_URL.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        readingContent.addView(label("", 12f, muted).apply {
            tag = "quran_attribution"
            text = linkedAttribution
            setLinkTextColor(accent)
            movementMethod = LinkMovementMethod.getInstance()
        }, params(-1, -2, 24))
    }

    private fun arabicText(text: String) = label(text, 28f, foreground).apply {
        textDirection = View.TEXT_DIRECTION_RTL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        gravity = Gravity.RIGHT
        includeFontPadding = true
        setLineSpacing(dp(6).toFloat(), 1.28f)
        if (Build.VERSION.SDK_INT >= 28) isFallbackLineSpacing = true
        setTextIsSelectable(true)
    }

    private fun label(value: String, size: Float, color: Int) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        includeFontPadding = true
    }

    private fun button(viewTag: String, value: String) = Button(this).apply {
        tag = viewTag
        text = value
        textSize = 14f
        isAllCaps = false
        setSingleLine(false)
        setTextColor(this@QuranActivity.foreground)
        minimumWidth = dp(48)
        minWidth = dp(48)
        minimumHeight = dp(48)
        minHeight = dp(48)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = buttonBackground(false)
    }

    private fun buttonBackground(selected: Boolean): RippleDrawable {
        val shape = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(surface)
            setStroke(dp(if (selected) 2 else 1), if (selected) accent else border)
        }
        val ripple = Color.argb(40, Color.red(accent), Color.green(accent), Color.blue(accent))
        return RippleDrawable(ColorStateList.valueOf(ripple), shape, null)
    }

    private fun View.accessibilityHeadingCompat() {
        if (Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
    }

    private fun params(width: Int, height: Int, top: Int = 0) = LinearLayout.LayoutParams(width, height).apply { topMargin = dp(top) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()

    private fun setRowParams(view: View, next: LinearLayout.LayoutParams) {
        val current = view.layoutParams as? LinearLayout.LayoutParams
        if (current == null || current.width != next.width || current.height != next.height || current.weight != next.weight ||
            current.topMargin != next.topMargin || current.marginStart != next.marginStart) view.layoutParams = next
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(READING_STATE, readingId)
        outState.putInt(SCROLL_STATE, scroll.scrollY)
        super.onSaveInstanceState(outState)
    }

    /** Uses actual window width, and permits labels to grow with the system font setting. */
    private inner class AdaptiveChoices : LinearLayout(this@QuranActivity) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val gap = dp(6)
            val natural = (0 until childCount).sumOf { index ->
                val child = getChildAt(index) as Button
                child.paint.measureText(child.text.toString()).toInt() + child.paddingLeft + child.paddingRight + dp(8)
            } + gap * (childCount - 1)
            val stacked = natural > width
            orientation = if (stacked) VERTICAL else HORIZONTAL
            for (index in 0 until childCount) {
                val child = getChildAt(index)
                setRowParams(child, LayoutParams(if (stacked) -1 else 0, -2, if (stacked) 0f else 1f).apply {
                    if (index > 0) { if (stacked) topMargin = gap else marginStart = gap }
                })
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    private inner class AdaptiveHeader(private val back: Button, private val title: TextView) : LinearLayout(this@QuranActivity) {
        init { gravity = Gravity.CENTER_VERTICAL; addView(back); addView(title) }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            back.measure(unspecified, unspecified)
            title.measure(unspecified, unspecified)
            val stacked = back.measuredWidth + title.measuredWidth + dp(12) > MeasureSpec.getSize(widthMeasureSpec)
            orientation = if (stacked) VERTICAL else HORIZONTAL
            setRowParams(back, LayoutParams(-2, -2))
            setRowParams(title, LayoutParams(if (stacked) -1 else 0, -2, if (stacked) 0f else 1f).apply {
                if (stacked) topMargin = dp(8) else marginStart = dp(12)
            })
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    private companion object {
        const val READING_STATE = "quran.reading"
        const val SCROLL_STATE = "quran.scroll"
    }
}
