package com.jagastech.stepwgndsq

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * v4.0 Full-Screen Graphical NR-MZ25 Console Activity for the 9-inch Stepwgn Head Unit.
 * Draws the live glowing spline curve + 13 full-height vertical touch faders + 4-Seat Cabin Focus
 * in 100% full-screen landscape mode!
 */
class MainActivity : Activity() {

    private var dsqService: DsqAudioEngineService? = null
    private var selectedBandIndex = 1 // 80 Hz default
    private lateinit var activeBandTitle: TextView
    private lateinit var subwStatusTv: TextView
    private lateinit var loudnessStatusTv: TextView
    private lateinit var spatialStatusTv: TextView
    private lateinit var seatingStatusTv: TextView
    private lateinit var eqGraphicView: EqGraphicCanvasView

    private val bandLabels = arrayOf("50", "80", "125", "200", "315", "500", "800", "1.25k", "2k", "3.15k", "5k", "8k", "16k")

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as DsqAudioEngineService.LocalBinder
            dsqService = binder.getService()
            refreshHeadUnitUI()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            dsqService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        enableImmersiveFullscreen()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B0E14"))
            setPadding(16, 10, 16, 10)
        }

        fun makeBtn(label: String, bgHex: String = "#1E293B", weight: Float = 0f, onClick: () -> Unit): Button {
            return Button(this).apply {
                text = label
                textSize = 11f
                typeface = Typeface.MONOSPACE
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor(bgHex))
                setPadding(10, 4, 10, 4)
                val lp = if (weight > 0f) {
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
                } else {
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                }
                lp.setMargins(4, 2, 4, 2)
                layoutParams = lp
                setOnClickListener { onClick() }
            }
        }

        // ROW 1: Active Band Readout + NR-MZ25 Step Buttons + Subw Bass Trim
        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#131822"))
            setPadding(12, 6, 12, 6)
        }
        activeBandTitle = TextView(this).apply {
            text = "B2 (80Hz): +3dB"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#F59E0B"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        row1.addView(activeBandTitle)
        row1.addView(makeBtn("◀ BAND") { selectedBandIndex = (selectedBandIndex + 12) % 13; refreshHeadUnitUI() })
        row1.addView(makeBtn("-1 dB", "#B45309") { stepSelectedBand(-1f) })
        row1.addView(makeBtn("0 dB") { setSelectedBand(0f) })
        row1.addView(makeBtn("+1 dB", "#047857") { stepSelectedBand(1f) })
        row1.addView(makeBtn("BAND ▶") { selectedBandIndex = (selectedBandIndex + 1) % 13; refreshHeadUnitUI() })

        subwStatusTv = TextView(this).apply {
            text = "  SUBW: 0dB "
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#38BDF8"))
        }
        row1.addView(subwStatusTv)
        row1.addView(makeBtn("SUB -", "#0369A1") {
            dsqService?.let { it.subwTrimDb = (it.subwTrimDb - 1f).coerceIn(-6f, 4f); it.applyGainsToEngine(); refreshHeadUnitUI() }
        })
        row1.addView(makeBtn("SUB +", "#0369A1") {
            dsqService?.let { it.subwTrimDb = (it.subwTrimDb + 1f).coerceIn(-6f, 4f); it.applyGainsToEngine(); refreshHeadUnitUI() }
        })

        // ROW 2: Clean Loudness + Sony S-Force 3D + 4-Position Cabin Seating
        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#161F2E"))
            setPadding(12, 6, 12, 6)
        }
        loudnessStatusTv = TextView(this).apply {
            text = "LOUD:+3dB "
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#10B981"))
        }
        row2.addView(loudnessStatusTv)
        listOf(0f to "OFF", 3f to "+3dB", 6f to "+6dB", 9f to "+9dB").forEach { (db, lbl) ->
            row2.addView(makeBtn(lbl, "#065F46") {
                dsqService?.let { it.loudnessGainDb = db; it.applyGainsToEngine(); refreshHeadUnitUI() }
            })
        }

        spatialStatusTv = TextView(this).apply {
            text = " 3D:65% "
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#A78BFA"))
        }
        row2.addView(spatialStatusTv)
        listOf(0 to "OFF", 40 to "APPLE", 65 to "S-FORCE", 95 to "ATMOS").forEach { (pct, lbl) ->
            row2.addView(makeBtn(lbl, "#4C1D95") {
                dsqService?.let { it.spatialStrengthPct = pct; it.applyGainsToEngine(); refreshHeadUnitUI() }
            })
        }

        seatingStatusTv = TextView(this).apply {
            text = " SEAT:ALL "
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#38BDF8"))
        }
        row2.addView(seatingStatusTv)
        listOf(0 to "ALL", 1 to "DRV-R", 2 to "PASS-L", 3 to "REAR").forEach { (mode, lbl) ->
            row2.addView(makeBtn(lbl, "#0284C7") {
                dsqService?.let { it.seatingMode = mode; it.applyGainsToEngine(); refreshHeadUnitUI() }
            })
        }

        // ROW 3: Anti-Rattle Presets
        val row3 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 4, 0, 4)
        }
        val presets = linkedMapOf(
            "SpaceBuds 4 (Safe)" to floatArrayOf(0f, 3f, 2f, 0f, -2f, -1f, 0f, 1f, 2f, 2f, 3f, 3f, 3f),
            "SpaceBuds 2" to floatArrayOf(0f, 2f, 2f, 0f, -1f, -1f, 0f, 1f, 2f, 2f, 2f, 3f, 2f),
            "Stepwgn De-Box" to floatArrayOf(-1f, 3f, 2f, -1f, -3f, -1f, 0f, 1f, 2f, 2f, 2f, 3f, 3f),
            "Amapiano Safe" to floatArrayOf(1f, 4f, 3f, -1f, -2f, -1f, 0f, 1f, 2f, 2f, 3f, 4f, 3f),
            "Flat 0dB" to FloatArray(13) { 0f }
        )
        for ((name, curve) in presets) {
            row3.addView(makeBtn(name, "#1E293B", 1f) {
                dsqService?.let { it.currentGainsDb = curve.clone(); it.applyGainsToEngine(); refreshHeadUnitUI() }
            })
        }

        // ROW 4: Full-Screen Graphical Spline Curve + 13 Tall Vertical Touch Fader Columns
        eqGraphicView = EqGraphicCanvasView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        root.addView(row1)
        root.addView(row2)
        root.addView(row3)
        root.addView(eqGraphicView)
        setContentView(root)

        Intent(this, DsqAudioEngineService::class.java).also { intent ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enableImmersiveFullscreen()
    }

    private fun enableImmersiveFullscreen() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )
    }

    private fun stepSelectedBand(delta: Float) {
        val current = dsqService?.currentGainsDb?.get(selectedBandIndex) ?: 0f
        setSelectedBand((current + delta).coerceIn(-12f, 12f))
    }

    private fun setSelectedBand(newDb: Float) {
        dsqService?.let {
            it.currentGainsDb[selectedBandIndex] = newDb
            it.applyGainsToEngine()
            refreshHeadUnitUI()
        }
    }

    private fun refreshHeadUnitUI() {
        val svc = dsqService ?: return
        val freq = svc.centerFrequenciesHz[selectedBandIndex]
        val gain = svc.currentGainsDb[selectedBandIndex].roundToInt()
        activeBandTitle.text = "B${selectedBandIndex + 1} (${freq}Hz): ${if (gain >= 0) "+" else ""}${gain}dB"
        val subw = svc.subwTrimDb.roundToInt()
        subwStatusTv.text = "  SUBW:${if (subw >= 0) "+" else ""}${subw}dB "
        loudnessStatusTv.text = "LOUD:+${svc.loudnessGainDb.roundToInt()}dB "
        spatialStatusTv.text = " 3D:${svc.spatialStrengthPct}% "
        val seatNames = arrayOf("ALL", "DRV-R", "PASS-L", "REAR")
        seatingStatusTv.text = " SEAT:${seatNames[svc.seatingMode.coerceIn(0, 3)]} "
        eqGraphicView.invalidate()
    }

    /**
     * Custom Full-Screen Interactive Graphic Equalizer View:
     * Renders 13 vertical touch faders + live glowing Base, Left S-Force, and Right S-Force spline curves!
     */
    inner class EqGraphicCanvasView(context: Context) : View(context) {
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0F141D") }
        private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#263248")
            strokeWidth = 2f
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(8f, 8f), 0f)
        }
        private val zeroLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#475569")
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }
        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
            strokeWidth = 14f
            strokeCap = Paint.Cap.ROUND
        }
        private val activeColPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#22F59E0B")
            style = Paint.Style.FILL
        }
        private val baseCurvePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#38BDF8")
            strokeWidth = 6f
            style = Paint.Style.STROKE
        }
        private val leftSForcePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#10B981")
            strokeWidth = 3.5f
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(10f, 6f), 0f)
        }
        private val rightSForcePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#A855F7")
            strokeWidth = 3.5f
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(6f, 10f), 0f)
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 20f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.MONOSPACE
        }
        private val curvePath = Path()
        private val leftPath = Path()
        private val rightPath = Path()
        private val fillPath = Path()
        private val colRect = RectF()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val svc = dsqService ?: return
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            canvas.drawRect(0f, 0f, w, h, bgPaint)

            val topPad = 42f
            val botPad = 44f
            val usableH = h - topPad - botPad
            val midY = topPad + usableH * 0.5f
            val colW = w / 13f

            // Draw dB horizontal grid lines (+12, +6, 0, -6, -12 dB)
            canvas.drawLine(0f, topPad, w, topPad, gridPaint)
            canvas.drawLine(0f, topPad + usableH * 0.25f, w, topPad + usableH * 0.25f, gridPaint)
            canvas.drawLine(0f, midY, w, midY, zeroLinePaint)
            canvas.drawLine(0f, topPad + usableH * 0.75f, w, topPad + usableH * 0.75f, gridPaint)
            canvas.drawLine(0f, topPad + usableH, w, topPad + usableH, gridPaint)

            val xs = FloatArray(13)
            val baseYs = FloatArray(13)
            val leftYs = FloatArray(13)
            val rightYs = FloatArray(13)

            for (i in 0 until 13) {
                val cx = colW * i + colW * 0.5f
                xs[i] = cx
                val baseDb = svc.currentGainsDb[i].coerceIn(-12f, 12f)
                val leftDb = svc.getChannelBandGainDb(i, false)
                val rightDb = svc.getChannelBandGainDb(i, true)

                baseYs[i] = midY - (baseDb / 12f) * (usableH * 0.5f)
                leftYs[i] = midY - (leftDb / 12f) * (usableH * 0.5f)
                rightYs[i] = midY - (rightDb / 12f) * (usableH * 0.5f)

                if (i == selectedBandIndex) {
                    colRect.set(colW * i + 4f, 4f, colW * (i + 1) - 4f, h - 4f)
                    canvas.drawRoundRect(colRect, 14f, 14f, activeColPaint)
                }

                // Vertical fader track
                canvas.drawLine(cx, topPad, cx, topPad + usableH, trackPaint)

                // Top dB value label
                val gInt = baseDb.roundToInt()
                textPaint.color = if (gInt > 0) Color.parseColor("#F59E0B") else if (gInt < 0) Color.parseColor("#38BDF8") else Color.parseColor("#94A3B8")
                textPaint.textSize = 19f
                canvas.drawText("${if (gInt >= 0) "+" else ""}${gInt}dB", cx, 28f, textPaint)

                // Bottom frequency label
                textPaint.color = if (i == selectedBandIndex) Color.parseColor("#F59E0B") else Color.WHITE
                textPaint.textSize = 19f
                canvas.drawText(bandLabels[i], cx, h - 12f, textPaint)
            }

            fun buildSpline(ptsX: FloatArray, ptsY: FloatArray, outPath: Path) {
                outPath.reset()
                outPath.moveTo(ptsX[0], ptsY[0])
                for (i in 0 until 12) {
                    val p0x = ptsX[if (i == 0) 0 else i - 1]
                    val p0y = ptsY[if (i == 0) 0 else i - 1]
                    val p1x = ptsX[i]; val p1y = ptsY[i]
                    val p2x = ptsX[i + 1]; val p2y = ptsY[i + 1]
                    val p3x = ptsX[if (i + 2 < 13) i + 2 else 12]
                    val p3y = ptsY[if (i + 2 < 13) i + 2 else 12]
                    val cp1x = p1x + (p2x - p0x) / 6f
                    val cp1y = p1y + (p2y - p0y) / 6f
                    val cp2x = p2x - (p3x - p1x) / 6f
                    val cp2y = p2y - (p3y - p1y) / 6f
                    outPath.cubicTo(cp1x, cp1y, cp2x, cp2y, p2x, p2y)
                }
            }

            buildSpline(xs, baseYs, curvePath)
            buildSpline(xs, leftYs, leftPath)
            buildSpline(xs, rightYs, rightPath)

            fillPath.set(curvePath)
            fillPath.lineTo(xs[12], midY)
            fillPath.lineTo(xs[0], midY)
            fillPath.close()
            fillPaint.shader = LinearGradient(0f, topPad, 0f, topPad + usableH, Color.parseColor("#4438BDF8"), Color.parseColor("#0538BDF8"), Shader.TileMode.CLAMP)
            canvas.drawPath(fillPath, fillPaint)

            canvas.drawPath(leftPath, leftSForcePaint)
            canvas.drawPath(rightPath, rightSForcePaint)
            canvas.drawPath(curvePath, baseCurvePaint)

            // Draw interactive fader knobs
            for (i in 0 until 13) {
                val isSel = i == selectedBandIndex
                thumbPaint.color = if (isSel) Color.parseColor("#F59E0B") else Color.parseColor("#38BDF8")
                canvas.drawCircle(xs[i], baseYs[i], if (isSel) 15f else 11f, thumbPaint)
                thumbPaint.color = Color.parseColor("#0B0E14")
                canvas.drawCircle(xs[i], baseYs[i], if (isSel) 6f else 4f, thumbPaint)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                val w = width.toFloat()
                val h = height.toFloat()
                if (w > 0f && h > 0f) {
                    val colIdx = ((event.x / w) * 13f).toInt().coerceIn(0, 12)
                    selectedBandIndex = colIdx
                    val topPad = 42f
                    val botPad = 44f
                    val usableH = h - topPad - botPad
                    val normY = ((event.y - topPad) / usableH).coerceIn(0f, 1f)
                    val newDb = ((0.5f - normY) * 24f).roundToInt().toFloat().coerceIn(-12f, 12f)
                    setSelectedBand(newDb)
                }
                return true
            }
            return super.onTouchEvent(event)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(connection)
    }
}
