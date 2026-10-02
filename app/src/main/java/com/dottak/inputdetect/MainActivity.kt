package com.dottak.inputdetect

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var verdictView: TextView
    private lateinit var detailView: TextView
    private lateinit var logView: TextView

    private val logLines = ArrayDeque<String>()

    // Strongest result seen during the current DOWN..UP gesture
    private var gestureResult: InputClassifier.Result? = null
    private var hoverCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        verdictView = findViewById(R.id.verdict)
        detailView = findViewById(R.id.detail)
        logView = findViewById(R.id.log)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val type = when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER -> "HOVER_ENTER"
            MotionEvent.ACTION_HOVER_MOVE -> "HOVER_MOVE"
            MotionEvent.ACTION_HOVER_EXIT -> "HOVER_EXIT"
            MotionEvent.ACTION_BUTTON_PRESS -> "BUTTON_PRESS"
            MotionEvent.ACTION_BUTTON_RELEASE -> "BUTTON_RELEASE"
            MotionEvent.ACTION_SCROLL -> "SCROLL"
            else -> null
        }
        if (type != null) {
            val result = InputClassifier.classify(event)
            logEvent(type, event, result)
            // Hover moves are frequent; refresh the verdict but only log every 10th one on screen
            if (event.actionMasked != MotionEvent.ACTION_HOVER_MOVE || hoverCount++ % 10 == 0) {
                showResult("커서 $type", result)
            }
        }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val type = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> "DOWN"
            MotionEvent.ACTION_MOVE -> "MOVE"
            MotionEvent.ACTION_UP -> "UP"
            MotionEvent.ACTION_CANCEL -> "CANCEL"
            else -> null
        }
        if (type != null) {
            val result = InputClassifier.classify(event)
            logEvent(type, event, result)

            if (event.actionMasked == MotionEvent.ACTION_DOWN) gestureResult = null
            val prev = gestureResult
            if (prev == null || result.score > prev.score || (result.isMouse && !prev.isMouse)) {
                gestureResult = result
            }
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                val final = gestureResult ?: result
                val durationMs = event.eventTime - event.downTime
                Log.i(TAG, "GESTURE verdict=${final.verdict} score=${final.score} durationMs=$durationMs device=\"${final.deviceName}\"")
                showResult("터치 제스처 (${durationMs}ms)", final)
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun logEvent(type: String, event: MotionEvent, result: InputClassifier.Result) {
        Log.d(TAG, "${InputClassifier.describe(type, event)} verdict=${result.verdict} score=${result.score}")
    }

    private fun showResult(title: String, result: InputClassifier.Result) {
        val verdict = result.verdict
        verdictView.text = verdict.label
        verdictView.setBackgroundColor(
            when (verdict) {
                InputClassifier.Verdict.PHYSICAL_TOUCH -> Color.parseColor("#2E7D32")
                InputClassifier.Verdict.PHYSICAL_MOUSE -> Color.parseColor("#1565C0")
                InputClassifier.Verdict.INJECTED_TOUCH,
                InputClassifier.Verdict.INJECTED_MOUSE -> Color.parseColor("#C62828")
            }
        )
        detailView.text = buildString {
            append("$title · 점수 ${result.score} · 장치 \"${result.deviceName}\"\n")
            result.reasons.forEach { append("• $it\n") }
        }
        logLines.addFirst("[$title] ${verdict.label} (score ${result.score})")
        while (logLines.size > 30) logLines.removeLast()
        logView.text = logLines.joinToString("\n")
    }

    companion object {
        const val TAG = "INPUT_DETECT"
    }
}
