package me.jameshunt.tilegame

import me.jameshunt.tilegame.input.FallFromDirection
import org.junit.Test

import org.junit.Assert.*

class FixTileByGravityTest {

    // https://en.wikipedia.org/wiki/Involution_(mathematics)

    @Test
    fun `test involution, applying twice should return equal value`() {
        val tiles = listOf(
            listOf(Tile(TileType.One), Tile(TileType.Two), Tile(TileType.Three)),
            listOf(Tile(TileType.Five), Tile(TileType.Six), Tile(TileType.Four)),
            listOf(Tile(TileType.Six), Tile(TileType.Three), Tile(TileType.Five))
        )

        FallFromDirection.values().forEach {
            val appliedTwice = tiles
                .alignTilesByFallDirection(it)
                .alignTilesByFallDirection(it)

            assertEquals(appliedTwice, tiles)
        }
    }

    @Test
    fun `test involution randomly`() {
        (4..200).forEach { gridSize ->
            val tiles = getInitialBoard(gridSize, 6)

            FallFromDirection.values().forEach {
                val appliedTwice = tiles
                    .alignTilesByFallDirection(it)
                    .alignTilesByFallDirection(it)

                assertEquals(appliedTwice, tiles)
            }
        }
    }

    @Test
    fun `once should not be equal`() {
        val tiles = getInitialBoard(50, 6)

        FallFromDirection.values()
            .filter { it != FallFromDirection.Top } // doesn't change grid at all
            .forEach { assertNotEquals(tiles.alignTilesByFallDirection(it), tiles) }
    }
}
