package me.jameshunt.tilegame

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import me.jameshunt.tilegame.Step.*
import me.jameshunt.tilegame.input.*
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random


class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    data class Config(
        val gridSize: Int = 18,
        val numTileTypes: Int = 6, // max of 6 at the moment, add more in TileType
        val numToMatch: Int = 3,
        val milliToSleepFor: Long = 4L,
        val sleepEveryXTicks: Int = 1
    )

    init {
        setBackgroundColor(Color.GRAY)
        handleTouchEvents()
    }

    // will be instantiated after view is measured.
    private val screenContext by lazy {
        check(height != 0 && width != 0)
        val gridSizePixels = min(width, height)

        ScreenContext(
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

    private val tileRenderer = TileRenderer()
    private val externalInput = ExternalInput()

    private val stateMachine = StateMachine(
        externalInput = externalInput,
        onNewStateReadyForRender = { invalidate() },
        onError = { post { throw it } }
    )

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // randomlyConfigure()

        stateMachine.getCurrentState().renderTileGrid(tileRenderer, canvas, screenContext)

        drawEdgesOfBoard(canvas, screenContext)
    }

    private fun randomlyConfigure() {
        if(Random.nextInt() % 5 == 0) {
            val gridSize = externalInput.config.gridSize
            externalInput.config = externalInput.config.copy(gridSize = gridSize + (Random.nextInt() % 2))
        }

        if(Random.nextInt() % 400 == 0) {
            val everyXTicks = externalInput.config.sleepEveryXTicks
            val milli = externalInput.config.milliToSleepFor
            externalInput.config = externalInput.config.copy(
                sleepEveryXTicks = everyXTicks + 1,
                milliToSleepFor = max(1, milli - 1)
            )
        }
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
                screenContext = screenContext
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
