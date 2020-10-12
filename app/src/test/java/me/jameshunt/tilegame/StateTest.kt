package me.jameshunt.tilegame

import me.jameshunt.tilegame.input.ExternalInput
import me.jameshunt.tilegame.input.FallFromDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StateTest {

    @Test
    fun checkGoToWaitForInputIfNoMoves() {
        val ex = ExternalInput(GameView.Config(gridSize = 2, numToMatch = 3))
        val state1 = State(
            tiles = listOf(
                listOf(Tile(TileType.One), Tile(TileType.One)),
                listOf(Tile(TileType.One), Tile(TileType.One))
            ),
            invisibleTiles = listOf(
                listOf(Tile(TileType.One), Tile(TileType.One)),
                listOf(Tile(TileType.One), Tile(TileType.One))
            ),
            step = Step.CheckForFallableTiles,
            directionToFallFrom = ex.directionToFallFrom,
            config = ex.config,
            lastTouchInput = null
        )

        state1
            .getNextState().also { assertTrue(it.step is Step.CheckForPoints) }
            .getNextState().also { assertTrue(it.step is Step.WaitForInput) }
    }

    @Test
    fun fallTwoTileTest() {
        val ex = ExternalInput(GameView.Config(gridSize = 2, numToMatch = 3))
        val state1 = State(
            tiles = listOf(
                listOf(null, null),
                listOf(Tile(TileType.One), Tile(TileType.One))
            ),
            invisibleTiles = listOf(
                listOf(Tile(TileType.One), Tile(TileType.One)),
                listOf(Tile(TileType.One), Tile(TileType.One))
            ),
            step = Step.CheckForFallableTiles,
            directionToFallFrom = ex.directionToFallFrom,
            config = ex.config,
            lastTouchInput = null
        )

        state1
            .assertShouldFallThenMoveBackToCheck()
            .assertShouldFallThenMoveBackToCheck()
            .getNextState().also { assertTrue(it.step is Step.CheckForPoints) }
            .getNextState().also {
                assertTrue(it.step is Step.WaitForInput)
            }
    }

    //
    @Test
    fun checkMatches() {
        val ex = ExternalInput(GameView.Config(gridSize = 3))
        val state1 = State(
            tiles = listOf(
                listOf(Tile(TileType.One), Tile(TileType.One), Tile(TileType.One)),
                listOf(Tile(TileType.One), Tile(TileType.Two), Tile(TileType.Three)),
                listOf(Tile(TileType.One), Tile(TileType.Three), Tile(TileType.Two))
            ),
            invisibleTiles = emptyList(),
            step = Step.CheckForFallableTiles,
            directionToFallFrom = ex.directionToFallFrom,
            config = ex.config,
            lastTouchInput = null
        )

        val state2 = state1
            .getNextState().also { assertTrue(it.step is Step.CheckForPoints) }
            .getNextState().also { assertTrue(it.step is Step.RemovingTiles) }


        // all ones are removed. two ways of 3 in a row

        (state2.step as Step.RemovingTiles).newBoardAfterRemove.let { newBoard ->
            newBoard.first().forEach { assertTrue(it == null) }
            assertTrue(newBoard[1][0] == null)
            assertTrue(newBoard[2][0] == null)
        }
    }

    @Test
    fun growGridTest() {
        val ex = ExternalInput(GameView.Config(gridSize = 3))
        val state1 = State(
            tiles = listOf(
                listOf(Tile(TileType.One), Tile(TileType.Two), Tile(TileType.One)),
                listOf(Tile(TileType.Two), Tile(TileType.Two), Tile(TileType.Three)),
                listOf(Tile(TileType.Four), Tile(TileType.Three), Tile(TileType.Two))
            ),
            invisibleTiles = emptyList(),
            step = Step.WaitForInput,
            directionToFallFrom = ex.directionToFallFrom,
            config = ex.config,
            lastTouchInput = null
        )

        state1
            .loadConfigChange(ex.config.copy(gridSize = 4))
            .getNextState().also {
                assertTrue(it.step is Step.CheckForFallableTiles)
            }
    }

    private fun State.assertShouldFallThenMoveBackToCheck(): State {
        val fallingTickDuration = Step
            .TilesFalling(0, emptyList(), FallFromDirection.Top)
            .tickDuration

        assertEquals(Step.CheckForFallableTiles, step)


        val newState = (0 until fallingTickDuration).fold(this) { acc, _ ->
            acc.getNextState().also { assertTrue(it.step is Step.TilesFalling) }
        }

        return newState.getNextState().also {
            assertEquals(Step.CheckForFallableTiles, it.step)
            println("fell one tile")
        }
    }
}