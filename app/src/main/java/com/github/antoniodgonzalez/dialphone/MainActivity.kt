package com.github.antoniodgonzalez.dialphone

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.telephony.TelephonyManager
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import kotlinx.android.synthetic.main.activity_main.*

private const val TAG = "MainActivity"

private const val REQUEST_ENABLE_BT = 3
private const val REQUEST_PHONE = 5

class MainActivity : AppCompatActivity(), PhoneServiceEventListener {

    private val phoneService = PhoneService(this)

    private val preferences by lazy { Preferences(this) }

    private var number = ""
        set(value) {
            val regex = Regex("(.{1,3})(.{0,2})(.{0,2})(.{0,2})")
            numberTextView.text = value.replaceFirst(regex, "$1 $2 $3 $4").trim()
            field = value
        }

    private val phoneCallReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                "android.intent.action.PHONE_STATE" -> {
                    when (intent.extras.getString(TelephonyManager.EXTRA_STATE)) {
                        TelephonyManager.EXTRA_STATE_RINGING ->
                            phoneService.startRinging()
                        TelephonyManager.EXTRA_STATE_IDLE,
                        TelephonyManager.EXTRA_STATE_OFFHOOK ->
                            phoneService.stopRinging()
                    }
                }
            }
        }
    }

    private val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)

    override fun onDial(digit: String) {
        toneGenerator.startTone(digit.toInt(), 100)
        number += digit
        if (number.length == 9) {
            startCall()
        }
    }

    override fun onHangUp() {
        toneGenerator.stopTone()
        callButton.setImageResource(R.drawable.ic_call_end)
        callButton.backgroundTintList = ColorStateList.valueOf(getColor(android.R.color.holo_red_dark))
        number = ""
    }

    override fun onPickUp() {
        toneGenerator.startTone(ToneGenerator.TONE_CDMA_DIAL_TONE_LITE)
        callButton.setImageResource(R.drawable.ic_call)
        callButton.backgroundTintList = ColorStateList.valueOf(getColor(android.R.color.holo_green_dark))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (BluetoothAdapter.getDefaultAdapter() == null) {
            Snackbar.make(mainLayout, R.string.bt_not_available, Snackbar.LENGTH_LONG).show()
            if (!BuildConfig.DEBUG) {
                finish()
            }
        }

        callButton.setOnClickListener { if (number.isNotEmpty()) startCall() }
        deleteButton.setOnClickListener { deleteNumber() }

        val intentFilter = IntentFilter("android.intent.action.PHONE_STATE")
        registerReceiver(phoneCallReceiver, intentFilter)
    }

    private fun deleteNumber() {
        number = number.dropLast(1)
    }

    private fun startCall() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), REQUEST_PHONE)
            return
        }

        startActivity(createCallIntent())
    }

    private fun createCallIntent() = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            REQUEST_PHONE -> if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                startActivity(createCallIntent())
            }
        }
    }

    public override fun onStart() {
        super.onStart()

        when {
            BluetoothAdapter.getDefaultAdapter() == null -> return
            !BluetoothAdapter.getDefaultAdapter().isEnabled -> {
                val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(enableIntent, REQUEST_ENABLE_BT)
            }
            preferences.bluetoothDeviceAddress != null -> {
                connectToDevice(preferences.bluetoothDeviceAddress!!)
            }
        }
    }

    public override fun onDestroy() {
        super.onDestroy()
        phoneService.disconnect()
        unregisterReceiver(phoneCallReceiver)
    }

    public override fun onResume() {
        super.onResume()
        phoneService.start()
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_ENABLE_BT -> {
                when (resultCode) {
                    Activity.RESULT_OK -> {
                        if (preferences.bluetoothDeviceAddress != null) {
                            connectToDevice(preferences.bluetoothDeviceAddress!!)
                        }
                    }
                    else -> {
                        Log.d(TAG, "BT not enabled")
                        Snackbar.make(mainLayout, R.string.bt_not_enabled, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    private fun connectToDevice(address: String) {
        phoneService.connect(address)
        preferences.bluetoothDeviceAddress = address
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.connect_scan -> {
                val f = DeviceListFragment()
                f.onSelectedDevice = { address -> connectToDevice(address) }
                f.show(fragmentManager, "dialog")
                return true
            }
        }
        return false
    }

    override fun onError(error: String) {
        Log.e(TAG, "onError: " + error)
        stateTextView.setTextColor(getColor(android.R.color.holo_red_dark))
        stateTextView.text = getString(R.string.connection_error, error)
    }

    override fun onStateChange(state: PoneServiceState) {
        Log.d(TAG, "onStateChange: " + state.toString())
        when (state) {
            PoneServiceState.NONE -> {
                stateTextView.setTextColor(getColor(android.R.color.darker_gray))
                stateTextView.setText(R.string.not_connected)
            }
            PoneServiceState.CONNECTING -> {
                stateTextView.setTextColor(getColor(android.R.color.darker_gray))
                stateTextView.setText(R.string.connecting)
            }
            PoneServiceState.CONNECTED -> {
                stateTextView.setTextColor(getColor(android.R.color.holo_green_dark))
                stateTextView.text = getString(R.string.connected_to, phoneService.deviceName)
            }
        }
    }
}
