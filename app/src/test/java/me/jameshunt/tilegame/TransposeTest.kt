package me.jameshunt.tilegame

import org.junit.Assert.*
import org.junit.Test

class TransposeTest {

    @Test
    fun transposeOneByOne() {
        val oneByOne = listOf(listOf<Tile?>(Tile(TileType.One)))
        oneByOne
            .transpose2DTileList()
            .simplifyToTileType()
            .let { assertTrue(it == oneByOne.simplifyToTileType()) }
    }

    @Test
    fun transposeTwoByTwo() {
        val twoByTwo = listOf(
            listOf<Tile?>(Tile(TileType.One), Tile(TileType.Two)),
            listOf<Tile?>(Tile(TileType.Two), Tile(TileType.One))
        )
        twoByTwo
            .transpose2DTileList()
            .simplifyToTileType()
            .let { assertTrue(it == twoByTwo.simplifyToTileType()) }
    }

    @Test
    fun transposeTwoByTwo2() {
        val input = listOf(
            listOf<Tile?>(Tile(TileType.One), Tile(TileType.Three)),
            listOf<Tile?>(Tile(TileType.Two), Tile(TileType.One))
        )

        val expected = listOf(
            listOf<Tile?>(Tile(TileType.One), Tile(TileType.Two)),
            listOf<Tile?>(Tile(TileType.Three), Tile(TileType.One))
        ).simplifyToTileType()
        input
            .transpose2DTileList()
            .simplifyToTileType()
            .let { assertTrue(it == expected) }
    }

    @Test
    fun transposeTwoByTwo3() {
        val input = listOf(
            listOf<Tile?>(Tile(TileType.One), Tile(TileType.Three)),
            listOf<Tile?>(Tile(TileType.Two), Tile(TileType.Four))
        )

        val expected = listOf(
            listOf<Tile?>(Tile(TileType.One), Tile(TileType.Two)),
            listOf<Tile?>(Tile(TileType.Three), Tile(TileType.Four))
        ).simplifyToTileType()
        input
            .transpose2DTileList()
            .simplifyToTileType()
            .let { assertTrue(it == expected) }
    }

    private fun List<List<Tile?>>.simplifyToTileType(): List<List<TileType?>> {
        return this.map { it.map { it?.type } }
    }
}