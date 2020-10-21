package me.jameshunt.tilegame

import me.jameshunt.tilegame.input.ExternalInput
import java.util.concurrent.Executors
import java.util.concurrent.Future

class StateMachine(
    private val externalInput: ExternalInput,
    private val onNewStateReadyForRender: () -> Unit,
    private val onError: (e: Throwable) -> Unit
) {
    private var state: State = getInitialState()
    private var stateLoop: Future<*> = stateLoop()

    fun getCurrentState(): State {
        if (stateLoop.isDone) {
            stateLoop = stateLoop()
        }
        return state
    }

    /**
     * By putting all of the magic in a background thread we can control
     * the rate at which the state machine progresses
     * The UI thread also needs to do less work
     */
    private fun stateLoop(): Future<*> = Executors.newSingleThreadExecutor().submit {
        while (true) {
            val input = externalInput

            if (state.tick % input.config.sleepEveryXTicks == 0) {
                Thread.sleep(input.config.milliToSleepFor)
            }

            val lastState = state
            val nextState = try {
                lastState
                    .loadInputChanges(input)
                    .getNextState()
                    .loadConfigChange(input.config)
            } catch (t: Throwable) {
                onError(t)
                return@submit
            }

            synchronized(this) {
                state = nextState
            }

            if (lastState.step == Step.WaitForInput && nextState.step == Step.WaitForInput) {
                Thread.sleep(80)
            } else {
                onNewStateReadyForRender()
            }
        }
    }

    fun resumeState(config: GameView.Config, tiles: List<List<Tile?>>) {
        synchronized(this) {
            externalInput.config = config
            state = state.copy(tiles = tiles).loadConfigChange(config)
        }
    }

    private fun getInitialState(): State {
        val config = externalInput.config
        val gridSize = config.gridSize
        val numTileTypes = config.numTileTypes
        return State(
            tiles = getInitialBoard(gridSize, numTileTypes),
            invisibleTiles = getInitialBoard(gridSize, numTileTypes),
            step = Step.CheckForFallableTiles,
            config = config,
            directionToFallFrom = externalInput.directionToFallFrom,
            lastTouchInput = null
        )
    }

    private fun State.loadInputChanges(externalInput: ExternalInput): State {
        if (this.step is Step.InputDetected) {
            // already handling current input
            return this
        }

        fun Int.isInGridRange(): Boolean = this in (0 until externalInput.config.gridSize)

        return this.copy(
            directionToFallFrom = externalInput.directionToFallFrom,
            lastTouchInput = externalInput.lastTouchInput?.let {
                val validXMove = it.touched.x.isInGridRange() && it.switchWith.x.isInGridRange()
                val validYMove = it.touched.y.isInGridRange() && it.switchWith.y.isInGridRange()

                this@StateMachine.externalInput.lastTouchInput = null

                when (validXMove && validYMove) {
                    true -> it
                    false -> null
                }
            }
        )
    }
}

internal fun State.loadConfigChange(newConfig: GameView.Config): State {
    if (this.step is Step.InputDetected) {
        // defer loading config until after CheckForPoints evaluation
        return this
    }

    if (this.step is Step.CheckForPoints) {
        // defer loading config until after match evaluation
        return this
    }

    fun Step.TilesFalling.handleTilesFallingConfigChange(): Step {
        // falling state data could be stale due to different tile grid size
        return when (newConfig.gridSize == this.lowestPosYOfFallableTiles.size) {
            true -> this
            false -> Step.CheckForFallableTiles
        }
    }

    return this.copy(
        config = newConfig,
        tiles = this.tiles.shrinkOrGrow(newConfig.gridSize),
        invisibleTiles = this.invisibleTiles.shrinkOrGrowFilled(
            newGridSize = newConfig.gridSize,
            numTileTypes = newConfig.numTileTypes
        ),
        step = when (step) {
            is Step.TilesFalling -> step.handleTilesFallingConfigChange()
            else -> step
        }
    ).also {
        check(it.config.gridSize == it.tiles.size)
        check(it.config.gridSize == it.invisibleTiles.size)
        check(it.config.gridSize == it.tiles.first().size)
        check(it.config.gridSize == it.invisibleTiles.first().size)
    }
}
