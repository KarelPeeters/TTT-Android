package ttt.tttandroid

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_bluetooth_picker.*

class BluetoothDeviceAdapter(context: Context) : ArrayAdapter<NamedDevice>(context, R.layout.btdevice_row) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: run {
            val inflater = LayoutInflater.from(context)
            inflater.inflate(R.layout.btdevice_row, parent, false)
        }

        val namedDevice = getItem(position)

        val titleView = view.findViewById<TextView>(R.id.title)
        val subtitleView = view.findViewById<TextView>(R.id.subtitle)

        titleView.text = namedDevice.name
        subtitleView.text = namedDevice.device.address

        return view
    }
}

class BluetoothPickerActivity : AppCompatActivity() {
    private var scanning = false
    private lateinit var btAdapter: BluetoothAdapter
    private lateinit var listAdapter: ArrayAdapter<NamedDevice>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bluetooth_picker)

        btAdapter = BluetoothAdapter.getDefaultAdapter()

        listAdapter = BluetoothDeviceAdapter(this)
        devices_list.adapter = listAdapter

        devices_list.setOnItemClickListener { list, view, pos, id ->
            stopScan()
            val namedDevice = listAdapter.getItem(pos)
            val intent = Intent(this, ControlActivity::class.java).apply {
                putExtra(Codes.BT_DEVICE_EXTRA, namedDevice)
            }
            startActivity(intent)
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        registerReceiver(broadCastReceiver, filter)

        startScan()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            Codes.REQUEST_ENABLE_BT -> {
                if (resultCode == Activity.RESULT_OK) {
                    startScan()
                } else {
                    Toast.makeText(this, R.string.count_not_enable_bt, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == Codes.REQUEST_COARSE_LOC && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
            startScan()
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private val broadCastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    val name = device.name
                            ?: btAdapter.bondedDevices.find { it.address == device.address }?.name
                            ?: device.address

                    listAdapter.add(NamedDevice(name, device))
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    scanDone()
                }
            }
        }
    }

    private fun startScan() {
        if (!btAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, Codes.REQUEST_ENABLE_BT)
            return
        }

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION), Codes.REQUEST_COARSE_LOC)
            return
        }

        progress_spinner.visibility = View.VISIBLE
        action_button.setText(R.string.stop_scan)
        listAdapter.clear()
        scanning = true

        if (btAdapter.isDiscovering) {
            btAdapter.cancelDiscovery()
        }
        btAdapter.startDiscovery()
    }

    private fun stopScan() {
        if (btAdapter.isDiscovering) {
            btAdapter.cancelDiscovery()
        }
        scanDone()
    }

    private fun scanDone() {
        progress_spinner.visibility = View.INVISIBLE
        action_button.setText(R.string.scan)
        scanning = false
    }

    fun actionButtonClicked(view: View) {
        if (scanning)
            stopScan()
        else
            startScan()
    }

    override fun onDestroy() {
        btAdapter.cancelDiscovery()
        unregisterReceiver(broadCastReceiver)

        super.onDestroy()
    }
}
