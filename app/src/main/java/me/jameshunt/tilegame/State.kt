package me.jameshunt.tilegame

import me.jameshunt.tilegame.input.ExternalInput
import me.jameshunt.tilegame.input.FallFromDirection
import me.jameshunt.tilegame.input.TouchInput
import java.util.concurrent.Executors

class StateMachine(
    private val externalInput: ExternalInput,
    private val onNewStateReadyForRender: () -> Unit,
    private val onError: (e: Throwable) -> Unit
) {
    private var state: State = getInitialState()

    init {
        // By putting all of the magic in a background thread we can control
        // the rate at which the state machine progresses
        // UI thread also needs to do less work
        Executors.newSingleThreadExecutor().submit {
            // super basic state loop
            while (true) {
                if (state.tick % externalInput.config.sleepEveryXTicks == 0) {
                    Thread.sleep(externalInput.config.milliToSleepFor)
                }

                val lastState = state
                val nextState = try {
                    lastState
                        .loadConfigChange()
                        .getNextState()
                } catch (t: Throwable) {
                    onError(t)
                    return@submit
                }

                synchronized(this) {
                    state = nextState
                }

                if (lastState.step != Step.WaitForInput || nextState.step != Step.WaitForInput) {
                    onNewStateReadyForRender()
                }
            }
        }
    }

    fun getCurrentState(): State = synchronized(this) { state }

    private fun State.loadConfigChange(): State {
        if (this.step is Step.CheckForPoints) {
            // defer loading config until after match evaluation
            return this
        }

        val currentConfig = externalInput.config

        fun Step.TilesFalling.handleTilesFallingConfigChange(): Step {
            // falling state data could be stale due to different board size
            return when (currentConfig.gridSize == this.lowestPosYOfFallableTiles.size) {
                true -> this
                false -> Step.CheckForFallableTiles
            }
        }

        return this.copy(
            config = currentConfig,
            tiles = this.tiles.shrinkOrGrow(currentConfig.gridSize),
            invisibleTiles = this.invisibleTiles.shrinkOrGrowFilled(
                currentConfig.gridSize,
                currentConfig.numTileTypes
            ),
            step = when (step) {
                is Step.TilesFalling -> step.handleTilesFallingConfigChange()
                else -> step
            }
        )
    }

    private fun getInitialState(): State {
        val gridSize = externalInput.config.gridSize
        val numTileTypes = externalInput.config.numTileTypes
        return State(
            tiles = getInitialBoard(gridSize, numTileTypes),
            invisibleTiles = getInitialBoard(gridSize, numTileTypes),
            step = Step.CheckForFallableTiles,
            externalInput = externalInput,
            config = externalInput.config
        )
    }
}

typealias TileXCoordinate = Int
typealias TileYCoordinate = Int

sealed class Step {
    object WaitForInput : Step()
    data class InputDetected(
        val touchInput: TouchInput,
        val startTick: Int,
        val switchBackIfNoPoints: Boolean = true
    ) : Step()

    object CheckForFallableTiles : Step()
    data class TilesFalling(
        val startTick: Int,
        val lowestPosYOfFallableTiles: List<TileYCoordinate>,
        val fallingFromDirection: FallFromDirection
    ) : Step()

    data class CheckForPoints(val previousInput: InputDetected?) : Step()
    data class RemovingTiles(
        val startTick: Int,
        val newBoardAfterRemove: List<List<Tile?>>
    ) : Step()

    val tickDuration: Int
        get() = when (this) {
            is InputDetected -> 8
            is TilesFalling -> 8
            is RemovingTiles -> 24
            else -> TODO("No Animation setup for: $this")
        }
}

/**
 * State is an immutable object representing the current state of the board.
 *
 * When the board is updated a new State is generated from the old one,
 * but one tick further ahead in time
 */
data class State(
    val tiles: List<List<Tile?>>,
    val invisibleTiles: List<List<Tile?>>,
    val step: Step,
    val tick: Int = 0,
    val config: GameView.Config,
    private val externalInput: ExternalInput,
) {

    val directionToFallFrom: FallFromDirection = externalInput.directionToFallFrom

    fun getNextState(): State {
        return this
            .copy(tick = tick + 1)
            .stepThroughStateMachine()
    }

    /**
     * Generates and returns the next state or the same state if animation in progress
     */
    private fun stepThroughStateMachine(): State {
        return when (val step = step) {
            is Step.WaitForInput -> handleWaitForInput()
            is Step.InputDetected -> step.onAnimationCompleted(
                startTick = step.startTick,
                generateNextState = { handleInputDetected(step) }
            )
            is Step.CheckForFallableTiles -> handleCheckingForFallableTiles()
            is Step.TilesFalling -> step.onAnimationCompleted(
                startTick = step.startTick,
                generateNextState = { handleTilesFalling(step) }
            )
            is Step.CheckForPoints -> handleCheckForPoints(step)
            is Step.RemovingTiles -> step.onAnimationCompleted(
                startTick = step.startTick,
                generateNextState = {
                    this.copy(
                        tiles = step.newBoardAfterRemove,
                        step = Step.CheckForFallableTiles
                    )
                })
        }
    }

    private fun handleWaitForInput(): State {
        return when (val input = externalInput.lastTouchInput) {
            null -> this // state unchanged, keep waiting on input
            else -> this
                .apply { externalInput.lastTouchInput = null }
                .copy(step = Step.InputDetected(input, tick))
        }
    }

    private fun handleInputDetected(step: Step.InputDetected): State {
        val input = step.touchInput

        val touchedTile = tiles.getOrNull(input.touched.x)?.getOrNull(input.touched.y)
        val switchWithTile = tiles.getOrNull(input.switchWith.x)?.getOrNull(input.switchWith.y)

        return this.copy(
            tiles = tiles.map { column ->
                column.map { tile ->
                    when {
                        tile === touchedTile -> switchWithTile
                        tile === switchWithTile -> touchedTile
                        else -> tile
                    }
                }
            },
            step = when (step.switchBackIfNoPoints) {
                true -> Step.CheckForPoints(step)
                false -> Step.WaitForInput
            }
        )
    }

    private fun handleCheckingForFallableTiles(): State {
        // find lowest fallable posY of each row
        // if any fallable tiles set current state to TilesFalling
        // if no fallable tiles set current state to CheckForPoints

        check(tiles.size == config.gridSize)
        check(tiles.first().size == config.gridSize)
        val gravityFixedTiles = tiles.fixTilesByGravity(directionToFallFrom)

        val lowestPosYOfFallableTiles: List<TileYCoordinate> = gravityFixedTiles.map { tileColumn ->
            val lowestPosOfNullTile = tileColumn.indexOfLast { it == null }
            (0 until lowestPosOfNullTile)
                .map { tileColumn[it] }
                .indexOfLast { it != null }
        }

        val doneFalling = lowestPosYOfFallableTiles.foldIndexed(true) { index, acc, posY ->
            val indexOfBottomTile = config.gridSize - 1
            acc && (posY == indexOfBottomTile || null !in gravityFixedTiles[index])
        }

        val nextStep = when (doneFalling) {
            true -> Step.CheckForPoints(null)
            false -> Step.TilesFalling(
                startTick = tick,
                lowestPosYOfFallableTiles = lowestPosYOfFallableTiles,
                fallingFromDirection = directionToFallFrom
            )
        }

        return this.copy(step = nextStep)
    }

    private fun handleTilesFalling(step: Step.TilesFalling): State {
        // shift ones that fell to tile spot below
        // set current state to CheckForFallableTiles

        // for joined (numTilesSize x (numTilesSize * 2)) grid
        fun List<Tile?>.shiftTilesInColumnDown(lowestFallableTile: TileYCoordinate): List<Tile?> {
            // represents a column of visible and invisible tiles combined,
            // with invisible ones being used to supply new tiles
            check(this.size == config.gridSize * 2)

            if (null !in this) return this

            val newTopTile = listOf(newRandomTile(config.numTileTypes)) as List<Tile?>

            val tilesThatFell = newTopTile + this.subList(0, lowestFallableTile + 1)

            val indexOfBottomTile = (config.gridSize * 2) - 1

            val tilesThatDidNotFall = (lowestFallableTile + 2..indexOfBottomTile).map { this[it] }

            return tilesThatFell + tilesThatDidNotFall
        }

        val joinedGridShift = tiles.fixTilesByGravity(directionToFallFrom)
            .mapIndexed { index, list -> invisibleTiles[index] + list }
            .mapIndexed { index, arrayOfTiles ->
                val lowestFallableTile = step.lowestPosYOfFallableTiles[index] + config.gridSize
                arrayOfTiles.shiftTilesInColumnDown(lowestFallableTile)
            }

        check(config.gridSize == joinedGridShift.size)
        check(config.gridSize * 2 == joinedGridShift.first().size)

        return this.copy(
            tiles = joinedGridShift
                .map { it.subList(config.gridSize, config.gridSize * 2) }
                .fixTilesByGravity(directionToFallFrom),
            invisibleTiles = joinedGridShift.map { it.subList(0, config.gridSize) },
            step = Step.CheckForFallableTiles
        )

    }

    private fun handleCheckForPoints(step: Step.CheckForPoints): State {
        check(null !in tiles.flatten())

        fun List<Tile>.checkMatchesInColumnOrTransposedRow(): List<Tile?> {
            val tilesWithRemoved = this.toMutableList<Tile?>()

            var indexOfStartMatching = 0
            var matchSoFarSize = 0

            fun checkIfMatch() {
                if (matchSoFarSize >= config.numToMatch) {
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

        val horizontalMatches = tiles
            .transpose2DTileList()
            .map { row ->
                row
                    .map { it!! } // no elements in list will be null
                    .checkMatchesInColumnOrTransposedRow()
            }
            .transpose2DTileList()

        val verticalMatches = tiles.map { column ->
            column
                .map { it!! } // no elements in list will be null
                .checkMatchesInColumnOrTransposedRow()
        }
        check(horizontalMatches.size == verticalMatches.size)
        check(horizontalMatches[0].size == verticalMatches[0].size)

        val mergedMatches = verticalMatches.mapIndexed { x, columns ->
            columns.mapIndexed { y, verticalMatchTile ->
                when (val horizontalMatchTile = horizontalMatches[x][y]) {
                    null -> horizontalMatchTile
                    else -> verticalMatchTile
                }
            }
        }

        val isBoardSame = mergedMatches.fold(true) { acc, column ->
            fun isColumnSame(): Boolean = null !in column
            acc && isColumnSame()
        }

        val nextStep = when (isBoardSame) {
            true -> when (step.previousInput == null) {
                true -> Step.WaitForInput
                false -> Step.InputDetected(
                    touchInput = TouchInput(
                        touched = step.previousInput.touchInput.switchWith,
                        switchWith = step.previousInput.touchInput.touched,
                        moveDirection = step.previousInput.touchInput.moveDirection.opposite()
                    ),
                    startTick = tick,
                    switchBackIfNoPoints = false
                )
            }
            false -> Step.RemovingTiles(tick, mergedMatches)
        }

        return this.copy(step = nextStep)
    }

    private fun Step.onAnimationCompleted(startTick: Int, generateNextState: () -> State): State {
        val isStarting = tick == startTick
        val isStartingOrEnding = (tick - startTick) % tickDuration == 0
        val isEnding = !isStarting && isStartingOrEnding
        return when (isEnding) {
            true -> generateNextState()
            false -> this@State // if in middle of animation then state not changing, return current state
        }
    }
}

fun List<List<Tile?>>.fixTilesByGravity(directionToFallFrom: FallFromDirection): List<List<Tile?>> {
    // all logic was originally assumed to have a falling direction of down from the top
    // this fixes the 2d tile grid so that the logic works in other directions too

    fun List<List<Tile?>>.reverseGrid(): List<List<Tile?>> =
        this.map { it.reversed() }.reversed()

    return when (directionToFallFrom) {
        FallFromDirection.Top -> this
        FallFromDirection.Bottom -> this.map { it.reversed() }
        FallFromDirection.Left -> this.transpose2DTileList()
        FallFromDirection.Right -> this.transpose2DTileList().reverseGrid()
    }
}