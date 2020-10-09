package me.jameshunt.tilegame

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import me.jameshunt.tilegame.Step.*
import me.jameshunt.tilegame.input.*
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
        const val numToMatch = 3
        const val milliBetweenUpdate = 16L
    }

    init {
        setBackgroundColor(Color.GRAY)
        handleTouchEvents()
    }

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

    private val tileRenderer = TileRenderer()
    private val externalInput = ExternalInput()

    private val stateManager = StateManager(
        numTilesSize = numTilesSize,
        externalInput = externalInput,
        onRenderNewState = { invalidate() }
    )

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        stateManager.getCurrentState().let { state ->
            state.renderTileGrid(tileRenderer, canvas, screenContext, state.tick)
        }

        drawEdgesOfBoard(canvas, screenContext)
    }

    fun setDirectionToFallFrom(direction: FallFromDirection) {
        externalInput.setDirectionToFallFrom(stateManager.getCurrentState().step, direction)
    }

    private fun handleTouchEvents() {
        setOnTouchListener(OnInputTouchListener { touchInfo ->
            if (stateManager.getCurrentState().step != WaitForInput) return@OnInputTouchListener

            val xTouchInGrid = touchInfo.xTouch - screenContext.gridStartX
            val xTile = floor(xTouchInGrid / screenContext.gridSize * numTilesSize).toInt()

            val yTouchInGrid = touchInfo.yTouch - screenContext.gridStartY
            val yTile = floor(yTouchInGrid / screenContext.gridSize * numTilesSize).toInt()

            val touched = TouchInput.TileCoordinate(xTile, yTile)
            val switchWith = when (touchInfo.moveDirection) {
                MoveDirection.Up -> TouchInput.TileCoordinate(xTile, yTile - 1)
                MoveDirection.Down -> TouchInput.TileCoordinate(xTile, yTile + 1)
                MoveDirection.Left -> TouchInput.TileCoordinate(xTile - 1, yTile)
                MoveDirection.Right -> TouchInput.TileCoordinate(xTile + 1, yTile)
            }

            val validXMove = switchWith.x in (0 until numTilesSize)
            val validYMove = switchWith.y in (0 until numTilesSize)

            if (validXMove && validYMove) {
                externalInput.lastTouchInput = TouchInput(touched, switchWith, touchInfo.moveDirection)
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

data class ScreenContext(
    val gridSize: Int,
    val gridStartX: Int,
    val gridStartY: Int
)
