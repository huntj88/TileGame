package me.jameshunt.tilegame

import org.junit.Assert.*
import org.junit.Test

class TransposeTest {

    @Test
    fun transposeOneByOne() {
        val oneByOne = listOf(listOf<Tile?>(Tile(TileType.One)))
        oneByOne
            .transpose2DTileList()
            .let { assertTrue(it == oneByOne) }
    }

    @Test
    fun transposeTwoByTwo() {
        val twoByTwo = listOf(
            listOf<Tile?>(Tile(TileType.One), Tile(TileType.Two)),
            listOf<Tile?>(Tile(TileType.Two), Tile(TileType.One))
        )
        twoByTwo
            .transpose2DTileList()
            .let { assertTrue(it == twoByTwo) }
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
        )
        input
            .transpose2DTileList()
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
        )
        input
            .transpose2DTileList()
            .let { assertTrue(it == expected) }
    }
}