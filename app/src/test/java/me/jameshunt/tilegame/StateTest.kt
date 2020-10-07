package me.jameshunt.tilegame

import org.junit.Assert.*
import org.junit.Test

class StateTest {

    @Test
    fun checkGoToWaitForInputIfNoMoves() {
        State(2).apply {
            tiles = listOf(
                listOf(Tile(TileType.One), Tile(TileType.One)),
                listOf(Tile(TileType.One), Tile(TileType.One))
            )
            assertEquals(GameState.CheckForFallableTiles, currentState)
            updateBoard()

            assertTrue(currentState is GameState.CheckForPoints)
            updateBoard()
            assertTrue(currentState is GameState.WaitForInput)
        }
    }

    @Test
    fun fallTwoTileTest() {
        State(2).apply {
            invisibleTiles = listOf(
                listOf(Tile(TileType.One), Tile(TileType.One)),
                listOf(Tile(TileType.One), Tile(TileType.One))
            )
            tiles = listOf(
                listOf(null, null),
                listOf(Tile(TileType.One), Tile(TileType.One))
            )
            assertShouldFallThenMoveBackToCheck()
            assertShouldFallThenMoveBackToCheck()

            updateBoard()
            assertTrue(currentState is GameState.CheckForPoints)
            updateBoard()
            assertTrue(currentState is GameState.WaitForInput)
        }
    }

    @Test
    fun checkMatches() {
        State(3).apply {
            tiles = listOf(
                listOf(Tile(TileType.One), Tile(TileType.One), Tile(TileType.One)),
                listOf(Tile(TileType.One), Tile(TileType.Two), Tile(TileType.Three)),
                listOf(Tile(TileType.One), Tile(TileType.Three), Tile(TileType.Two))
            )
            updateBoard()
            assertTrue(currentState is GameState.CheckForPoints)

            updateBoard()
            assertTrue(currentState is GameState.RemovingTiles)

            // all ones are removed. two ways of 3 in a row

            (currentState as GameState.RemovingTiles).newBoardAfterRemove.let { newBoard ->
                newBoard.first().forEach { assertTrue(it == null) }
                assertTrue(newBoard[1][0] == null)
                assertTrue(newBoard[2][0] == null)
            }

        }
    }

    private fun State.assertShouldFallThenMoveBackToCheck() {
        val fallingTickDuration = GameState
            .TilesFalling(0, emptyList(), GravitySensor.TileFromDirection.Top)
            .tickDuration

        assertEquals(GameState.CheckForFallableTiles, currentState)

        (0 until fallingTickDuration).forEach {
            updateBoard()
            assertTrue(currentState is GameState.TilesFalling)
        }
        updateBoard()
        assertEquals(GameState.CheckForFallableTiles, currentState)
        println("fell one tile")
    }
}