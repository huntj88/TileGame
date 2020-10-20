package me.jameshunt.tilegame.input

import me.jameshunt.tilegame.*
import kotlin.math.floor

/**
 * The only mutable state I actually need, because its external user input.
 * Applied immutably in state machine
 */
class ExternalInput(var config: GameView.Config = GameView.Config()) {
    var lastTouchInput: TouchInput? = null

    // affected by gravity (rotate/tilt)
    var directionToFallFrom: FallFromDirection = FallFromDirection.Top
        private set

    fun setDirectionToFallFrom(step: Step, directionToFallFrom: FallFromDirection) {
        if (step !is Step.TilesFalling) {
            this.directionToFallFrom = directionToFallFrom
        }
    }
}

data class TouchInput(
    val touched: TileCoordinate,
    val switchWith: TileCoordinate,
    val moveDirection: MoveDirection
) {
    data class TileCoordinate(
        val x: TileXCoordinate,
        val y: TileYCoordinate
    )
}

fun OnInputTouchListener.TouchInfo.toInput(
    gridSize: Int,
    screenContext: ScreenContext
): TouchInput {
    val xTouchInGrid = this.xTouch - screenContext.gridStartX
    val xTile = floor(xTouchInGrid / screenContext.gridSizePixels * gridSize).toInt()

    val yTouchInGrid = this.yTouch - screenContext.gridStartY
    val yTile = floor(yTouchInGrid / screenContext.gridSizePixels * gridSize).toInt()

    val touched = TouchInput.TileCoordinate(xTile, yTile)
    val switchWith = when (this.moveDirection) {
        MoveDirection.Up -> TouchInput.TileCoordinate(xTile, yTile - 1)
        MoveDirection.Down -> TouchInput.TileCoordinate(xTile, yTile + 1)
        MoveDirection.Left -> TouchInput.TileCoordinate(xTile - 1, yTile)
        MoveDirection.Right -> TouchInput.TileCoordinate(xTile + 1, yTile)
    }

    return TouchInput(touched, switchWith, this.moveDirection)
}


/**
 * affected by gravity (rotate/tilt)
 */
enum class FallFromDirection {
    Top,
    Bottom,
    Left,
    Right
}

/**
 * affected by sliding a tile
 */
enum class MoveDirection {
    Up,
    Down,
    Left,
    Right;

    fun opposite(): MoveDirection = when (this) {
        Up -> Down
        Down -> Up
        Left -> Right
        Right -> Left
    }
}
