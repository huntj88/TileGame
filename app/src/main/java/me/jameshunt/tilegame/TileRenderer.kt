package me.jameshunt.tilegame

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import me.jameshunt.tilegame.input.FallFromDirection
import me.jameshunt.tilegame.input.MoveDirection
import kotlin.random.Random

fun State.renderTileGrid(
    tileRenderer: TileRenderer,
    canvas: Canvas,
    screenContext: ScreenContext,
) {
    this.renderNewlyVisibleTiles(tileRenderer, canvas, screenContext)

    val gridSize = config.gridSize
    (0 until gridSize).forEach { x ->
        (0 until gridSize).forEach { y ->
            tiles.getOrNull(x)?.getOrNull(y)?.type?.let { tileType ->
                tileRenderer.render(
                    type = tileType,
                    x = x,
                    y = y,
                    canvas = canvas,
                    screenContext = screenContext,
                    tick = tick,
                    step = step,
                    gridSize = gridSize
                )
            }
        }
    }
}

private fun State.renderNewlyVisibleTiles(
    tileRenderer: TileRenderer,
    canvas: Canvas,
    screenContext: ScreenContext
) {
    if (step !is Step.TilesFalling) return

    val fixTilesByGravity = tiles.alignTilesByFallDirection(directionToFallFrom)
    (0 until config.gridSize).forEach { i ->
        if (null in fixTilesByGravity[i]) {
            invisibleTiles[i]?.type?.let { tileType ->
                tileRenderer.renderNewlyVisible(
                    type = tileType,
                    i = i,
                    canvas = canvas,
                    screenContext = screenContext,
                    tick = tick,
                    step = step,
                    gridSize = config.gridSize
                )
            }
        }
    }
}

class TileRenderer {

    private companion object {
        val paints: List<Paint> = listOf(
            Paint().apply { color = Color.BLUE },
            Paint().apply { color = Color.RED },
            Paint().apply { color = Color.GREEN },
            Paint().apply { color = Color.CYAN },
            Paint().apply { color = Color.MAGENTA },
            Paint().apply { color = Color.YELLOW }
        ).sortedBy { Random.nextInt() }

        fun TileType.paint(): Paint {
            return paints.getOrNull(this.ordinal) ?: TODO("Color not set for: $this")
        }
    }

    fun render(
        type: TileType,
        x: TileXCoordinate,
        y: TileYCoordinate,
        canvas: Canvas,
        screenContext: ScreenContext,
        tick: Int,
        step: Step,
        gridSize: Int
    ) {
        val tileSize = screenContext.gridSizePixels / gridSize.toFloat()
        val tileRadius = tileSize / 4f

        val fallingOffset = fallingOffset(x, y, tileSize, tick, step, gridSize)
        val sizeOffset = sizeOffset(x, y, tileSize, tick, step)
        val inputMoveOffset = inputMoveOffset(x, y, tileSize, tick, step)

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
            type.paint()
        )
    }

    fun renderNewlyVisible(
        type: TileType,
        i: Int,
        canvas: Canvas,
        screenContext: ScreenContext,
        tick: Int,
        step: Step,
        gridSize: Int
    ) {
        check(step is Step.TilesFalling)

        val (x: TileXCoordinate, y: TileYCoordinate) = when (step.fallingFromDirection) {
            FallFromDirection.Top -> Pair(i, -1)
            FallFromDirection.Bottom -> Pair(i, gridSize)
            FallFromDirection.Left -> Pair(-1, i)
            FallFromDirection.Right -> Pair(gridSize, (gridSize - 1) - i)
        }

        render(type, x, y, canvas, screenContext, tick, step, gridSize)
    }

    private fun fallingOffset(
        x: TileXCoordinate,
        y: TileYCoordinate,
        tileSize: Float,
        tick: Int,
        step: Step,
        gridSize: Int
    ): Offset = (step as? Step.TilesFalling)?.let {
        val fallingYOffsetPerTick = tileSize / step.tickDuration
        val fallingYOffset =
            fallingYOffsetPerTick * ((tick - step.startTick) % step.tickDuration)

        when {
            step.fallingFromDirection == FallFromDirection.Top &&
                    step.lowestPosYOfFallableTiles[x] >= y -> Offset(0f, fallingYOffset)
            step.fallingFromDirection == FallFromDirection.Left &&
                    step.lowestPosYOfFallableTiles[y] >= x -> Offset(fallingYOffset, 0f)
            step.fallingFromDirection == FallFromDirection.Right -> {
                val fixedLowest = step.lowestPosYOfFallableTiles.reversed()[y]
                when (fixedLowest >= (gridSize - 1) - x) {
                    true -> Offset(-fallingYOffset, 0f)
                    false -> Offset(0f, 0f)
                }
            }
            step.fallingFromDirection == FallFromDirection.Bottom -> {
                when (step.lowestPosYOfFallableTiles[x] >= (gridSize - 1) - y) {
                    true -> Offset(0f, -fallingYOffset)
                    false -> Offset(0f, 0f)
                }
            }
            else -> Offset(0f, 0f)
        }
    } ?: Offset(0f, 0f)

    private fun sizeOffset(
        x: TileXCoordinate,
        y: TileYCoordinate,
        tileSize: Float,
        tick: Int,
        step: Step
    ): Float {
        if (y == -1) return 6f

        return (step as? Step.RemovingTiles)?.let {
            val sizeShrinkPerTick = tileSize / 2 / step.tickDuration

            when (it.newBoardAfterRemove.getOrNull(x)?.getOrNull(y) == null) {
                true -> (tileSize / 14) + tileSize - (sizeShrinkPerTick * ((tick - step.startTick) % step.tickDuration))
                false -> 6f
            }
        } ?: 6f
    }

    private fun inputMoveOffset(
        x: TileXCoordinate,
        y: TileYCoordinate,
        tileSize: Float,
        tick: Int,
        step: Step
    ): Offset {
        return (step as? Step.InputDetected)?.let {
            val input = step.touchInput
            val isTouchedTile = input.touched.x == x && input.touched.y == y
            val isSwitchWithTile = input.switchWith.x == x && input.switchWith.y == y

            val offsetPerTick = tileSize / step.tickDuration
            val moveOffset = offsetPerTick * ((tick - step.startTick) % step.tickDuration)

            when {
                isTouchedTile -> when (input.moveDirection) {
                    MoveDirection.Up -> Offset(0f, -moveOffset)
                    MoveDirection.Down -> Offset(0f, moveOffset)
                    MoveDirection.Left -> Offset(-moveOffset, 0f)
                    MoveDirection.Right -> Offset(moveOffset, 0f)
                }
                isSwitchWithTile -> when (input.moveDirection) {
                    MoveDirection.Up -> Offset(0f, moveOffset)
                    MoveDirection.Down -> Offset(0f, -moveOffset)
                    MoveDirection.Left -> Offset(moveOffset, 0f)
                    MoveDirection.Right -> Offset(-moveOffset, 0f)
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
