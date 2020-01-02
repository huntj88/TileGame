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

        val fallingYOffset = fallingOffset(x, y, tileSize, tick, state)
        val sizeOffset = sizeOffset(x, y, tileSize, tick, state)
        val inputMoveOffset = inputMoveOffset(x, y, tileSize, tick, state)

        val leftOffset = sizeOffset + inputMoveOffset.first
        val topOffset = fallingYOffset + sizeOffset + inputMoveOffset.second
        val rightOffset = inputMoveOffset.first - sizeOffset
        val bottomOffset = fallingYOffset - sizeOffset + inputMoveOffset.second

        canvas.drawRoundRect(
            (x * tileSize) + screenContext.gridStartX + leftOffset,
            ((y - GameView.numTilesSize) * tileSize) + screenContext.gridStartY + topOffset,
            (x * tileSize) + tileSize + screenContext.gridStartX + rightOffset,
            ((y - GameView.numTilesSize) * tileSize) + tileSize + screenContext.gridStartY + bottomOffset,
            tileRadius,
            tileRadius,
            type.paint
        )
    }

    private fun fallingOffset(
        x: TileXCoord,
        y: TileYCoord,
        tileSize: Float,
        tick: Int,
        state: GameState
    ): Float {
        return (state as? GameState.TilesFalling)?.let {
            val fallingYOffsetPerTick = tileSize / GameView.ticksPerAction
            val fallingYOffset =
                fallingYOffsetPerTick * ((tick - state.startTick) % GameView.ticksPerAction)

            when (state.lowestPosYOfFallableTiles[x] < y) {
                true -> 0f
                false -> fallingYOffset
            }
        } ?: 0f
    }

    private fun sizeOffset(
        x: TileXCoord,
        y: TileYCoord,
        tileSize: Float,
        tick: Int,
        state: GameState
    ): Float {
        return (state as? GameState.RemovingTiles)?.let {
            val sizeShrinkPerTick = tileSize / 2 / GameView.ticksPerAction

            when (it.newBoardAfterRemove[x][y] == null) {
                true -> tileSize - (sizeShrinkPerTick * ((tick - state.startTick) % GameView.ticksPerAction))
                false -> 0f
            }
        } ?: 0f
    }

    private fun inputMoveOffset(
        x: TileXCoord,
        y: TileYCoord,
        tileSize: Float,
        tick: Int,
        state: GameState
    ): Pair<Float, Float> {
        return (state as? GameState.InputDetected)?.let {
            val isTouchedTile = state.touched.x == x && state.touched.y + GameView.numTilesSize == y
            val isSwitchWithTile =
                state.switchWith.x == x && state.switchWith.y + GameView.numTilesSize == y

            val offsetPerTick = tileSize / GameView.ticksPerAction
            val moveOffset = offsetPerTick * ((tick - state.startTick) % GameView.ticksPerAction)

            when {
                isTouchedTile -> when (state.direction) {
                    OnInputTouchListener.Direction.Up -> Pair(0f, -moveOffset)
                    OnInputTouchListener.Direction.Down -> Pair(0f, moveOffset)
                    OnInputTouchListener.Direction.Left -> Pair(-moveOffset, 0f)
                    OnInputTouchListener.Direction.Right -> Pair(moveOffset, 0f)
                }
                isSwitchWithTile -> when (state.direction) {
                    OnInputTouchListener.Direction.Up -> Pair(0f, moveOffset)
                    OnInputTouchListener.Direction.Down -> Pair(0f, -moveOffset)
                    OnInputTouchListener.Direction.Left -> Pair(moveOffset, 0f)
                    OnInputTouchListener.Direction.Right -> Pair(-moveOffset, 0f)
                }
                else -> Pair(0f, 0f)
            }
        } ?: Pair(0f, 0f)
    }
}