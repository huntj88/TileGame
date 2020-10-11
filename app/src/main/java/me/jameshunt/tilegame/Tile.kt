package me.jameshunt.tilegame

enum class TileType {
    One,
    Two,
    Three,
    Four,
    Five,
    Six
}

data class Tile(val type: TileType)

fun newRandomTile(numTileTypes: Int): Tile = Tile(
    type = TileType.values()
        .slice(0 until numTileTypes)
        .random()
)

fun getInitialBoard(gridSize: Int, numTileTypes: Int): List<List<Tile?>> {
    return (0 until gridSize).map { x ->
        (0 until gridSize).map { y ->
            newRandomTile(numTileTypes)
        }
    }
}

fun List<List<Tile?>>.transpose2DTileList(): List<List<Tile?>> {
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

fun List<List<Tile?>>.shrinkOrGrow(newGridSize: Int): List<List<Tile?>> {
    if (this.size == newGridSize) return this

    return (0 until newGridSize).map { x ->
        val column = getOrNull(x)
        (0 until newGridSize).map { y ->
            column?.getOrNull(y)
        }
    }
}

fun List<List<Tile?>>.shrinkOrGrowFilled(newGridSize: Int, numTileTypes: Int): List<List<Tile?>> {
    if (this.size == newGridSize) return this

    return (0 until newGridSize).map { x ->
        val column = getOrNull(x)
        (0 until newGridSize).map { y ->
            val existingTile = column?.getOrNull(y)
            existingTile ?: newRandomTile(numTileTypes)
        }
    }
}
