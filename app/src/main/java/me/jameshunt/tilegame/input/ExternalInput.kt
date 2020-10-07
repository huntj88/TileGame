package me.jameshunt.tilegame.input

import me.jameshunt.tilegame.Step
import me.jameshunt.tilegame.TileXCoordinate
import me.jameshunt.tilegame.TileYCoordinate

/**
 * The only mutable state I actually need, because its external user input
 */
class ExternalInput {
    var lastTouchInput: TouchInput? = null

    // affected by gravity (rotate/tilt)
    var directionToFallFrom: FallFromDirection = FallFromDirection.Top
        private set

    fun setDirectionToFallFrom(stepState: Step, directionToFallFrom: FallFromDirection) {
        if (stepState !is Step.TilesFalling) {
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

    fun opposite() = when(this) {
        Up -> Down
        Down -> Up
        Left -> Right
        Right -> Left
    }
}
