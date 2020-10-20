package me.jameshunt.tilegame

import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.dialog_settings.*
import me.jameshunt.tilegame.input.GravitySensor
import kotlin.math.max

class MainActivity : AppCompatActivity() {

    private val sensorManager: SensorManager by lazy {
        ContextCompat.getSystemService(this, SensorManager::class.java)!!
    }
    private val sensor: Sensor by lazy {
        sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    }

    private val gravitySensor = GravitySensor {
        gameView.setDirectionToFallFrom(it)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        settingsButton.setOnClickListener {
            showConfigDialog()
        }
    }

    private fun showConfigDialog() {
        val configDialog = supportFragmentManager
            .findFragmentByTag("config")
            ?.let { it as? ConfigDialog }
            ?: ConfigDialog()

        configDialog.setVars()
        configDialog.show(supportFragmentManager, "config")
    }

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(gravitySensor, sensor, SensorManager.SENSOR_DELAY_GAME)
        supportFragmentManager
            .findFragmentByTag("config")
            ?.let { it as? ConfigDialog }
            ?.setVars()
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(gravitySensor)
    }

    private fun ConfigDialog.setVars() {
        val gameView = findViewById<GameView>(R.id.gameView)
        this.config = gameView.config
        this.callback = {
            gameView.config = it
        }
    }
}

class ConfigDialog : DialogFragment() {
    lateinit var config: GameView.Config
    lateinit var callback: (GameView.Config) -> Unit

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.dialog_settings, container, false)

    private fun setNewConfig() {
        config = config.copy(
            gridSize = max(1, gridSizeSeek.progress),
            milliToSleepFor = max(1, sleepLengthSeek.progress).toLong(),
            numTileTypes = max(1, numTileTypeSeek.progress),
            numToMatch = max(2, numMatchSeek.progress),
            sleepEveryXTicks = max(1, sleepXTickSeek.progress)
        )

        showCurrentConfig()

        callback(config)
    }

    private fun showCurrentConfig() {
        gridSizeSeek.progress = config.gridSize
        gridSizeSelectedLabel.text = config.gridSize.toString()

        sleepLengthSeek.progress = config.milliToSleepFor.toInt()
        sleepLengthSelectedLabel.text = config.milliToSleepFor.toString()

        numTileTypeSeek.progress = config.numTileTypes
        numTileTypeSelectedLabel.text = config.numTileTypes.toString()

        numMatchSeek.progress = config.numToMatch
        numMatchSelectedLabel.text = config.numToMatch.toString()

        sleepXTickSeek.progress = config.sleepEveryXTicks
        sleepXTickSelectedLabel.text = config.sleepEveryXTicks.toString()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        showCurrentConfig()

        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                setNewConfig()
            }
        }

        gridSizeSeek.setOnSeekBarChangeListener(listener)
        sleepLengthSeek.setOnSeekBarChangeListener(listener)
        numTileTypeSeek.setOnSeekBarChangeListener(listener)
        numMatchSeek.setOnSeekBarChangeListener(listener)
        sleepXTickSeek.setOnSeekBarChangeListener(listener)
    }
}
