package me.jameshunt.tilegame

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

enum class TileType {
    One,
    Two,
    Three,
    Four,
    Five,
    Six;

    private companion object {
        val paint1 = Paint().apply { color = Color.BLUE }
        val paint2 = Paint().apply { color = Color.RED }
        val paint3 = Paint().apply { color = Color.GREEN }
        val paint4 = Paint().apply { color = Color.CYAN }
        val paint5 = Paint().apply { color = Color.MAGENTA }
        val paint6 = Paint().apply { color = Color.YELLOW }
    }

    val paint: Paint
        get() = when (this) {
            One -> paint1
            Two -> paint2
            Three -> paint3
            Four -> paint4
            Five -> paint5
            Six -> paint6
        }
}

class Tile(val type: TileType) {
    fun render(
        x: TileXCoord,
        y: TileYCoord,
        canvas: Canvas,
        screenContext: ScreenContext,
        tick: Int,
        state: GameState
    ) {
        val tileSize = screenContext.gridSize / GameView.numTilesSize.toFloat()
        val tileRadius = tileSize / 4f

        val fallingOffset = fallingOffset(x, y, tileSize, tick, state)
        val sizeOffset = sizeOffset(x, y, tileSize, tick, state)
        val inputMoveOffset = inputMoveOffset(x, y, tileSize, tick, state)

        val leftOffset = fallingOffset.x + sizeOffset + inputMoveOffset.x
        val topOffset = fallingOffset.y + sizeOffset + inputMoveOffset.y
        val rightOffset = fallingOffset.x + inputMoveOffset.x - sizeOffset
        val bottomOffset = fallingOffset.y - sizeOffset + inputMoveOffset.y

        canvas.drawRoundRect(
            (x * tileSize) + screenContext.gridStartX + leftOffset,
            (y * tileSize) + screenContext.gridStartY + topOffset,
            (x * tileSize) + tileSize + screenContext.gridStartX + rightOffset,
            (y * tileSize) + tileSize + screenContext.gridStartY + bottomOffset,
            tileRadius,
            tileRadius,
            type.paint
        )
    }

    fun renderNewlyVisible(
        i: Int,
        canvas: Canvas,
        screenContext: ScreenContext,
        tick: Int,
        state: GameState
    ) {
        check(state is GameState.TilesFalling)

        val numTilesSize = GameView.numTilesSize
        val (x, y) = when (state.fallingFromDirection) {
            GravitySensor.TileFromDirection.Top -> Pair(i, -1)
            GravitySensor.TileFromDirection.Bottom -> Pair(i, numTilesSize)
            GravitySensor.TileFromDirection.Left -> Pair(-1, i)
            GravitySensor.TileFromDirection.Right -> Pair(numTilesSize, (numTilesSize - 1) - i)
        } as Pair<TileXCoord, TileYCoord>

        render(x, y, canvas, screenContext, tick, state)
    }

    private fun fallingOffset(
        x: TileXCoord,
        y: TileYCoord,
        tileSize: Float,
        tick: Int,
        state: GameState
    ): Offset {
        return (state as? GameState.TilesFalling)?.let {
            val fallingYOffsetPerTick = tileSize / state.tickDuration
            val fallingYOffset =
                fallingYOffsetPerTick * ((tick - state.startTick) % state.tickDuration)

            when {
                state.fallingFromDirection == GravitySensor.TileFromDirection.Top &&
                        state.lowestPosYOfFallableTiles[x] >= y -> Offset(0f, fallingYOffset)
                state.fallingFromDirection == GravitySensor.TileFromDirection.Left
                        && state.lowestPosYOfFallableTiles[y] >= x -> Offset(fallingYOffset, 0f)
                state.fallingFromDirection == GravitySensor.TileFromDirection.Right -> {
                    val fixedLowest = state.lowestPosYOfFallableTiles.reversed()[y]
                    when (fixedLowest >= (GameView.numTilesSize - 1) - x) {
                        true -> Offset(-fallingYOffset, 0f)
                        false -> Offset(0f, 0f)
                    }
                }
                state.fallingFromDirection == GravitySensor.TileFromDirection.Bottom -> {
                    when (state.lowestPosYOfFallableTiles[x] >= (GameView.numTilesSize - 1) - y) {
                        true -> Offset(0f, -fallingYOffset)
                        false -> Offset(0f, 0f)
                    }
                }
                else -> Offset(0f, 0f)
            }
        } ?: Offset(0f, 0f)
    }

    private fun sizeOffset(
        x: TileXCoord,
        y: TileYCoord,
        tileSize: Float,
        tick: Int,
        state: GameState
    ): Float {
        if (y == -1) return 6f

        return (state as? GameState.RemovingTiles)?.let {
            val sizeShrinkPerTick = tileSize / 2 / state.tickDuration

            when (it.newBoardAfterRemove[x][y] == null) {
                true -> (tileSize / 14) + tileSize - (sizeShrinkPerTick * ((tick - state.startTick) % state.tickDuration))
                false -> 6f
            }
        } ?: 6f
    }

    private fun inputMoveOffset(
        x: TileXCoord,
        y: TileYCoord,
        tileSize: Float,
        tick: Int,
        state: GameState
    ): Offset {
        return (state as? GameState.InputDetected)?.let {
            val isTouchedTile = state.touched.x == x && state.touched.y == y
            val isSwitchWithTile = state.switchWith.x == x && state.switchWith.y == y

            val offsetPerTick = tileSize / state.tickDuration
            val moveOffset = offsetPerTick * ((tick - state.startTick) % state.tickDuration)

            when {
                isTouchedTile -> when (state.direction) {
                    OnInputTouchListener.Direction.Up -> Offset(0f, -moveOffset)
                    OnInputTouchListener.Direction.Down -> Offset(0f, moveOffset)
                    OnInputTouchListener.Direction.Left -> Offset(-moveOffset, 0f)
                    OnInputTouchListener.Direction.Right -> Offset(moveOffset, 0f)
                }
                isSwitchWithTile -> when (state.direction) {
                    OnInputTouchListener.Direction.Up -> Offset(0f, moveOffset)
                    OnInputTouchListener.Direction.Down -> Offset(0f, -moveOffset)
                    OnInputTouchListener.Direction.Left -> Offset(moveOffset, 0f)
                    OnInputTouchListener.Direction.Right -> Offset(-moveOffset, 0f)
                }
                else -> Offset(0f, 0f)
            }
        } ?: Offset(0f, 0f)
    }

    data class Offset(
        val x: Float,
        val y: Float
    )
}