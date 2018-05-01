package ttt.tttandroid

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Parcelable
import android.util.Log
import kotlinx.android.parcel.Parcelize
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue

typealias Receiver = (data: Int) -> Unit

@Parcelize
data class NamedDevice(val name: String, val device: BluetoothDevice) : Parcelable

class BluetoothConnection(
        private val context: Context,
        private val namedDevice: NamedDevice,
        private val receiver: Receiver,
        private val finishedCallBack: (failed: Boolean) -> Unit
) {
    val device = namedDevice.device
    private var running = true

    private val initThread = InitThread().apply { start() }
    private var readerThread: ReaderThread? = null
    private var writerThread: WriterThread? = null

    private lateinit var socket: BluetoothSocket
    private lateinit var input: InputStream
    private lateinit var output: OutputStream

    private val writeQueue: BlockingQueue<Int> = LinkedBlockingQueue()

    private val broadCastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                val prevState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR)
                val changeDevice = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)

                if (changeDevice != device)
                    return

                if (state == BluetoothDevice.BOND_BONDED)
                    initThread.bondFinished()
                if (state == BluetoothDevice.BOND_NONE && prevState == BluetoothDevice.BOND_BONDING)
                    handleFail()

                Log.e("BTDebug", "state: $prevState -> $state")
            }
        }
    }

    fun handleFail() {
        finishedCallBack(true)
        destroy()
        initThread.failed()
    }

    init {
        context.registerReceiver(broadCastReceiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
    }

    inner class InitThread : Thread() {
        private var failed = false
        private var bondFinished = false
        private val finishedLock = java.lang.Object()

        fun failed() {
            Log.e("InitThread", "failed")
            failed = true
            synchronized(finishedLock) {
                finishedLock.notify()
            }
        }

        fun bondFinished() {
            bondFinished = true
            synchronized(finishedLock) {
                finishedLock.notify()
            }
        }

        override fun run() {
            try {
                BluetoothAdapter.getDefaultAdapter().cancelDiscovery()

                if (device.bondState != BluetoothDevice.BOND_BONDED) {
                    device.createBond()

                    while (!bondFinished) {
                        if (failed)
                            return

                        synchronized(finishedLock) {
                            finishedLock.wait()
                        }
                    }
                }

                try {
                    val uuid = device.uuids?.get(0)?.uuid ?: throw AssertionError("UUID not found")
                    socket = device.createRfcommSocketToServiceRecord(uuid)
                    socket.connect()
                } catch (e: IOException) {
                    handleFail()
                    return
                }

                input = socket.inputStream
                output = socket.outputStream
                readerThread = ReaderThread().apply { start() }
                writerThread = WriterThread().apply { start() }

                finishedCallBack(false)
            } catch (e: InterruptedException) {
            }
        }
    }

    inner class ReaderThread : Thread("ReaderThread") {
        override fun run() {
            try {
                while (running) {
                    receiver(input.read())
                }
            } catch (e: InterruptedException) {

            } catch (e: IOException) {
                handleFail()
            }
        }
    }

    inner class WriterThread : Thread("WriterThread") {
        override fun run() {
            try {
                while (running) {
                    try {
                        output.write(writeQueue.take())
                    } catch (e: IOException) {
                        handleFail()
                    }
                }
            } catch (e: InterruptedException) {
                Log.e("WriterThread", "interrupted")
            }
        }
    }

    fun destroy() {
        running = false

        initThread.interrupt()
        readerThread?.interrupt()
        writerThread?.interrupt()

        socket.close()

        try {
            context.unregisterReceiver(broadCastReceiver)
        } catch(e: IllegalArgumentException) {
            //receiver wasn't registered yet
        }
    }

    /**
     * Write the lower 8 bits of data to the connection
     */
    fun write(data: Int) {
        writeQueue.put(data)
    }

    fun write(data: IntArray) {
        for (value in data) {
            write(value)
        }
    }
}