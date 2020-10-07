package me.jameshunt.tilegame

import me.jameshunt.tilegame.input.ExternalInput
import me.jameshunt.tilegame.input.FallFromDirection
import me.jameshunt.tilegame.input.TouchInput

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
    private val externalInput: ExternalInput,
    private val tick: Int = 0
) {
    private val numTilesSize = tiles.size

    val directionToFallFrom: FallFromDirection
        get() = externalInput.directionToFallFrom

    fun updateBoard(render: (nextState: State, tick: Int) -> Unit): State {
        val nextState = stepThroughStateMachine().copy(tick = tick + 1)
        render(nextState, tick)
        return nextState
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

    private fun handleInputDetected(stepState: Step.InputDetected): State {
        val input = stepState.touchInput
        val touchedTile = tiles[input.touched.x][input.touched.y]
        val switchWithTile = tiles[input.switchWith.x][input.switchWith.y]

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
            step = when (stepState.switchBackIfNoPoints) {
                true -> Step.CheckForPoints(stepState)
                false -> Step.WaitForInput
            }
        )
    }

    private fun handleCheckingForFallableTiles(): State {
        // find lowest fallable posY of each row
        // if any fallable tiles set current state to TilesFalling
        // if no fallable tiles set current state to CheckForPoints

        val gravityFixedTiles = tiles.fixTilesByGravity(directionToFallFrom)

        val lowestPosYOfFallableTiles: List<TileYCoordinate> = gravityFixedTiles.map { tileColumn ->
            val lowestPosOfNullTile = tileColumn.indexOfLast { it == null }
            (0 until lowestPosOfNullTile)
                .map { tileColumn[it] }
                .indexOfLast { it != null }
        }

        val doneFalling = lowestPosYOfFallableTiles.foldIndexed(true) { index, acc, posY ->
            val indexOfBottomTile = numTilesSize - 1
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

    private fun handleTilesFalling(stepState: Step.TilesFalling): State {
        // shift ones that fell to tile spot below
        // set current state to CheckForFallableTiles

        // for joined (numTilesSize x (numTilesSize * 2)) grid
        fun List<Tile?>.shiftTilesInColumnDown(lowestFallableTile: TileYCoordinate): List<Tile?> {
            // represents a column of visible and invisible tiles combined,
            // with invisible ones being used to supply new tiles
            check(this.size == numTilesSize * 2)

            if (null !in this) return this

            val newTopTile = listOf(newRandomTile()) as List<Tile?>

            val tilesThatFell = newTopTile + this.subList(0, lowestFallableTile + 1)

            val indexOfBottomTile = (numTilesSize * 2) - 1

            val tilesThatDidNotFall = (lowestFallableTile + 2..indexOfBottomTile).map { this[it] }

            return tilesThatFell + tilesThatDidNotFall
        }

        val joinedGridShift = tiles.fixTilesByGravity(directionToFallFrom)
            .mapIndexed { index, list -> invisibleTiles[index] + list }
            .mapIndexed { index, arrayOfTiles ->
                val lowestFallableTile = stepState.lowestPosYOfFallableTiles[index] + numTilesSize
                arrayOfTiles.shiftTilesInColumnDown(lowestFallableTile)
            }

        return this.copy(
            tiles = joinedGridShift
                .map { it.subList(numTilesSize, numTilesSize * 2) }
                .fixTilesByGravity(directionToFallFrom),
            invisibleTiles = joinedGridShift.map { it.subList(0, numTilesSize) },
            step = Step.CheckForFallableTiles
        )

    }

    private fun handleCheckForPoints(stepState: Step.CheckForPoints): State {
        fun List<Tile>.checkMatchesInColumnOrTransposedRow(): List<Tile?> {
            val tilesWithRemoved = this.map { it as Tile? }.toMutableList()

            var indexOfStartMatching = 0
            var matchSoFarSize = 0

            fun checkIfMatch() {
                if (matchSoFarSize >= GameView.numToMatch) {
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
            true -> when (stepState.previousInput == null) {
                true -> Step.WaitForInput
                false -> Step.InputDetected(
                    touchInput = TouchInput(
                        touched = stepState.previousInput.touchInput.switchWith,
                        switchWith = stepState.previousInput.touchInput.touched,
                        moveDirection = stepState.previousInput.touchInput.moveDirection.opposite()
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