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
                val input = externalInput

                if (state.tick % input.config.sleepEveryXTicks == 0) {
                    Thread.sleep(input.config.milliToSleepFor)
                }

                val lastState = state
                val nextState = try {
                    lastState
                        .loadInputChanges(input)
                        .loadConfigChange(input.config)
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

    private fun State.loadConfigChange(newConfig: GameView.Config): State {
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
        )
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
}
