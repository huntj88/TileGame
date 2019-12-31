package me.jameshunt.tilegame

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.absoluteValue
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow

class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    companion object {
        const val numTilesSize = 8
        const val ticksPerAction = 8
    }

    init {
        setBackgroundColor(Color.GRAY)

        setOnTouchListener(OnInputTouchListener {
            if (currentState == GameState.WaitForInput) {
                currentState = GameState.InputDetected(it, tick)
            }
        })
    }

    private var tiles: List<List<Tile?>> = getInitialBoard()
    private var currentState: GameState = GameState.CheckForFallableTiles
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
            (0 until numTilesSize * 2).forEach { y ->
                tiles[x][y]?.render(x, y, canvas, screenContext, tick, currentState)
            }
        }

        drawEdgesOfBoard(canvas, screenContext)

        tick += 1

        invalidate()
    }

    private fun updateBoard() {
        when (val state = currentState) {
            is GameState.WaitForInput -> {
                // noOp
            }
            is GameState.InputDetected -> {
                val xTouchInGrid = state.touchInfo.xTouch - screenContext.gridStartX
                val xTile = floor(xTouchInGrid / screenContext.gridSize * numTilesSize)

                val yTouchInGrid = state.touchInfo.yTouch - screenContext.gridStartY
                val yTile = floor(yTouchInGrid / screenContext.gridSize * numTilesSize)

                println("xTile: $xTile, yTile: $yTile")
                currentState = GameState.WaitForInput
            }
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
                    acc && (posY == indexOfBottomTile || !tiles[index].contains(null))
                }

                currentState = when (doneFalling) {
                    true -> GameState.CheckForPoints
                    false -> GameState.TilesFalling(tick, lowestPosYOfFallableTiles)
                }
            }
            is GameState.TilesFalling -> {
                onAnimationCompleted(state.startTick) {

                    // shift ones that fell to tile spot below
                    // set current state to CheckForFallableTiles
                    // call updateBoard again

                    fun List<Tile?>.shiftTilesInColumnDown(lowestFallableTile: PosY): List<Tile?> {
                        if (!this.contains(null)) return this

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

                    currentState = GameState.CheckForFallableTiles
                }
            }
            is GameState.CheckForPoints -> {
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
                    val isColumnSame = column.fold(true) { accColumn, tile ->
                        accColumn && tile != null
                    }
                    acc && isColumnSame
                }

                currentState = when (isBoardSame) {
                    true -> GameState.WaitForInput
                    false -> GameState.RemovingTiles(tick, mergedMatches)
                }
            }
            is GameState.RemovingTiles -> {
                onAnimationCompleted(state.startTick) {
                    tiles = state.newBoardAfterRemove
                    currentState = GameState.CheckForFallableTiles
                }
            }
        }
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
                when (y < 8 && (y + x) % 3 == 0) {
//                when (y < numTilesSize) {
                    true -> Tile(TileType.values().random()) // TODO: start with no auto solvable
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

private class Tile(val type: TileType) {
    fun render(x: Int, y: Int, canvas: Canvas, screenContext: ScreenContext, tick: Int, state: GameState) {
        val tileSize = screenContext.gridSize / GameView.numTilesSize.toFloat()
        val tileRadius = tileSize / 4f

        val fallingYOffset = (state as? GameState.TilesFalling)?.let {
            val fallingYOffsetPerTick = tileSize / GameView.ticksPerAction
            val fallingYOffset =
                fallingYOffsetPerTick * ((tick - state.startTick) % GameView.ticksPerAction)

            when (state.lowestPosYOfFallableTiles[x] < y) {
                true -> 0f
                false -> fallingYOffset
            }
        } ?: 0f

        val sizeOffset = (state as? GameState.RemovingTiles)?.let {
            val sizeShrinkPerTick = tileSize / 2 / GameView.ticksPerAction

            when (it.newBoardAfterRemove[x][y] == null) {
                true -> tileSize - (sizeShrinkPerTick * ((tick - state.startTick) % GameView.ticksPerAction))
                false -> 0f
            }
        } ?: 0f

        canvas.drawRoundRect(
            (x * tileSize) + screenContext.gridStartX + sizeOffset,
            ((y - GameView.numTilesSize) * tileSize) + screenContext.gridStartY + fallingYOffset + sizeOffset,
            (x * tileSize) + tileSize + screenContext.gridStartX - sizeOffset,
            ((y - GameView.numTilesSize) * tileSize) + tileSize + screenContext.gridStartY + fallingYOffset - sizeOffset,
            tileRadius,
            tileRadius,
            type.paint
        )
    }
}

private enum class TileType {
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

//typealias PosX = Int
typealias PosY = Int

private sealed class GameState {
    object WaitForInput : GameState()
    data class InputDetected(
        val touchInfo: OnInputTouchListener.TouchInfo,
        val tick: Int
    ) : GameState()

    object CheckForFallableTiles : GameState()
    data class TilesFalling(
        val startTick: Int,
        val lowestPosYOfFallableTiles: List<PosY>
    ) : GameState()

    object CheckForPoints : GameState()
    data class RemovingTiles(
        val startTick: Int,
        val newBoardAfterRemove: List<List<Tile?>>
    ) : GameState()
}

private class OnInputTouchListener(private val inputDetectedHandler: (TouchInfo) -> Unit) :
    View.OnTouchListener {

    private var startX: Float = 0f
    private var startY: Float = 0f

    override fun onTouch(v: View, event: MotionEvent): Boolean {

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
            }
            MotionEvent.ACTION_UP -> {
                val xDiff = event.x - startX
                val yDiff = event.y - startY

                // pythagorean to check min distance moved
                if(xDiff.pow(2) + yDiff.pow(2) > (min(v.width, v.height) / 6f).pow(2)) {
                    val direction = Direction.from(xDiff, yDiff)
                    inputDetectedHandler(TouchInfo(startX, startY, direction))
                }
            }
        }

        return true
    }

    data class TouchInfo(
        val xTouch: Float,
        val yTouch: Float,
        val direction: Direction
    )

    enum class Direction {
        Up,
        Down,
        Left,
        Right;

        companion object {
            fun from(xDiff: Float, yDiff: Float): Direction {
                return when (xDiff.absoluteValue > yDiff.absoluteValue) {
                    true -> if (xDiff > 0) Right else Left
                    false -> if (yDiff > 0) Down else Up
                }
            }
        }
    }
}