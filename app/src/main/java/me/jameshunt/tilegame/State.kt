package me.jameshunt.tilegame

typealias TileXCoord = Int
typealias TileYCoord = Int

sealed class GameState {
    object WaitForInput : GameState()
    data class InputDetected(
        val touched: TileCoordinate,
        val switchWith: TileCoordinate,
        val direction: OnInputTouchListener.Direction,
        val startTick: Int,
        val switchBackIfNoPoints: Boolean = true
    ) : GameState() {
        data class TileCoordinate(
            val x: TileXCoord,
            val y: TileYCoord
        )
    }

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

class State {
    var invisibleTiles: List<List<Tile?>> = getInitialBoard()
    var tiles: List<List<Tile?>> = getInitialBoard()
    var currentState: GameState = GameState.CheckForFallableTiles
    var tick = 0
    var directionToFallFrom = GravitySensor.TileFromDirection.Top
        set(value) {
            if (currentState !is GameState.TilesFalling) {
                field = value
            }
        }

    private fun getInitialBoard(): List<List<Tile?>> {
        return (0 until GameView.numTilesSize).map { x ->
            (0 until GameView.numTilesSize).map { y ->
                //when(true) {
                when ((y + x) % 3 == 0) {
                    true -> Tile(TileType.values().slice(0 until GameView.numTileTypes).random())
                    false -> null
                }
            }
        }
    }

    private fun GameState.onAnimationCompleted(startTick: Int, action: () -> Unit) {
        val isStarting = tick == startTick
        val isStartingOrEnding = (tick - startTick) % tickDuration == 0
        val isEnding = !isStarting && isStartingOrEnding
        if (isEnding) {
            action()
        }
    }

    // updates using state machine concepts
    // will evaluate current state and progress to the next state
    fun updateBoard() {
        when (val state = currentState) {
            is GameState.WaitForInput -> {
                // noOp
            }
            is GameState.InputDetected -> {
                state.onAnimationCompleted(state.startTick) {
                    val touchedTile = tiles[state.touched.x][state.touched.y]
                    val switchWithTile =
                        tiles[state.switchWith.x][state.switchWith.y]

                    tiles = tiles.map { column ->
                        column.map { tile ->
                            when (tile) {
                                touchedTile -> switchWithTile
                                switchWithTile -> touchedTile
                                else -> tile
                            }
                        }
                    }

                    currentState = when (state.switchBackIfNoPoints) {
                        true -> GameState.CheckForPoints(state)
                        false -> GameState.WaitForInput
                    }
                }
            }
            is GameState.CheckForFallableTiles -> {

                // find lowest fallable posY of each row
                // if any fallable tiles set current state to TilesFalling
                // if no fallable tiles set current state to CheckForPoints

                val gravityFixedTiles = tiles.fixTilesByGravity(directionToFallFrom)

                val lowestPosYOfFallableTiles = gravityFixedTiles.map { tileColumn ->
                    val lowestPosOfNullTile = tileColumn.indexOfLast { it == null }
                    (0 until lowestPosOfNullTile)
                        .map { tileColumn[it] }
                        .indexOfLast { it != null } as TileYCoord
                }
                val doneFalling = lowestPosYOfFallableTiles.foldIndexed(true) { index, acc, posY ->
                    val indexOfBottomTile = GameView.numTilesSize - 1
                    acc && (posY == indexOfBottomTile || null !in gravityFixedTiles[index])
                }

                currentState = when (doneFalling) {
                    true -> GameState.CheckForPoints(null)
                    false -> GameState.TilesFalling(
                        tick,
                        lowestPosYOfFallableTiles,
                        directionToFallFrom
                    )
                }
            }
            is GameState.TilesFalling -> {
                state.onAnimationCompleted(state.startTick) {

                    // shift ones that fell to tile spot below
                    // set current state to CheckForFallableTiles

                    // for joined (numTilesSize x (numTilesSize * 2)) grid
                    fun List<Tile?>.shiftTilesInColumnDown(lowestFallableTile: TileYCoord): List<Tile?> {
                        if (null !in this) return this

                        val newTopTile = listOf(
                            Tile(TileType.values().slice(0 until GameView.numTileTypes).random())
                        ) as List<Tile?>

                        val tilesThatFell = newTopTile + this.subList(0, lowestFallableTile + 1)

                        val indexOfBottomTile = (GameView.numTilesSize * 2) - 1

                        val tilesThatDidNotFall = (lowestFallableTile + 2..indexOfBottomTile)
                            .map { this[it] }

                        return tilesThatFell + tilesThatDidNotFall
                    }

                    val joinedGridShift = tiles.fixTilesByGravity(directionToFallFrom)
                        .mapIndexed { index, list -> invisibleTiles[index] + list }
                        .mapIndexed { index, arrayOfTiles ->
                            val lowestFallableTile =
                                state.lowestPosYOfFallableTiles[index] + GameView.numTilesSize
                            arrayOfTiles.shiftTilesInColumnDown(lowestFallableTile)
                        }

                    invisibleTiles = joinedGridShift.map { it.subList(0, GameView.numTilesSize) }

                    tiles = joinedGridShift
                        .map { it.subList(GameView.numTilesSize, GameView.numTilesSize * 2) }
                        .fixTilesByGravity(directionToFallFrom)

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
                    val isColumnSame = null !in column
                    acc && isColumnSame
                }

                currentState = when (isBoardSame) {
                    true -> when (state.previousInput == null) {
                        true -> GameState.WaitForInput
                        false -> GameState.InputDetected(
                            touched = state.previousInput.switchWith,
                            switchWith = state.previousInput.touched,
                            direction = state.previousInput.direction.opposite(),
                            startTick = tick,
                            switchBackIfNoPoints = false
                        )
                    }
                    false -> GameState.RemovingTiles(tick, mergedMatches)
                }
            }
            is GameState.RemovingTiles -> {
                state.onAnimationCompleted(state.startTick) {
                    tiles = state.newBoardAfterRemove
                    currentState = GameState.CheckForFallableTiles
                }
            }
        }
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