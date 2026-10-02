package com.dottak.inputdetect

import android.view.InputDevice
import android.view.MotionEvent

/**
 * Classifies a MotionEvent as a physical finger touch, a physical mouse, or an
 * event injected by a remote-control tool (scrcpy, adb `input`, Vysor, etc.).
 *
 * scrcpy (and `adb shell input`) build MotionEvents themselves and push them through
 * InputManager.injectInputEvent(). Those events are not tied to a real kernel input
 * device, which leaves fingerprints that an app can read:
 *  - deviceId is 0 (or -1 / unknown), so InputDevice.getDevice() is null or virtual
 *  - mouse clicks/hover from the PC arrive as SOURCE_MOUSE / TOOL_TYPE_MOUSE on a phone
 *  - contact geometry (touchMajor/minor, size) is 0 and pressure is a constant
 */
object InputClassifier {

    enum class Verdict(val label: String) {
        PHYSICAL_TOUCH("실제 손가락 터치"),
        PHYSICAL_MOUSE("물리 마우스(OTG/BT)"),
        INJECTED_TOUCH("원격 주입 터치 (scrcpy/adb 등)"),
        INJECTED_MOUSE("원격 주입 마우스 (scrcpy 마우스 등)"),
    }

    data class Result(
        val score: Int,
        val isMouse: Boolean,
        val reasons: List<String>,
        val deviceName: String,
    ) {
        val verdict: Verdict
            get() = when {
                score >= INJECTED_THRESHOLD && isMouse -> Verdict.INJECTED_MOUSE
                score >= INJECTED_THRESHOLD -> Verdict.INJECTED_TOUCH
                isMouse -> Verdict.PHYSICAL_MOUSE
                else -> Verdict.PHYSICAL_TOUCH
            }
    }

    const val INJECTED_THRESHOLD = 60

    private val SUSPICIOUS_DEVICE_NAME = Regex("scrcpy|uinput|uhid|vysor|anydesk|teamviewer", RegexOption.IGNORE_CASE)

    fun classify(event: MotionEvent): Result {
        var score = 0
        var isMouse = false
        val reasons = mutableListOf<String>()

        val device = InputDevice.getDevice(event.deviceId)
        val deviceName = device?.name ?: "(none)"

        when {
            event.deviceId <= 0 -> {
                score += 100
                reasons += "deviceId=${event.deviceId}: 실제 입력장치 없이 주입됨"
            }
            device == null -> {
                score += 60
                reasons += "deviceId=${event.deviceId}에 해당하는 InputDevice 없음"
            }
            device.isVirtual -> {
                score += 80
                reasons += "가상 InputDevice ($deviceName)"
            }
        }
        if (device != null && SUSPICIOUS_DEVICE_NAME.containsMatchIn(deviceName)) {
            score += 60
            reasons += "원격제어 도구로 의심되는 장치명: $deviceName"
        }

        val toolType = if (event.pointerCount > 0) event.getToolType(0) else MotionEvent.TOOL_TYPE_UNKNOWN
        if (event.isFromSource(InputDevice.SOURCE_MOUSE) || toolType == MotionEvent.TOOL_TYPE_MOUSE) {
            isMouse = true
            reasons += "마우스 소스/툴타입"
        }
        if (event.buttonState != 0) {
            isMouse = true
            reasons += "buttonState=${event.buttonState} (마우스 버튼)"
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE, MotionEvent.ACTION_HOVER_EXIT -> {
                isMouse = true
                reasons += "호버 이벤트 (커서가 화면 위에 있음)"
            }
        }

        if (!isMouse && event.touchMajor == 0f && event.touchMinor == 0f && event.size == 0f) {
            score += 15
            reasons += "접촉 면적 0 (touchMajor/minor/size)"
        }

        return Result(score, isMouse, reasons, deviceName)
    }

    fun describe(type: String, event: MotionEvent): String {
        val toolType = if (event.pointerCount > 0) event.getToolType(0) else MotionEvent.TOOL_TYPE_UNKNOWN
        return "type=$type x=${"%.1f".format(event.x)} y=${"%.1f".format(event.y)} " +
            "source=0x${event.source.toString(16)} deviceId=${event.deviceId} " +
            "toolType=$toolType buttonState=${event.buttonState} actionButton=${event.actionButton} " +
            "pressure=${event.pressure} size=${event.size} touchMajor=${event.touchMajor}"
    }
}
