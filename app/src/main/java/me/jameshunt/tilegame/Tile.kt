package me.jameshunt.tilegame

enum class TileType {
    One,
    Two,
    Three,
    Four,
    Five,
    Six
}

class Tile(val type: TileType)

fun newRandomTile(): Tile = Tile(
    type = TileType.values()
        .slice(0 until GameView.numTileTypes)
        .random()
)

fun getInitialSparseBoard(numTilesSize: Int): List<List<Tile?>> {
    return (0 until numTilesSize).map { x ->
        (0 until numTilesSize).map { y ->
            when ((y + x) % 3 == 0) {
                true -> newRandomTile()
                false -> null
            }
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