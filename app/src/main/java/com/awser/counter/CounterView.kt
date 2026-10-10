package com.awser.counter

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.FrameLayout
import kotlin.math.max
import kotlin.math.min

/** A lightweight, resolution-independent physical counter. Its hit areas are real buttons. */
class CounterView(context: Context) : FrameLayout(context) {
    var onIncrement: (() -> Unit)? = null
    var onReset: (() -> Unit)? = null
    private var count = 0L
    private var dark = true
    private var lightEnabled = true
    private var hasCount = false
    private var pressing = 0f
    private var displayPulse = 0f
    private var pressAnimator: ValueAnimator? = null
    private var displayAnimator: ValueAnimator? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var scale = 1f
    private var offsetX = 0f
    private var offsetY = 0f
    val counterButton = Button(context).apply {
        tag = "counter"
        contentDescription = "Add one. Count 0"
        background = ColorDrawable(Color.TRANSPARENT)
        minimumWidth = 0
        minimumHeight = 0
        setPadding(0, 0, 0, 0)
        setOnClickListener {
            animateClickPress()
            onIncrement?.invoke()
        }
        setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> animatePress(1f)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> animatePress(0f)
            }
            false
        }
    }
    private val resetButton = object : Button(context) {
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                // A circular48dp target avoids intercepting the larger silver count button.
                val dx = event.x - width / 2f
                val dy = event.y - height / 2f
                val radius = min(width, height) / 2f
                if (dx * dx + dy * dy > radius * radius) return false
            }
            return super.onTouchEvent(event)
        }
    }.apply {
        tag = "reset"
        contentDescription = "Reset counter"
        background = ColorDrawable(Color.TRANSPARENT)
        minimumWidth = 0
        minimumHeight = 0
        setPadding(0, 0, 0, 0)
        setOnClickListener { onReset?.invoke() }
    }

    init {
        setWillNotDraw(false)
        // Native shadows and the fine metal texture require no images or external library.
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(counterButton)
        addView(resetButton)
    }

    fun update(value: Long, darkTheme: Boolean) {
        val increased = hasCount && value > count
        count = value
        hasCount = true
        dark = darkTheme
        counterButton.contentDescription = "Add one. Count $value"
        if (increased) animateBacklight()
        invalidate()
    }

    /** LCD lighting is independent of the count, physical buttons and app theme. */
    fun setDisplayLightEnabled(enabled: Boolean) {
        if (lightEnabled == enabled) return
        lightEnabled = enabled
        displayAnimator?.cancel()
        displayAnimator = null
        displayPulse = 0f
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        val w = resolveSize((360 * density).toInt(), widthMeasureSpec)
        val h = resolveSize((418 * density).toInt(), heightMeasureSpec)
        scale = min(w / 360f, h / 418f)
        offsetX = (w - 360f * scale) / 2f
        offsetY = (h - 418f * scale) / 2f
        fun position(view: View, x: Float, y: Float, size: Float) {
            val next = LayoutParams((size * scale).toInt(), (size * scale).toInt()).apply {
                leftMargin = (offsetX + x * scale).toInt()
                topMargin = (offsetY + y * scale).toInt()
            }
            val old = view.layoutParams as? LayoutParams
            if (old == null || old.width != next.width || old.height != next.height || old.leftMargin != next.leftMargin || old.topMargin != next.topMargin) {
                view.layoutParams = next
            }
        }
        position(counterButton, 102f, 214f, 156f)
        // A 48dp reset touch target surrounds the small, physical reset button.
        val resetSize = max(48f * density / scale, 48f)
        position(resetButton, 268f - resetSize / 2f, 209f - resetSize / 2f, resetSize)
        // Measure after sizing the buttons, so the very first frame has correct hit areas.
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(scale, scale)
        drawBody(canvas)
        drawDisplay(canvas)
        drawButtons(canvas)
        canvas.restore()
    }

    private fun drawBody(canvas: Canvas) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        path.reset()
        path.moveTo(180f, 19f)
        path.cubicTo(104f, 19f, 51f, 35f, 32f, 80f)
        path.cubicTo(9f, 133f, 24f, 216f, 38f, 267f)
        path.cubicTo(50f, 308f, 34f, 367f, 82f, 391f)
        path.cubicTo(111f, 407f, 249f, 407f, 278f, 391f)
        path.cubicTo(326f, 367f, 310f, 308f, 322f, 267f)
        path.cubicTo(336f, 216f, 351f, 133f, 328f, 80f)
        path.cubicTo(309f, 35f, 256f, 19f, 180f, 19f)
        path.close()
        paint.style = Paint.Style.FILL
        paint.alpha = 255
        paint.shader = LinearGradient(0f, 20f, 0f, 400f, intArrayOf(Color.rgb(14, 17, 15), Color.rgb(3, 5, 4), Color.rgb(1, 2, 1)), null, Shader.TileMode.CLAMP)
        paint.setShadowLayer(12f, 0f, 9f, if (dark) Color.argb(110, 0, 0, 0) else Color.argb(48, 0, 0, 0))
        canvas.drawPath(path, paint)
        paint.clearShadowLayer()
        canvas.save()
        canvas.scale(.94f, .952f, 180f, 210f)
        paint.shader = LinearGradient(0f, 22f, 0f, 392f, intArrayOf(Color.rgb(48, 52, 49), Color.rgb(24, 28, 25), Color.rgb(8, 10, 9)), null, Shader.TileMode.CLAMP)
        canvas.drawPath(path, paint)
        paint.shader = null
        paint.color = Color.argb(46, 173, 181, 175)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = .7f
        canvas.drawPath(path, paint)
        canvas.restore()
        paint.style = Paint.Style.FILL
        text(canvas, "TASBIH COUNTER", 180f, 59f, 10f, Color.rgb(137, 148, 141))
    }

    private fun drawDisplay(canvas: Canvas) {
        val display = RectF(58f, 78f, 302f, 174f)
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(5, 12, 7)
        canvas.drawRoundRect(RectF(55f, 75f, 305f, 178f), 18f, 18f, paint)
        paint.alpha = 255
        val top = if (lightEnabled) backlightColor(Color.rgb(116, 227, 143)) else Color.rgb(151, 168, 156)
        val bottom = if (lightEnabled) backlightColor(Color.rgb(77, 207, 113)) else Color.rgb(128, 148, 136)
        paint.shader = LinearGradient(0f, 78f, 0f, 174f, top, bottom, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(display, 15f, 15f, paint)
        paint.shader = null
        paint.color = if (lightEnabled) Color.argb(85, 224, 255, 231) else Color.argb(45, 229, 235, 231)
        canvas.drawRoundRect(RectF(62f, 82f, 298f, 85f), 2f, 2f, paint)
        text(canvas, "TOTAL COUNT", 180f, 96f, 7.5f,
            if (lightEnabled) Color.rgb(34, 92, 51) else Color.rgb(46, 66, 53))
        val digits = count.toString().padStart(6, '0')
        val digitWidth = min(30f, 216f / digits.length)
        val start = 180f - digits.length * digitWidth / 2f
        val scaleDigits = min(1f, digitWidth / 30f)
        val leading = digits.length - count.toString().length
        digits.forEachIndexed { index, char ->
            canvas.save()
            canvas.translate(start + index * digitWidth + 2f, 109f + (1f - scaleDigits) * 24f)
            canvas.scale(scaleDigits, scaleDigits)
            drawDigit(canvas, char.digitToInt(), index < leading)
            canvas.restore()
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = .8f
        paint.color = if (lightEnabled) Color.argb(80, 212, 255, 223) else Color.argb(65, 207, 221, 212)
        canvas.drawRoundRect(display, 15f, 15f, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawDigit(canvas: Canvas, number: Int, leading: Boolean) {
        val mask = intArrayOf(0x3f, 0x06, 0x5b, 0x4f, 0x66, 0x6d, 0x7d, 0x07, 0x7f, 0x6f)[number]
        // Seven-segment polygons, clockwise from the top; g is the middle segment.
        val segments = arrayOf(
            floatArrayOf(4f, 0f, 22f, 0f, 18f, 4f, 8f, 4f),
            floatArrayOf(23f, 1f, 23f, 21f, 19f, 18f, 19f, 5f),
            floatArrayOf(23f, 25f, 23f, 45f, 19f, 41f, 19f, 29f),
            floatArrayOf(4f, 46f, 22f, 46f, 18f, 42f, 8f, 42f),
            floatArrayOf(3f, 25f, 3f, 45f, 7f, 41f, 7f, 29f),
            floatArrayOf(3f, 1f, 3f, 21f, 7f, 18f, 7f, 5f),
            floatArrayOf(4f, 23f, 8f, 20f, 18f, 20f, 22f, 23f, 18f, 26f, 8f, 26f)
        )
        segments.forEachIndexed { index, points ->
            val active = mask and (1 shl index) != 0
            paint.color = if (active && !leading) {
                if (lightEnabled) Color.rgb(17, 59, 31) else Color.rgb(18, 34, 24)
            } else Color.argb(if (active) 23 else 10, 26, 101, 46)
            path.reset()
            path.moveTo(points[0], points[1])
            for (i in 2 until points.size step 2) path.lineTo(points[i], points[i + 1])
            path.close()
            canvas.drawPath(path, paint)
        }
    }

    private fun drawButtons(canvas: Canvas) {
        val cx = 180f
        val depression = pressing.coerceIn(-.08f, 1f)
        val cy = 292f + depression * 2.5f
        val radius = 74f * (1f - depression * .025f)
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(3, 5, 4)
        canvas.drawCircle(cx, 293f, 79f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.3f
        paint.color = Color.argb(78, 126, 139, 130)
        canvas.drawCircle(cx, 292f, 79f, paint)
        paint.style = Paint.Style.FILL
        paint.alpha = 255
        paint.setShadowLayer(5f - depression.coerceAtLeast(0f) * 3f, 0f, 4f - depression.coerceAtLeast(0f) * 3f, Color.argb(170, 0, 0, 0))
        paint.shader = SweepGradient(cx, cy, intArrayOf(Color.rgb(253, 253, 252), Color.rgb(214, 217, 215), Color.WHITE, Color.rgb(227, 230, 228), Color.rgb(248, 248, 247), Color.rgb(207, 212, 208), Color.rgb(253, 253, 252)), null)
        canvas.drawCircle(cx, cy, radius, paint)
        paint.clearShadowLayer()
        paint.shader = RadialGradient(cx - 18f, cy - 24f, 105f, intArrayOf(Color.argb(120, 255, 255, 255), Color.argb(0, 255, 255, 255), Color.argb(28, 45, 52, 47)), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, radius, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = .55f
        for (i in 6..72 step 3) {
            paint.color = Color.argb(13, 75, 92, 81)
            canvas.drawCircle(cx, cy, i.toFloat() * (1f - depression * .025f), paint)
        }
        paint.strokeWidth = 1.2f
        paint.color = Color.argb(185, 255, 255, 255)
        canvas.drawCircle(cx, cy, radius - 1f, paint)
        paint.style = Paint.Style.FILL
        paint.alpha = 255
        paint.shader = RadialGradient(263f, 201f, 26f, Color.WHITE, Color.rgb(198, 207, 201), Shader.TileMode.CLAMP)
        canvas.drawCircle(268f, 209f, 17f, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f
        paint.color = Color.argb(170, 255, 255, 255)
        canvas.drawCircle(268f, 209f, 16f, paint)
        paint.style = Paint.Style.FILL
    }

    private fun text(canvas: Canvas, value: String, x: Float, y: Float, size: Float, color: Int) {
        paint.shader = null
        paint.color = color
        paint.textSize = size
        paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(value, x, y, paint)
    }

    private fun animatePress(target: Float) {
        pressAnimator?.cancel()
        if (!animationsEnabled()) {
            pressing = target
            invalidate()
            return
        }
        pressAnimator = ValueAnimator.ofFloat(pressing, target).apply {
            duration = if (target == 1f) 85 else 240
            interpolator = if (target == 1f) DecelerateInterpolator() else OvershootInterpolator(.65f)
            addUpdateListener { pressing = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private fun animateClickPress() {
        if (!animationsEnabled()) {
            pressAnimator?.cancel()
            pressing = 0f
            invalidate()
            return
        }
        // TalkBack and keyboard activation receive the same physical feedback as a tap.
        pressing = max(pressing, .85f)
        animatePress(0f)
    }

    private fun animateBacklight() {
        displayAnimator?.cancel()
        if (!lightEnabled || !animationsEnabled()) {
            displayPulse = 0f
            invalidate()
            return
        }
        displayAnimator = ValueAnimator.ofFloat(displayPulse, 1f, 0f).apply {
            duration = 340
            interpolator = DecelerateInterpolator()
            addUpdateListener { displayPulse = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private fun backlightColor(base: Int): Int {
        val amount = displayPulse * .35f
        fun channel(value: Int, highlight: Int) = (value + (highlight - value) * amount).toInt()
        return Color.rgb(channel(Color.red(base), 193), channel(Color.green(base), 255), channel(Color.blue(base), 208))
    }

    fun pauseAnimations() {
        pressAnimator?.cancel()
        displayAnimator?.cancel()
        pressAnimator = null
        displayAnimator = null
        pressing = 0f
        displayPulse = 0f
        invalidate()
    }

    private fun animationsEnabled(): Boolean = if (Build.VERSION.SDK_INT >= 26) {
        ValueAnimator.areAnimatorsEnabled()
    } else {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }

    override fun onDetachedFromWindow() {
        pauseAnimations()
        super.onDetachedFromWindow()
    }
}
