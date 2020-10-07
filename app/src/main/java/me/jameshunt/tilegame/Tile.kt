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
        val paint1 by lazy { Paint().apply { color = Color.BLUE } }
        val paint2 by lazy { Paint().apply { color = Color.RED } }
        val paint3 by lazy { Paint().apply { color = Color.GREEN } }
        val paint4 by lazy { Paint().apply { color = Color.CYAN } }
        val paint5 by lazy { Paint().apply { color = Color.MAGENTA } }
        val paint6 by lazy { Paint().apply { color = Color.YELLOW } }
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
        stepState: GameState
    ) {
        val tileSize = screenContext.gridSize / GameView.numTilesSize.toFloat()
        val tileRadius = tileSize / 4f

        val fallingOffset = fallingOffset(x, y, tileSize, tick, stepState)
        val sizeOffset = sizeOffset(x, y, tileSize, tick, stepState)
        val inputMoveOffset = inputMoveOffset(x, y, tileSize, tick, stepState)

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
        stepState: GameState
    ) {
        check(stepState is GameState.TilesFalling)

        val numTilesSize = GameView.numTilesSize
        val (x: TileXCoord, y: TileYCoord) = when (stepState.fallingFromDirection) {
            GravitySensor.TileFromDirection.Top -> Pair(i, -1)
            GravitySensor.TileFromDirection.Bottom -> Pair(i, numTilesSize)
            GravitySensor.TileFromDirection.Left -> Pair(-1, i)
            GravitySensor.TileFromDirection.Right -> Pair(numTilesSize, (numTilesSize - 1) - i)
        }

        render(x, y, canvas, screenContext, tick, stepState)
    }

    private fun fallingOffset(
        x: TileXCoord,
        y: TileYCoord,
        tileSize: Float,
        tick: Int,
        stepState: GameState
    ): Offset {
        return (stepState as? GameState.TilesFalling)?.let {
            val fallingYOffsetPerTick = tileSize / stepState.tickDuration
            val fallingYOffset =
                fallingYOffsetPerTick * ((tick - stepState.startTick) % stepState.tickDuration)

            when {
                stepState.fallingFromDirection == GravitySensor.TileFromDirection.Top &&
                        stepState.lowestPosYOfFallableTiles[x] >= y -> Offset(0f, fallingYOffset)
                stepState.fallingFromDirection == GravitySensor.TileFromDirection.Left
                        && stepState.lowestPosYOfFallableTiles[y] >= x -> Offset(fallingYOffset, 0f)
                stepState.fallingFromDirection == GravitySensor.TileFromDirection.Right -> {
                    val fixedLowest = stepState.lowestPosYOfFallableTiles.reversed()[y]
                    when (fixedLowest >= (GameView.numTilesSize - 1) - x) {
                        true -> Offset(-fallingYOffset, 0f)
                        false -> Offset(0f, 0f)
                    }
                }
                stepState.fallingFromDirection == GravitySensor.TileFromDirection.Bottom -> {
                    when (stepState.lowestPosYOfFallableTiles[x] >= (GameView.numTilesSize - 1) - y) {
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
        stepState: GameState
    ): Float {
        if (y == -1) return 6f

        return (stepState as? GameState.RemovingTiles)?.let {
            val sizeShrinkPerTick = tileSize / 2 / stepState.tickDuration

            when (it.newBoardAfterRemove[x][y] == null) {
                true -> (tileSize / 14) + tileSize - (sizeShrinkPerTick * ((tick - stepState.startTick) % stepState.tickDuration))
                false -> 6f
            }
        } ?: 6f
    }

    private fun inputMoveOffset(
        x: TileXCoord,
        y: TileYCoord,
        tileSize: Float,
        tick: Int,
        stepState: GameState
    ): Offset {
        return (stepState as? GameState.InputDetected)?.let {
            val input = stepState.input
            val isTouchedTile = input.touched.x == x && input.touched.y == y
            val isSwitchWithTile = input.switchWith.x == x && input.switchWith.y == y

            val offsetPerTick = tileSize / stepState.tickDuration
            val moveOffset = offsetPerTick * ((tick - stepState.startTick) % stepState.tickDuration)

            when {
                isTouchedTile -> when (input.direction) {
                    OnInputTouchListener.Direction.Up -> Offset(0f, -moveOffset)
                    OnInputTouchListener.Direction.Down -> Offset(0f, moveOffset)
                    OnInputTouchListener.Direction.Left -> Offset(-moveOffset, 0f)
                    OnInputTouchListener.Direction.Right -> Offset(moveOffset, 0f)
                }
                isSwitchWithTile -> when (input.direction) {
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

fun State.renderNewlyVisibleTiles(canvas: Canvas, screenContext: ScreenContext, tick: Int) {
    if (stepState !is GameState.TilesFalling) return

    val fixTilesByGravity = tiles.fixTilesByGravity(directionToFallFrom)
    (0 until GameView.numTilesSize).forEach { i ->
        if (null in fixTilesByGravity[i]) {
            invisibleTiles[i]
                .last()
                ?.renderNewlyVisible(i, canvas, screenContext, tick, stepState)
        }
    }
}

fun List<List<Tile?>>.transpose2DTileList(): List<List<Tile?>> {
    val new = this[0].indices
        .map { this.indices.map { null }.toMutableList<Tile?>() }
        .toMutableList()

    this.indices.forEach { x ->
        this[x].indices.forEach { y ->
            new[y][x] = this[x][y]
        }
    }

    return new
}