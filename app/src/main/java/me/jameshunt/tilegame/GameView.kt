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

    private var tiles: List<List<Tile?>> = getInitialBoard()
    private var currentState: GameState = GameState.CheckForFallableTiles()
    private var tick = 0

    private val black = Paint().apply { color = Color.BLACK }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        updateBoard()

        val screenContext = getScreenContext(canvas)
        (0 until numTilesSize).forEach { x ->
            (0 until numTilesSize * 2).forEach { y ->
                tiles[x][y]?.render(x, y, screenContext, tick, currentState)
            }
        }

        canvas.drawLine(
            screenContext.gridStartX.toFloat(),
            screenContext.gridStartY.toFloat(),
            screenContext.gridStartX.toFloat() + screenContext.gridSize.toFloat(),
            screenContext.gridStartY.toFloat(),
            black
        )

        canvas.drawLine(
            screenContext.gridStartX.toFloat(),
            screenContext.gridStartY.toFloat()+ screenContext.gridSize.toFloat(),
            screenContext.gridStartX.toFloat() + screenContext.gridSize.toFloat(),
            screenContext.gridStartY.toFloat() + screenContext.gridSize.toFloat(),
            black
        )

        tick += 1

        invalidate()
    }

    private fun updateBoard() {
        when (val state = currentState) {
            is GameState.WaitForInput -> TODO()
            is GameState.InputDetected -> TODO()
            is GameState.CheckForFallableTiles -> {

                // find lowest fallable posY of each row
                // if any fallable tiles set current state to TilesFalling
                // if no fallable tiles set current state to CheckForPoints
                // call updateBoard again

                val lowestPosYOfFallableTiles = tiles.map { tileColumn ->
                    val lowestPosOfNullTile = tileColumn.indexOfLast { it == null }
                    (0 until lowestPosOfNullTile)
                        .map { tileColumn[it] }
                        .indexOfLast { it != null } as PosY
                }
                val doneFalling = lowestPosYOfFallableTiles.foldIndexed(true) { index, acc, posY ->
                    val indexOfBottomTile = (numTilesSize * 2) - 1
                    acc && posY == indexOfBottomTile && !tiles[index].contains(null)
                }

                currentState = when (doneFalling) {
                    true -> GameState.CheckForPoints()
                    false -> GameState.TilesFalling(tick, lowestPosYOfFallableTiles)
                }

                updateBoard()
            }
            is GameState.TilesFalling -> {
                val isStarting = tick == state.startTick
                val isStartingOrEnding = (tick - state.startTick) % ticksPerAction == 0
                val isEnding = !isStarting && isStartingOrEnding
                if (isEnding) {

                    // shift ones that fell to tile spot below
                    // set current state to CheckForFallableTiles
                    // call updateBoard again

                    fun List<Tile?>.shiftTilesInColumnDown(lowestFallableTile: PosY): List<Tile?> {
                        if(!this.contains(null)) return this

                        val newTopTile = listOf(Tile(TileType.values().random())) as List<Tile?>
                        val tilesThatFell = newTopTile + this.subList(0, lowestFallableTile + 1)

                        val indexOfBottomTile = (numTilesSize * 2) - 1

                        val tilesThatDidNotFall = (lowestFallableTile + 2..indexOfBottomTile)
                            .map { this[it] }

                        return tilesThatFell + tilesThatDidNotFall
                    }

                    tiles = tiles.mapIndexed { index, arrayOfTiles ->
                        arrayOfTiles.shiftTilesInColumnDown(state.lowestPosYOfFallableTiles[index])
                    }

                    currentState = GameState.CheckForFallableTiles()
                    updateBoard()
                }
            }
            is GameState.CheckForPoints -> TODO()
            is GameState.RemovingTiles -> TODO()
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

    // TODO: will be used in checkForPoints
//    private fun getVisibleTiles(): List<List<Tile?>> {
//        return tiles.map { it.subList(numTilesSize, numTilesSize * 2) }
//    }

    private fun getInitialBoard(): List<List<Tile?>> {
        return (0 until numTilesSize).map { x ->
            (0 until numTilesSize * 2).map { y ->
//                when (y < 8 && y % 2 == 0) {
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
    fun render(x: Int, y: Int, screenContext: ScreenContext, tick: Int, state: GameState) {
        val tileSize = screenContext.gridSize / GameView.numTilesSize.toFloat()
        val tileRadius = tileSize / 4f

        val fallingYOffset = (state as? GameState.TilesFalling)?.let {
            val fallingYOffsetPerTick = tileSize / GameView.ticksPerAction
            val fallingYOffset = fallingYOffsetPerTick * ((tick - state.startTick) % GameView.ticksPerAction)

            when(state.lowestPosYOfFallableTiles[x] < y) {
                true -> 0f
                false -> fallingYOffset
            }
        } ?: 0f

        screenContext.canvas.drawRoundRect(
            (x * tileSize) + screenContext.gridStartX,
            ((y - GameView.numTilesSize) * tileSize) + screenContext.gridStartY + fallingYOffset,
            (x * tileSize) + tileSize + screenContext.gridStartX,
            ((y - GameView.numTilesSize) * tileSize) + tileSize + screenContext.gridStartY + fallingYOffset,
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

//typealias PosX = Int
typealias PosY = Int

sealed class GameState {
    class WaitForInput : GameState()
    class InputDetected : GameState()
    class CheckForFallableTiles : GameState()
    class TilesFalling(val startTick: Int, val lowestPosYOfFallableTiles: List<PosY>) : GameState()
    class CheckForPoints : GameState()
    class RemovingTiles : GameState()
}