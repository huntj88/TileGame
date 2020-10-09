package me.jameshunt.tilegame

import me.jameshunt.tilegame.input.ExternalInput
import me.jameshunt.tilegame.input.FallFromDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
            step = Step.CheckForFallableTiles,
            externalInput = ExternalInput()
        )

        state1
            .getNextState().also { assertTrue(it.step is Step.CheckForPoints) }
            .getNextState().also { assertTrue(it.step is Step.WaitForInput) }
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
            step = Step.CheckForFallableTiles,
            externalInput = ExternalInput()
        )

        state1
            .assertShouldFallThenMoveBackToCheck()
            .assertShouldFallThenMoveBackToCheck()
            .getNextState().also { assertTrue(it.step is Step.CheckForPoints) }
            .getNextState().also { assertTrue(it.step is Step.WaitForInput) }
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
            step = Step.CheckForFallableTiles,
            externalInput = ExternalInput()
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