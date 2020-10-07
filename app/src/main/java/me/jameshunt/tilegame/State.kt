package me.jameshunt.tilegame

typealias TileXCoord = Int
typealias TileYCoord = Int

data class Input(
    val touched: TileCoordinate,
    val switchWith: TileCoordinate,
    val direction: OnInputTouchListener.Direction
) {
    data class TileCoordinate(
        val x: TileXCoord,
        val y: TileYCoord
    )
}

sealed class GameState {
    object WaitForInput : GameState()
    data class InputDetected(
        val input: Input,
        val startTick: Int,
        val switchBackIfNoPoints: Boolean = true
    ) : GameState()

    object CheckForFallableTiles : GameState()
    data class TilesFalling(
        val startTick: Int,
        val lowestPosYOfFallableTiles: List<TileYCoord>,
        val fallingFromDirection: GravitySensor.TileFromDirection
    ) : GameState()

    data class CheckForPoints(val previousInput: InputDetected?) : GameState()
    data class RemovingTiles(
        val startTick: Int,
        val newBoardAfterRemove: List<List<Tile?>>
    ) : GameState()

    val tickDuration: Int
        get() = when (this) {
            is InputDetected -> 8
            is TilesFalling -> 8
            is RemovingTiles -> 24
            else -> TODO(this.toString())
        }
}

data class State(
    val tiles: List<List<Tile?>>,
    val invisibleTiles: List<List<Tile?>>,
    val stepState: GameState,
    private val tick: Int = 0
) {
    private val numTilesSize = tiles.size

    var lastInput: Input? = null
    var directionToFallFrom = GravitySensor.TileFromDirection.Top
        private set

    fun setDirectionToFallFrom(directionToFallFrom: GravitySensor.TileFromDirection) {
        if (stepState !is GameState.TilesFalling) {
            this.directionToFallFrom = directionToFallFrom
        }
    }

    fun updateBoard(render: (nextState: State, tick: Int) -> Unit): State {
        val nextState = stepThroughStateMachine().copy(tick = tick + 1)
        nextState.directionToFallFrom = this.directionToFallFrom
        nextState.lastInput = this.lastInput

        render(nextState, tick)
        return nextState
    }

    private fun stepThroughStateMachine(): State {
        return when (val state = stepState) {
            is GameState.WaitForInput -> handleWaitForInput()
            is GameState.InputDetected -> state.onAnimationCompleted(
                startTick = state.startTick,
                nextState = { handleInputDetected(state) }
            )
            is GameState.CheckForFallableTiles -> handleCheckingForFallableTiles()
            is GameState.TilesFalling -> state.onAnimationCompleted(
                startTick = state.startTick,
                nextState = { handleTilesFalling(state) }
            )
            is GameState.CheckForPoints -> handleCheckForPoints(state)
            is GameState.RemovingTiles -> state.onAnimationCompleted(
                startTick = state.startTick,
                nextState = {
                    this.copy(
                        tiles = state.newBoardAfterRemove,
                        stepState = GameState.CheckForFallableTiles
                    )
                })
        }
    }

    private fun handleWaitForInput(): State {
        return when(val input = lastInput) {
            null -> this
            else -> this
                .apply { lastInput = null }
                .copy(stepState = GameState.InputDetected(input, tick))
        }
    }

    private fun handleInputDetected(stepState: GameState.InputDetected): State {
        val input = stepState.input
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
            stepState = when (stepState.switchBackIfNoPoints) {
                true -> GameState.CheckForPoints(stepState)
                false -> GameState.WaitForInput
            }
        )
    }

    private fun handleCheckingForFallableTiles(): State {
        // find lowest fallable posY of each row
        // if any fallable tiles set current state to TilesFalling
        // if no fallable tiles set current state to CheckForPoints

        val gravityFixedTiles = tiles.fixTilesByGravity(directionToFallFrom)

        val lowestPosYOfFallableTiles: List<TileYCoord> =
            gravityFixedTiles.map { tileColumn ->
                val lowestPosOfNullTile = tileColumn.indexOfLast { it == null }
                (0 until lowestPosOfNullTile)
                    .map { tileColumn[it] }
                    .indexOfLast { it != null }
            }
        val doneFalling = lowestPosYOfFallableTiles.foldIndexed(true) { index, acc, posY ->
            val indexOfBottomTile = numTilesSize - 1
            acc && (posY == indexOfBottomTile || null !in gravityFixedTiles[index])
        }

        return this.copy(
            stepState = when (doneFalling) {
                true -> GameState.CheckForPoints(null)
                false -> GameState.TilesFalling(
                    startTick = tick,
                    lowestPosYOfFallableTiles = lowestPosYOfFallableTiles,
                    fallingFromDirection = directionToFallFrom
                )
            }
        )
    }

    private fun handleTilesFalling(stepState: GameState.TilesFalling): State {
        // shift ones that fell to tile spot below
        // set current state to CheckForFallableTiles

        // for joined (numTilesSize x (numTilesSize * 2)) grid
        fun List<Tile?>.shiftTilesInColumnDown(lowestFallableTile: TileYCoord): List<Tile?> {
            if (null !in this) return this

            val newTopTile = listOf(
                Tile(TileType.values().slice(0 until GameView.numTileTypes).random())
            ) as List<Tile?>

            val tilesThatFell = newTopTile + this.subList(0, lowestFallableTile + 1)

            val indexOfBottomTile = (numTilesSize * 2) - 1

            val tilesThatDidNotFall = (lowestFallableTile + 2..indexOfBottomTile)
                .map { this[it] }

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
            stepState = GameState.CheckForFallableTiles
        )

    }

    private fun handleCheckForPoints(stepState: GameState.CheckForPoints): State {
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

        return this.copy(
            stepState = when (isBoardSame) {
                true -> when (stepState.previousInput == null) {
                    true -> GameState.WaitForInput
                    false -> GameState.InputDetected(
                        input = Input(
                            touched = stepState.previousInput.input.switchWith,
                            switchWith = stepState.previousInput.input.touched,
                            direction = stepState.previousInput.input.direction.opposite()
                        ),
                        startTick = tick,
                        switchBackIfNoPoints = false
                    )
                }
                false -> GameState.RemovingTiles(tick, mergedMatches)
            }
        )
    }

    private fun GameState.onAnimationCompleted(startTick: Int, nextState: () -> State): State {
        val isStarting = tick == startTick
        val isStartingOrEnding = (tick - startTick) % tickDuration == 0
        val isEnding = !isStarting && isStartingOrEnding
        return when (isEnding) {
            true -> nextState()
            false -> this@State // if in middle of animation then state not changing, return current state
        }
    }
}

fun List<List<Tile?>>.fixTilesByGravity(directionToFallFrom: GravitySensor.TileFromDirection): List<List<Tile?>> {
    fun List<List<Tile?>>.reverseGrid(): List<List<Tile?>> =
        this.map { it.reversed() }.reversed()

    return when (directionToFallFrom) {
        GravitySensor.TileFromDirection.Top -> this
        GravitySensor.TileFromDirection.Bottom -> this.map { it.reversed() }
        GravitySensor.TileFromDirection.Left -> this.transpose2DTileList()
        GravitySensor.TileFromDirection.Right -> this.transpose2DTileList().reverseGrid()
    }
}