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
            assertEquals(GameState.CheckForFallableTiles, stepState)
            updateBoard {}

            assertTrue(stepState is GameState.CheckForPoints)
            updateBoard {}
            assertTrue(stepState is GameState.WaitForInput)
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

            updateBoard {}
            assertTrue(stepState is GameState.CheckForPoints)
            updateBoard {}
            assertTrue(stepState is GameState.WaitForInput)
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
            updateBoard {}
            assertTrue(stepState is GameState.CheckForPoints)

            updateBoard {}
            assertTrue(stepState is GameState.RemovingTiles)

            // all ones are removed. two ways of 3 in a row

            (stepState as GameState.RemovingTiles).newBoardAfterRemove.let { newBoard ->
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

        assertEquals(GameState.CheckForFallableTiles, stepState)

        (0 until fallingTickDuration).forEach {
            updateBoard {}
            assertTrue(stepState is GameState.TilesFalling)
        }
        updateBoard {}
        assertEquals(GameState.CheckForFallableTiles, stepState)
        println("fell one tile")
    }
}