package me.jameshunt.tilegame

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
    val invisibleTiles: List<Tile?>,
    val step: Step,
    val tick: Int = 0,
    val config: GameView.Config,
    val directionToFallFrom: FallFromDirection,
    private val lastTouchInput: TouchInput?
) {

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
                generateNextState = { handleTilesFalling() }
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
        if (null in tiles.flatten()) {
            // check if board has nulls (due to grid resizing) and make them fall
            return this.copy(step = Step.CheckForFallableTiles)
        }

        return when (val input = lastTouchInput) {
            null -> this // state unchanged, keep waiting on input
            else -> this.copy(step = Step.InputDetected(input, tick))
        }
    }

    private fun handleInputDetected(step: Step.InputDetected): State {
        val input = step.touchInput

        check(input.touched.x >= 0 && input.touched.x <= tiles.size - 1)
        check(input.touched.y >= 0 && input.touched.y <= tiles.size - 1)
        check(input.switchWith.x >= 0 && input.switchWith.x <= tiles.size - 1)
        check(input.switchWith.y >= 0 && input.switchWith.y <= tiles.size - 1)

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
            step = when (step.switchBackIfNoPoints) {
                true -> Step.CheckForPoints(step)
                false -> Step.WaitForInput
            }
        )
    }

    private fun handleCheckingForFallableTiles(): State {
        check(tiles.size == config.gridSize)
        check(tiles.first().size == config.gridSize)

        if (null !in tiles.flatten()) {
            // no fallable tiles, check for points
            return this.copy(step = Step.CheckForPoints(null))
        }

        val directionFixedTiles = tiles.alignTilesByFallDirection(directionToFallFrom)

        val lowestPosYOfFallableTiles: List<TileYCoordinate> = directionFixedTiles.map { tileColumn ->
            val lowestPosOfNullTile = tileColumn.indexOfLast { it == null }
            (0 until lowestPosOfNullTile)
                .map { tileColumn[it] }
                .indexOfLast { it != null }
        }

        return this.copy(
            step = Step.TilesFalling(
                startTick = tick,
                lowestPosYOfFallableTiles = lowestPosYOfFallableTiles,
                fallingFromDirection = directionToFallFrom
            )
        )
    }

    private fun handleTilesFalling(): State {
        // shift ones that fell to tile spot below
        // set current state to CheckForFallableTiles

        fun List<Tile?>.shiftTilesInColumnDown(): List<Tile?> {
            // represents a column of the invisible tile and visible tiles combined, (gridSize + 1)
            check(this.size == config.gridSize + 1)
            if (null !in this) return this

            val newTopTile: List<Tile?> = listOf(newRandomTile(config.numTileTypes))

            val bottomNullIndex = this.lastIndexOf(null)
            check(bottomNullIndex != -1)

            val tileThatFell = this.subList(0, bottomNullIndex)
            val tilesThatDidNotFall = this.subList(bottomNullIndex + 1, this.size)
            return newTopTile + tileThatFell + tilesThatDidNotFall
        }

        val joinedGridShift = tiles.alignTilesByFallDirection(directionToFallFrom)
            .mapIndexed { index, list -> listOf(invisibleTiles[index]) + list }
            .map { arrayOfTiles -> arrayOfTiles.shiftTilesInColumnDown() }

        check(config.gridSize == joinedGridShift.size)
        check(config.gridSize + 1 == joinedGridShift.first().size)

        return this.copy(
            tiles = joinedGridShift
                .map { it.subList(1, config.gridSize + 1) }
                .alignTilesByFallDirection(directionToFallFrom),
            invisibleTiles = joinedGridShift.map { it.first() },
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
            true -> when (val previousInput = step.previousInput) {
                null -> Step.WaitForInput
                else -> Step.InputDetected(
                    touchInput = TouchInput(
                        touched = previousInput.touchInput.switchWith,
                        switchWith = previousInput.touchInput.touched,
                        moveDirection = previousInput.touchInput.moveDirection.opposite()
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

fun List<List<Tile?>>.alignTilesByFallDirection(directionToFallFrom: FallFromDirection): List<List<Tile?>> {
    // all logic was originally assumed to have tiles fall from the top of the grid
    // this manipulates the board so the same logic can be applied when tiles fall from a different direction

    // applying alignTilesByFallDirection() twice will give you the original value
    // https://en.wikipedia.org/wiki/Involution_(mathematics)

    fun List<List<Tile?>>.flipAlongYEqualsNegativeX(): List<List<Tile?>> = this
        .transpose2DTileList()
        .map { it.reversed() }
        .reversed()

    return when (directionToFallFrom) {
        FallFromDirection.Top -> this
        FallFromDirection.Bottom -> this.map { it.reversed() }
        FallFromDirection.Left -> this.transpose2DTileList()
        FallFromDirection.Right -> this.flipAlongYEqualsNegativeX()
    }
}