package me.jameshunt.tilegame

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import kotlinx.android.parcel.Parcelize
import me.jameshunt.tilegame.Step.WaitForInput
import me.jameshunt.tilegame.input.ExternalInput
import me.jameshunt.tilegame.input.FallFromDirection
import me.jameshunt.tilegame.input.OnInputTouchListener
import me.jameshunt.tilegame.input.toInput
import kotlin.math.min


class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    @Parcelize
    data class Config(
        val gridSize: Int = 8,
        val numTileTypes: Int = 3, // max of 6 at the moment, add more in TileType
        val numToMatch: Int = 3,
        val milliToSleepFor: Long = 16L,
        val sleepEveryXTicks: Int = 1
    ): Parcelable

    init {
        setBackgroundColor(Color.GRAY)
        handleTouchEvents()
    }

    private val tileRenderer = TileRenderer()
    private val externalInput = ExternalInput()

    private val stateMachine = StateMachine(
        externalInput = externalInput,
        onNewStateReadyForRender = { invalidate() },
        onError = { post { throw it } }
    )

    var config: Config
        get() = externalInput.config
        set(value) { externalInput.config = value }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val screenContext = screenContext()

        stateMachine.getCurrentState().renderTileGrid(tileRenderer, canvas, screenContext)
        drawEdgesOfBoard(canvas, screenContext)
    }

    private fun screenContext(): ScreenContext {
        check(height != 0 && width != 0)
        val gridSizePixels = min(width, height)

        return ScreenContext(
            gridSizePixels = gridSizePixels,
            gridStartX = when (width == gridSizePixels) {
                true -> 0
                false -> (width - height) / 2
            },
            gridStartY = when (height == gridSizePixels) {
                true -> 0
                false -> (height - width) / 2
            }
        )
    }

    override fun onSaveInstanceState(): Parcelable {
        val tilesFlattened = stateMachine
            .getCurrentState()
            .tiles
            .flatten()
            .map { tile -> tile?.type?.ordinal ?: Int.MIN_VALUE }

        check(tilesFlattened.size / config.gridSize == config.gridSize)
        return Bundle().apply {
            putParcelable("super_state", super.onSaveInstanceState())
            putParcelable("config", config)
            putIntegerArrayList("tiles", ArrayList(tilesFlattened))
        }
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        (state as? Bundle)?.getParcelable<Config>("config")?.let { config ->
            val resumedTiles = state
                .getIntegerArrayList("tiles")!! // config exists, tiles should too
                .also { check(it.size / config.gridSize == config.gridSize) }
                .map { typeEnumIndex ->
                    when (typeEnumIndex == Int.MIN_VALUE) {
                        true -> null
                        false -> Tile(TileType.values()[typeEnumIndex])
                    }
                }
                .chunked(config.gridSize)

            stateMachine.resumeState(config, resumedTiles)
        }

        val superState = (state as? Bundle)?.getParcelable("super_state") ?: state
        super.onRestoreInstanceState(superState)
    }

    fun setDirectionToFallFrom(direction: FallFromDirection) {
        externalInput.setDirectionToFallFrom(
            step = stateMachine.getCurrentState().step,
            directionToFallFrom = direction
        )
    }

    private fun handleTouchEvents() {
        setOnTouchListener(OnInputTouchListener { touchInfo ->
            if (stateMachine.getCurrentState().step != WaitForInput) return@OnInputTouchListener

            externalInput.lastTouchInput = touchInfo.toInput(
                gridSize = externalInput.config.gridSize,
                screenContext = screenContext()
            )
        })
    }

    private val edgeOfBoardColor = Paint().apply { color = Color.DKGRAY }
    private fun drawEdgesOfBoard(canvas: Canvas, screenContext: ScreenContext) {
        canvas.drawRect(
            screenContext.gridStartX.toFloat(),
            0f,
            screenContext.gridStartX.toFloat() + screenContext.gridSizePixels.toFloat(),
            screenContext.gridStartY.toFloat(),
            edgeOfBoardColor
        )

        canvas.drawRect(
            screenContext.gridStartX.toFloat(),
            screenContext.gridStartY.toFloat() + screenContext.gridSizePixels.toFloat(),
            screenContext.gridStartX.toFloat() + screenContext.gridSizePixels.toFloat(),
            height.toFloat(),
            edgeOfBoardColor
        )
    }
}

data class ScreenContext(
    val gridSizePixels: Int,
    val gridStartX: Int,
    val gridStartY: Int
)
