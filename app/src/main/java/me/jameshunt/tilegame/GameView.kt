package me.jameshunt.tilegame

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import me.jameshunt.tilegame.GameState.*
import me.jameshunt.tilegame.OnInputTouchListener.Direction
import kotlin.math.floor
import kotlin.math.min


class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    companion object {
        const val numTilesSize = 8
        const val numTileTypes = 3 // max of 6 at the moment, add more in TileType
    }

    init {
        setBackgroundColor(Color.GRAY)
        handleTouchEvents()
    }

    private val state = State()

    // will be instantiated after view is measured.
    private val screenContext by lazy {
        check(height != 0 && width != 0)

        val gridSize = min(width, height)

        val gridStartX = when (width == gridSize) {
            true -> 0
            false -> (width - height) / 2
        }

        val gridStartY = when (height == gridSize) {
            true -> 0
            false -> (height - width) / 2
        }

        ScreenContext(gridSize, gridStartX, gridStartY)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        state.updateBoard()

        renderNewlyVisibleTiles(canvas)

        (0 until numTilesSize).forEach { x ->
            (0 until numTilesSize).forEach { y ->
                state.tiles[x][y]?.render(x, y, canvas, screenContext, state.tick, state.currentState)
            }
        }

        drawEdgesOfBoard(canvas, screenContext)

        state.tick += 1

        invalidate()
    }

    fun setDirectionToFallFrom(directionToFallFrom: GravitySensor.TileFromDirection) {
        state.directionToFallFrom = directionToFallFrom
    }

    private fun handleTouchEvents() {
        setOnTouchListener(OnInputTouchListener { touchInfo ->
            if (state.currentState == WaitForInput) {
                val xTouchInGrid = touchInfo.xTouch - screenContext.gridStartX
                val xTile = floor(xTouchInGrid / screenContext.gridSize * numTilesSize).toInt()

                val yTouchInGrid = touchInfo.yTouch - screenContext.gridStartY
                val yTile = floor(yTouchInGrid / screenContext.gridSize * numTilesSize).toInt()

                val touched = InputDetected.TileCoordinate(xTile, yTile)
                val switchWith = when (touchInfo.direction) {
                    Direction.Up -> InputDetected.TileCoordinate(xTile, yTile - 1)
                    Direction.Down -> InputDetected.TileCoordinate(xTile, yTile + 1)
                    Direction.Left -> InputDetected.TileCoordinate(xTile - 1, yTile)
                    Direction.Right -> InputDetected.TileCoordinate(xTile + 1, yTile)
                }

                val validXMove = switchWith.x in (0 until numTilesSize)
                val validYMove = switchWith.y in (0 until numTilesSize)

                if (validXMove && validYMove) {
                    state.currentState = InputDetected(touched, switchWith, touchInfo.direction, state.tick)
                }
            }
        })
    }

    private val edgeOfBoardColor = Paint().apply { color = Color.DKGRAY }
    private fun drawEdgesOfBoard(canvas: Canvas, screenContext: ScreenContext) {
        canvas.drawRect(
            screenContext.gridStartX.toFloat(),
            0f,
            screenContext.gridStartX.toFloat() + screenContext.gridSize.toFloat(),
            screenContext.gridStartY.toFloat(),
            edgeOfBoardColor
        )

        canvas.drawRect(
            screenContext.gridStartX.toFloat(),
            screenContext.gridStartY.toFloat() + screenContext.gridSize.toFloat(),
            screenContext.gridStartX.toFloat() + screenContext.gridSize.toFloat(),
            height.toFloat(),
            edgeOfBoardColor
        )
    }

    private fun renderNewlyVisibleTiles(canvas: Canvas) {
        if (state.currentState !is TilesFalling) return

        val fixTilesByGravity = state.tiles.fixTilesByGravity(state.directionToFallFrom)
        (0 until numTilesSize).forEach { i ->
            if (null in fixTilesByGravity[i]) {
                state.invisibleTiles[i]
                    .last()
                    ?.renderNewlyVisible(i, canvas, screenContext, state.tick, state.currentState)
            }
        }
    }
}

data class ScreenContext(
    val gridSize: Int,
    val gridStartX: Int,
    val gridStartY: Int
)
