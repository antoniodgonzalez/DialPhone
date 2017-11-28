package com.github.antoniodgonzalez.dialphone

import com.github.antoniodgonzalez.dialphone.bluetooth.BluetoothSerialEventListener
import com.github.antoniodgonzalez.dialphone.bluetooth.BluetoothSerialService

enum class PoneServiceState {
    NONE, CONNECTING, CONNECTED
}

class PhoneService(val listener: PhoneServiceEventListener) {

    private val bluetoothSerialService = BluetoothSerialService (object : BluetoothSerialEventListener {
        override fun onError(error: String) {
            listener.onError(error)
        }

        override fun onStateChange(state: Int) {
            when (state) {
                BluetoothSerialService.STATE_NONE -> listener.onStateChange(PoneServiceState.NONE)
                BluetoothSerialService.STATE_CONNECTING -> listener.onStateChange(PoneServiceState.CONNECTING)
                BluetoothSerialService.STATE_CONNECTED -> {
                    requestState()
                    listener.onStateChange(PoneServiceState.CONNECTED)
                }
            }
        }

        override fun onDataReceived(message: String) {
            when {
                message.startsWith("DIAL") -> listener.onDial(message.substring(4))
                message == "HANGUP" -> listener.onHangUp()
                message == "PICKUP" -> listener.onPickUp()
            }
        }
    })
    
    fun connect(address: String) {
        if (bluetoothSerialService.state == BluetoothSerialService.STATE_NONE) {
            bluetoothSerialService.connect(address)
        }
    }

    fun start() = bluetoothSerialService.start()

    fun disconnect() = bluetoothSerialService.stop()

    fun startRinging() = bluetoothSerialService.write("r".toByteArray())

    fun stopRinging() = bluetoothSerialService.write("o".toByteArray())

    private fun requestState(): Unit = bluetoothSerialService.write("s".toByteArray())

    val deviceName: String
        get() = bluetoothSerialService.deviceName

}