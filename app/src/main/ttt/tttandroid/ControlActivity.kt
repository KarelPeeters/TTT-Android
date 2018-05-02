package ttt.tttandroid

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.SeekBar
import kotlinx.android.synthetic.main.activity_control.*
import ttt.tttandroid.Mode.RAW
import ttt.tttandroid.Mode.TENNIS

data class State(
        var lm: Int,
        var rm: Int,
        var servo: Int,
        var stepperDelay: Int,
        var stepperPos: Int
) {
    fun toBytes(): IntArray {
        return intArrayOf(
                lm and 0xff,
                rm and 0xff,
                servo and 0xff,
                stepperDelay and 0xff,
                (stepperDelay shr 8) and 0xff,
                stepperPos and 0xff,
                (stepperPos shr 8) and 0xff
        )
    }
}

private enum class Mode {
    TENNIS, RAW
}

class ControlActivity : AppCompatActivity() {
    val state: State = State(0, 0, 0, 0, 0)
    lateinit var connection: BluetoothConnection

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_control)

        state.apply {
            lm = lm_slider.progress
            rm = rm_slider.progress
            servo = servo_slider.progress
            stepperDelay = stepper_delay_slider.progress
            stepperPos = stepper_pos_slider.progress
        }

        lm_slider.setOnSeekBarChangeListener(seekBarListener)
        rm_slider.setOnSeekBarChangeListener(seekBarListener)
        servo_slider.setOnSeekBarChangeListener(seekBarListener)
        stepper_delay_slider.setOnSeekBarChangeListener(seekBarListener)
        stepper_pos_slider.setOnSeekBarChangeListener(seekBarListener)

        navigation.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_tennis -> {
                    changeMode(TENNIS)
                    true
                }
                R.id.navigation_raw -> {
                    changeMode(RAW)
                    true
                }
                else -> false
            }
        }

        changeMode(TENNIS)
    }

    private fun initConnection() {
        val device = intent.getParcelableExtra<NamedDevice>(Codes.BT_DEVICE_EXTRA)
        connectionStatusText.text = getString(R.string.connecting_to, device.name)
        connecting_spinner.visibility = View.VISIBLE
        connection = BluetoothConnection(this, device, ::onReceive, responseSize) { failed ->
            if (failed) {
                Log.e("ControlActivity", "failed")
                finish()
            } else runOnUiThread {
                connectionStatusText.text = getString(R.string.connected_to, device.name)
                connecting_spinner.visibility = View.INVISIBLE
                onStateChanged()
            }
        }
    }

    private fun changeMode(mode: Mode) {
        val (tennisVis, rawVis) = when (mode) {
            TENNIS -> View.VISIBLE to View.GONE
            RAW -> View.GONE to View.VISIBLE
        }

        view_tennis.visibility = tennisVis
        view_raw.visibility = rawVis
    }

    fun disconnectClicked(view: View) {
        finish()
    }

    val responseSize = 2

    fun onReceive(data: IntArray) {
        Log.e("Receive", data.toString())
    }

    override fun onStop() {
        connection.destroy()
        super.onStop()
    }

    override fun onStart() {
        initConnection()
        super.onStart()
    }

    fun onStateChanged() {
        val data = state.toBytes()
        connection.write(data)

        lm_value.text = state.lm.toString().leftPad(4)
        rm_value.text = state.rm.toString().leftPad(4)
        servo_value.text = state.servo.toString().leftPad(4)
        stepper_speed_value.text = state.stepperDelay.toString().leftPad(4)
        stepper_pos_value.text = state.stepperPos.toString().leftPad(4)
    }

    private val seekBarListener = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            when (seekBar) {
                lm_slider -> state.lm = progress
                rm_slider -> state.rm = progress
                servo_slider -> state.servo = progress
                stepper_delay_slider -> state.stepperDelay = progress
                stepper_pos_slider -> state.stepperPos = progress
            }
            onStateChanged()
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) {}

        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    }
}

private fun String.leftPad(length: Int) = if (this.length < length) " ".repeat(length - this.length) + this else this