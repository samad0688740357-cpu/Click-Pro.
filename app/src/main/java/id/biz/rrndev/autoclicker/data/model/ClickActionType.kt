package id.biz.rrndev.autoclicker.data.model

const val DEFAULT_TAP_DURATION_MS = 50L
const val DEFAULT_LONG_PRESS_DURATION_MS = 1000L
const val DEFAULT_SWIPE_DURATION_MS = 500L
const val MIN_LONG_PRESS_DURATION_MS = 100L

object ClickActionType {
    const val CLICK = 0
    const val SWIPE = 1
    const val LONG_PRESS = 2

    fun normalize(value: Int): Int = when (value) {
        CLICK, SWIPE, LONG_PRESS -> value
        else -> CLICK
    }
}
