package me.jameshunt.tilegame

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import me.jameshunt.tilegame.GameState.*
import me.jameshunt.tilegame.OnInputTouchListener.*
import kotlin.math.*

class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    companion object {
        const val numTilesSize = 8
        const val ticksPerAction = 8
        const val numTileTypes = 4 // max of 6 at the moment, add more in TileType
    }

    init {
        setBackgroundColor(Color.GRAY)
        handleTouchEvents()
    }

    private var tiles: List<List<Tile?>> = getInitialBoard()
    private var currentState: GameState = CheckForFallableTiles
    private var tick = 0

    // will be instantiated after view is measured.
    private val screenContext by lazy {
        assert(height != 0 && width != 0)

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

        updateBoard()

        (0 until numTilesSize).forEach { x ->
            (numTilesSize - 1 until numTilesSize * 2).forEach { y ->
                tiles[x][y]?.render(x, y, canvas, screenContext, tick, currentState)
            }
        }

        drawEdgesOfBoard(canvas, screenContext)

        tick += 1

        invalidate()
    }

    private fun updateBoard() {
        when (val state = currentState) {
            is WaitForInput -> {
                // noOp
            }
            is InputDetected -> {
                onAnimationCompleted(state.startTick) {
                    val touchedTile = tiles[state.touched.x][state.touched.y + numTilesSize]
                    val switchWithTile =
                        tiles[state.switchWith.x][state.switchWith.y + numTilesSize]

                    tiles = tiles.map { column ->
                        column.map { tile ->
                            when (tile) {
                                touchedTile -> switchWithTile
                                switchWithTile -> touchedTile
                                else -> tile
                            }
                        }
                    }

                    currentState = CheckForPoints
                }
            }
            is CheckForFallableTiles -> {

                // find lowest fallable posY of each row
                // if any fallable tiles set current state to TilesFalling
                // if no fallable tiles set current state to CheckForPoints

                val lowestPosYOfFallableTiles = tiles.map { tileColumn ->
                    val lowestPosOfNullTile = tileColumn.indexOfLast { it == null }
                    (0 until lowestPosOfNullTile)
                        .map { tileColumn[it] }
                        .indexOfLast { it != null } as TileYCoord
                }
                val doneFalling = lowestPosYOfFallableTiles.foldIndexed(true) { index, acc, posY ->
                    val indexOfBottomTile = (numTilesSize * 2) - 1
                    acc && (posY == indexOfBottomTile || null !in tiles[index])
                }

                currentState = when (doneFalling) {
                    true -> CheckForPoints
                    false -> TilesFalling(tick, lowestPosYOfFallableTiles)
                }
            }
            is TilesFalling -> {
                onAnimationCompleted(state.startTick) {

                    // shift ones that fell to tile spot below
                    // set current state to CheckForFallableTiles

                    fun List<Tile?>.shiftTilesInColumnDown(lowestFallableTile: TileYCoord): List<Tile?> {
                        if (null !in this) return this

                        val newTopTile = listOf(
                            Tile(TileType.values().slice(0 until numTileTypes).random())
                        ) as List<Tile?>

                        val tilesThatFell = newTopTile + this.subList(0, lowestFallableTile + 1)

                        val indexOfBottomTile = (numTilesSize * 2) - 1

                        val tilesThatDidNotFall = (lowestFallableTile + 2..indexOfBottomTile)
                            .map { this[it] }

                        return tilesThatFell + tilesThatDidNotFall
                    }

                    tiles = tiles.mapIndexed { index, arrayOfTiles ->
                        arrayOfTiles.shiftTilesInColumnDown(state.lowestPosYOfFallableTiles[index])
                    }

                    currentState = CheckForFallableTiles
                }
            }
            is CheckForPoints -> {
                fun List<Tile>.checkMatchesInColumnOrTransposedRow(): List<Tile?> {
                    val tilesWithRemoved = this.map { it as Tile? }.toMutableList()

                    var indexOfStartMatching = 0
                    var matchSoFarSize = 0

                    fun checkIfMatch() {
                        if (matchSoFarSize >= 3) {
                            (indexOfStartMatching until indexOfStartMatching + matchSoFarSize).forEach {
                                tilesWithRemoved[it] = null
                            }
                        }
                    }

                    this.forEachIndexed { index, tile ->
                        if (index == indexOfStartMatching) {
                            matchSoFarSize = 1
                            return@forEachIndexed
                        }

                        when (tile.type == this[indexOfStartMatching].type) {
                            true -> matchSoFarSize += 1
                            false -> {
                                checkIfMatch()

                                // tile changed start new stuff
                                indexOfStartMatching = index
                                matchSoFarSize = 1
                            }
                        }
                    }

                    checkIfMatch()

                    return tilesWithRemoved
                }

                val tilesTransposed = tiles.transpose2DTileList()

                val transposedRowsWithRemovedMatches = tilesTransposed
                    .subList(numTilesSize, numTilesSize * 2)
                    .map { it ->
                        it
                            .map { it!! } // no elements in list will be null
                            .checkMatchesInColumnOrTransposedRow()
                    }

                val horizontalMatches =
                    (tilesTransposed.subList(0, numTilesSize) + transposedRowsWithRemovedMatches)
                        .transpose2DTileList()

                val verticalMatches = tiles.map { column ->
                    val visibleTiles = column.subList(numTilesSize, numTilesSize * 2)
                        .map { it!! } // no elements in list will be null
                        .checkMatchesInColumnOrTransposedRow()

                    column.subList(0, numTilesSize) + visibleTiles
                }
                assert(horizontalMatches.size == verticalMatches.size)
                assert(horizontalMatches[0].size == verticalMatches[0].size)


                val mergedMatches = verticalMatches.mapIndexed { x, columns ->
                    columns.mapIndexed { y, verticalMatchTile ->
                        when (val horizontalMatchTile = horizontalMatches[x][y]) {
                            null -> horizontalMatchTile
                            else -> verticalMatchTile
                        }
                    }
                }

                val isBoardSame = mergedMatches.fold(true) { acc, column ->
                    val isColumnSame = null !in column
                    acc && isColumnSame
                }

                currentState = when (isBoardSame) {
                    true -> WaitForInput
                    false -> RemovingTiles(tick, mergedMatches)
                }
            }
            is RemovingTiles -> {
                onAnimationCompleted(state.startTick) {
                    tiles = state.newBoardAfterRemove
                    currentState = CheckForFallableTiles
                }
            }
        }
    }

    private fun handleTouchEvents() {
        setOnTouchListener(OnInputTouchListener { touchInfo ->
            if (currentState == WaitForInput) {
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
                    currentState = InputDetected(touched, switchWith, touchInfo.direction, tick)
                }
            }
        })
    }

    private fun onAnimationCompleted(startTick: Int, action: () -> Unit) {
        val isStarting = tick == startTick
        val isStartingOrEnding = (tick - startTick) % ticksPerAction == 0
        val isEnding = !isStarting && isStartingOrEnding
        if (isEnding) {
            action()
        }
    }

    private fun List<List<Tile?>>.transpose2DTileList(): List<List<Tile?>> {
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

    private fun getInitialBoard(): List<List<Tile?>> {
        return (0 until numTilesSize).map { x ->
            (0 until numTilesSize * 2).map { y ->
                when (y < numTilesSize && (y + x) % 3 == 0) {
                    true -> Tile(TileType.values().slice(0 until numTileTypes).random())
                    false -> null
                }
            }
        }
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

        canvas.drawRect(
            0f,
            0f,
            screenContext.gridStartX.toFloat(),
            height.toFloat(),
            edgeOfBoardColor
        )

        canvas.drawRect(
            screenContext.gridStartX.toFloat() + screenContext.gridSize.toFloat(),
            0f,
            width.toFloat(),
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

typealias TileXCoord = Int
typealias TileYCoord = Int

sealed class GameState {
    object WaitForInput : GameState()
    data class InputDetected(
        val touched: TileCoordinate,
        val switchWith: TileCoordinate,
        val direction: Direction,
        val startTick: Int
    ) : GameState() {
        data class TileCoordinate(
            val x: TileXCoord,
            val y: TileYCoord
        )
    }

    object CheckForFallableTiles : GameState()
    data class TilesFalling(
        val startTick: Int,
        val lowestPosYOfFallableTiles: List<TileYCoord>
    ) : GameState()

    object CheckForPoints : GameState()
    data class RemovingTiles(
        val startTick: Int,
        val newBoardAfterRemove: List<List<Tile?>>
    ) : GameState()
}
