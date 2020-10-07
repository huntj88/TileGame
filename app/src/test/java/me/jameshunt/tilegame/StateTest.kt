package me.jameshunt.tilegame

import org.junit.Assert.*
import org.junit.Test

class StateTest {

    @Test
    fun checkGoToWaitForInputIfNoMoves() {
        val state1 = State(
            tiles = listOf(
                listOf(Tile(TileType.One), Tile(TileType.One)),
                listOf(Tile(TileType.One), Tile(TileType.One))
            ),
            invisibleTiles = listOf(
                listOf(Tile(TileType.One), Tile(TileType.One)),
                listOf(Tile(TileType.One), Tile(TileType.One))
            ),
            stepState = GameState.CheckForFallableTiles
        )

        state1
            .updateBoard { nextState, tick ->
                assertTrue(nextState.stepState is GameState.CheckForPoints)
            }
            .updateBoard { nextState, tick ->
                assertTrue(nextState.stepState is GameState.WaitForInput)
            }
    }

    @Test
    fun fallTwoTileTest() {
        val state1 = State(
            tiles = listOf(
                listOf(null, null),
                listOf(Tile(TileType.One), Tile(TileType.One))
            ),
            invisibleTiles = listOf(
                listOf(Tile(TileType.One), Tile(TileType.One)),
                listOf(Tile(TileType.One), Tile(TileType.One))
            ),
            stepState = GameState.CheckForFallableTiles
        )

        state1
            .assertShouldFallThenMoveBackToCheck()
            .assertShouldFallThenMoveBackToCheck()
            .updateBoard { nextState, tick ->
                assertTrue(nextState.stepState is GameState.CheckForPoints)
            }
            .updateBoard { nextState, tick ->
                assertTrue(nextState.stepState is GameState.WaitForInput)
            }
    }

    //
    @Test
    fun checkMatches() {
        val state1 = State(
            tiles = listOf(
                listOf(Tile(TileType.One), Tile(TileType.One), Tile(TileType.One)),
                listOf(Tile(TileType.One), Tile(TileType.Two), Tile(TileType.Three)),
                listOf(Tile(TileType.One), Tile(TileType.Three), Tile(TileType.Two))
            ),
            invisibleTiles = emptyList(),
            stepState = GameState.CheckForFallableTiles
        )

        val state2 = state1
            .updateBoard { nextState, tick ->
                assertTrue(nextState.stepState is GameState.CheckForPoints)
            }
            .updateBoard { nextState, tick ->
                assertTrue(nextState.stepState is GameState.RemovingTiles)
            }


        // all ones are removed. two ways of 3 in a row

        (state2.stepState as GameState.RemovingTiles).newBoardAfterRemove.let { newBoard ->
            newBoard.first().forEach { assertTrue(it == null) }
            assertTrue(newBoard[1][0] == null)
            assertTrue(newBoard[2][0] == null)
        }
    }

    private fun State.assertShouldFallThenMoveBackToCheck(): State {
        val fallingTickDuration = GameState
            .TilesFalling(0, emptyList(), GravitySensor.TileFromDirection.Top)
            .tickDuration

        assertEquals(GameState.CheckForFallableTiles, stepState)


        val newState = (0 until fallingTickDuration).fold(this) { acc, next ->
            acc.updateBoard { nextState, tick ->
                assertTrue(nextState.stepState is GameState.TilesFalling)
            }
        }

        return newState.updateBoard { nextState, tick ->
            assertEquals(GameState.CheckForFallableTiles, nextState.stepState)
            println("fell one tile")
        }
    }
}