package me.jameshunt.tilegame

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    companion object {
        const val numTilesSize = 8
        const val ticksPerAction = 8
    }

    private var tiles: Array<Array<Tile?>> = getInitialBoard()
    private var currentState: GameState = GameState.TilesFalling
    private var tick = 0

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val visibleTiles = getVisibleTiles()
        val screenContext = getScreenContext(canvas)

        when (currentState) {
            GameState.WaitForInput -> TODO()
            GameState.InputDetected -> TODO()
            GameState.TilesFalling -> {

            }
            GameState.CheckForPoints -> TODO()
            GameState.RemovingTiles -> TODO()
        }


        (0 until numTilesSize).forEach { x ->
            (0 until numTilesSize).forEach { y ->
                visibleTiles[x][y]?.render(x, y, screenContext, tick)
            }
        }
    }

    private fun getScreenContext(canvas: Canvas): ScreenContext {
        val gridSize = min(width, height)

        val gridStartX = when (width == gridSize) {
            true -> 0
            false -> (width - height) / 2
        }

        val gridStartY = when (height == gridSize) {
            true -> 0
            false -> (height - width) / 2
        }

        return ScreenContext(canvas, gridSize, gridStartX, gridStartY)
    }

    private fun getVisibleTiles(): Array<Array<Tile?>> {
        return tiles
            .map { it.copyOfRange(numTilesSize, numTilesSize * 2) }
            .toTypedArray()
    }

    private fun getInitialBoard(): Array<Array<Tile?>> {
        return Array(numTilesSize) { x ->
            Array(numTilesSize * 2) { y ->
                when (y < numTilesSize) {
                    true -> Tile(TileType.values().random()) // TODO: start with no auto solvable
                    false -> null
                }
            }
        }
    }
}

data class ScreenContext(
    val canvas: Canvas,
    val gridSize: Int,
    val gridStartX: Int,
    val gridStartY: Int
)

private class Tile(val type: TileType) {
    fun render(x: Int, y: Int, screenContext: ScreenContext, tick: Int) {
        val tileSize = screenContext.gridSize / GameView.numTilesSize.toFloat()
        val tileRadius = tileSize / 4f

        screenContext.canvas.drawRoundRect(
            (x * tileSize) + screenContext.gridStartX,
            (y * tileSize) + screenContext.gridStartY,
            (x * tileSize) + tileSize + screenContext.gridStartX,
            (y * tileSize) + +tileSize + screenContext.gridStartY,
            tileRadius,
            tileRadius,
            type.paint
        )
    }
}

enum class TileType {
    One,
    Two,
    Three,
    Four,
    Five,
    Six;

    companion object {
        private val paint1 = Paint().apply { color = Color.BLUE }
        private val paint2 = Paint().apply { color = Color.RED }
        private val paint3 = Paint().apply { color = Color.GREEN }
        private val paint4 = Paint().apply { color = Color.CYAN }
        private val paint5 = Paint().apply { color = Color.MAGENTA }
        private val paint6 = Paint().apply { color = Color.YELLOW }
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

enum class GameState {
    WaitForInput,
    InputDetected,
    TilesFalling,
    CheckForPoints,
    RemovingTiles
}