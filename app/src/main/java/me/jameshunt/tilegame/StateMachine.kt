package me.jameshunt.tilegame

import me.jameshunt.tilegame.input.ExternalInput
import java.util.concurrent.Executors

class StateMachine(
    private val externalInput: ExternalInput,
    private val onNewStateReadyForRender: () -> Unit,
    private val onError: (e: Throwable) -> Unit
) {
    private var state: State = getInitialState()

    init {
        // By putting all of the magic in a background thread we can control
        // the rate at which the state machine progresses
        // UI thread also needs to do less work
        Executors.newSingleThreadExecutor().submit {
            while (true) {
                if (state.tick % externalInput.config.sleepEveryXTicks == 0) {
                    Thread.sleep(externalInput.config.milliToSleepFor)
                }

                val lastState = state
                val nextState = try {
                    lastState
                        .loadConfigChange()
                        .getNextState()
                } catch (t: Throwable) {
                    onError(t)
                    return@submit
                }

                synchronized(this) {
                    state = nextState
                }

                if (lastState.step != Step.WaitForInput || nextState.step != Step.WaitForInput) {
                    onNewStateReadyForRender()
                }
            }
        }
    }

    fun getCurrentState(): State = synchronized(this) { state }

    private fun State.loadConfigChange(): State {
        if (this.step is Step.CheckForPoints) {
            // defer loading config until after match evaluation
            return this
        }

        val currentConfig = externalInput.config

        fun Step.TilesFalling.handleTilesFallingConfigChange(): Step {
            // falling state data could be stale due to different tile grid size
            return when (currentConfig.gridSize == this.lowestPosYOfFallableTiles.size) {
                true -> this
                false -> Step.CheckForFallableTiles
            }
        }

        return this.copy(
            config = currentConfig,
            tiles = this.tiles.shrinkOrGrow(currentConfig.gridSize),
            invisibleTiles = this.invisibleTiles.shrinkOrGrowFilled(
                currentConfig.gridSize,
                currentConfig.numTileTypes
            ),
            step = when (step) {
                is Step.TilesFalling -> step.handleTilesFallingConfigChange()
                else -> step
            }
        )
    }

    private fun getInitialState(): State {
        val gridSize = externalInput.config.gridSize
        val numTileTypes = externalInput.config.numTileTypes
        return State(
            tiles = getInitialBoard(gridSize, numTileTypes),
            invisibleTiles = getInitialBoard(gridSize, numTileTypes),
            step = Step.CheckForFallableTiles,
            externalInput = externalInput,
            config = externalInput.config
        )
    }
}