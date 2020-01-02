package me.jameshunt.tilegame

import android.view.MotionEvent
import android.view.View
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min

class OnInputTouchListener(private val inputDetectedHandler: (TouchInfo) -> Unit) :
    View.OnTouchListener {

    private var startX: Float = 0f
    private var startY: Float = 0f

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
            }
            MotionEvent.ACTION_UP -> {
                val xDiff = event.x - startX
                val yDiff = event.y - startY

                val minMoveDistance = min(
                    v.width,
                    v.height
                ) / (GameView.numTilesSize * 3f)
                if (max(xDiff.absoluteValue, yDiff.absoluteValue) > minMoveDistance) {
                    val direction = Direction.from(xDiff, yDiff)
                    inputDetectedHandler(TouchInfo(startX, startY, direction))
                }
            }
        }

        return true
    }

    data class TouchInfo(
        val xTouch: Float,
        val yTouch: Float,
        val direction: Direction
    )

    enum class Direction {
        Up,
        Down,
        Left,
        Right;

        companion object {
            fun from(xDiff: Float, yDiff: Float): Direction {
                return when (xDiff.absoluteValue > yDiff.absoluteValue) {
                    true -> if (xDiff > 0) Right else Left
                    false -> if (yDiff > 0) Down else Up
                }
            }
        }
    }
}