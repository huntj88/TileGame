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

    private var state = State(
        invisibleTiles = getInitialBoard(numTilesSize),
        tiles = getInitialBoard(numTilesSize),
        stepState = CheckForFallableTiles
    )

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

        state = state.updateBoard { nextState, tick ->
            nextState.renderNewlyVisibleTiles(canvas, screenContext, tick)

            (0 until numTilesSize).forEach { x ->
                (0 until numTilesSize).forEach { y ->
                    nextState.tiles[x][y]?.render(
                        x = x,
                        y = y,
                        canvas = canvas,
                        screenContext = screenContext,
                        tick = tick,
                        state = nextState.stepState
                    )
                }
            }
        }

        drawEdgesOfBoard(canvas, screenContext)

        invalidate()
    }

    fun setDirectionToFallFrom(directionToFallFrom: GravitySensor.TileFromDirection) {
        state.setDirectionToFallFrom(directionToFallFrom)
    }

    private fun handleTouchEvents() {
        setOnTouchListener(OnInputTouchListener { touchInfo ->
            if (state.stepState == WaitForInput) {
                val xTouchInGrid = touchInfo.xTouch - screenContext.gridStartX
                val xTile = floor(xTouchInGrid / screenContext.gridSize * numTilesSize).toInt()

                val yTouchInGrid = touchInfo.yTouch - screenContext.gridStartY
                val yTile = floor(yTouchInGrid / screenContext.gridSize * numTilesSize).toInt()

                val touched = Input.TileCoordinate(xTile, yTile)
                val switchWith = when (touchInfo.direction) {
                    Direction.Up -> Input.TileCoordinate(xTile, yTile - 1)
                    Direction.Down -> Input.TileCoordinate(xTile, yTile + 1)
                    Direction.Left -> Input.TileCoordinate(xTile - 1, yTile)
                    Direction.Right -> Input.TileCoordinate(xTile + 1, yTile)
                }

                val validXMove = switchWith.x in (0 until numTilesSize)
                val validYMove = switchWith.y in (0 until numTilesSize)

                if (validXMove && validYMove) {
                    state.lastInput = Input(touched, switchWith, touchInfo.direction)
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
}

fun getInitialBoard(numTilesSize: Int): List<List<Tile?>> {
    return (0 until numTilesSize).map { x ->
        (0 until numTilesSize).map { y ->
            //when(true) {
            when ((y + x) % 3 == 0) {
                true -> Tile(TileType.values().slice(0 until GameView.numTileTypes).random())
                false -> null
            }
        }
    }
}

data class ScreenContext(
    val gridSize: Int,
    val gridStartX: Int,
    val gridStartY: Int
)
