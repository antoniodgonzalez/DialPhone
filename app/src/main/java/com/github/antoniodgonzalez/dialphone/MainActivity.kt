package com.github.antoniodgonzalez.dialphone

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.telephony.TelephonyManager
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast

import com.github.antoniodgonzalez.dialphone.bluetooth.BluetoothSerialEventListener
import com.github.antoniodgonzalez.dialphone.bluetooth.BluetoothSerialService
import kotlinx.android.synthetic.main.activity_main.*
import android.content.IntentFilter
import android.content.res.ColorStateList

private const val TAG = "MainActivity"

private const val REQUEST_ENABLE_BT = 3
private const val REQUEST_PHONE = 5

class MainActivity : AppCompatActivity(), BluetoothSerialEventListener {

    private var bluetoothAdapter: BluetoothAdapter? = null
    private val bluetoothSerialService = BluetoothSerialService(this)
    private var number = ""
        set(value) {
            val regex = Regex("(.{1,3})(.{0,2})(.{0,2})(.{0,2})");
            numberTextView.text = value.replaceFirst(regex, "$1 $2 $3 $4").trim()
            field = value
        }

    private val phoneCallReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                "android.intent.action.PHONE_STATE" -> {
                    val state = intent.extras.getString(TelephonyManager.EXTRA_STATE)
                    when (state) {
                        TelephonyManager.EXTRA_STATE_RINGING ->
                            startRinging()
                        TelephonyManager.EXTRA_STATE_IDLE,
                        TelephonyManager.EXTRA_STATE_OFFHOOK ->
                            stopRinging()
                    }
                }
            }
        }
    }

    private fun startRinging() = bluetoothSerialService.write("r".toByteArray())

    private fun stopRinging() = bluetoothSerialService.write("o".toByteArray())

    private fun requestState() = bluetoothSerialService.write("s".toByteArray())

    private fun dial(digit: String) {
        number += digit
        if (number.length == 9) {
            startCall()
        }
    }

    private fun hangUp() {
        callButton.setImageResource(R.drawable.ic_call_end)
        callButton.backgroundTintList = ColorStateList.valueOf(getColor(android.R.color.holo_red_dark))
        number = ""
    }

    private fun pickUp() {
        callButton.setImageResource(R.drawable.ic_call)
        callButton.backgroundTintList = ColorStateList.valueOf(getColor(android.R.color.holo_green_dark))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        if (bluetoothAdapter == null) {
            Toast.makeText(this, R.string.bt_not_available_leaving, Toast.LENGTH_LONG).show()
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

    private fun createCallIntent(): Intent {
        val callIntent = Intent(Intent.ACTION_CALL)
        callIntent.data = Uri.parse("tel:${numberTextView.text}")
        return callIntent
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            REQUEST_PHONE -> if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startActivity(createCallIntent())
            }
        }
    }

    public override fun onStart() {
        super.onStart()

        if (bluetoothAdapter != null && !bluetoothAdapter!!.isEnabled) {
            val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT)
        }
    }

    public override fun onDestroy() {
        super.onDestroy()
        bluetoothSerialService.stop()
        unregisterReceiver(phoneCallReceiver)
    }

    public override fun onResume() {
        super.onResume()

        if (bluetoothSerialService.state == BluetoothSerialService.STATE_NONE) {
            bluetoothSerialService.start()
        }
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_ENABLE_BT -> if (resultCode != Activity.RESULT_OK) {
                Log.d(TAG, "BT not enabled")
                Toast.makeText(this, R.string.bt_not_enabled_leaving,
                        Toast.LENGTH_SHORT).show()
                this.finish()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.connect_scan -> {
                val f = DeviceListFragment()
                f.onSelectedDevice = { address -> bluetoothSerialService.connect(address) }
                f.show(fragmentManager, "dialog")
                return true
            }
        }
        return false
    }

    override fun onDataReceived(message: String) {
        Log.d(TAG, "onDataReceived: " + message)
        when {
            message.startsWith("DIAL") -> dial(message.substring(4))
            message == "HANGUP" -> hangUp()
            message == "PICKUP" -> pickUp()
        }
    }

    override fun onError(error: String) {
        Log.e(TAG, "onError: " + error)
        stateTextView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        stateTextView.text = getString(R.string.connection_error, error)
    }

    override fun onStateChange(state: Int) {
        Log.d(TAG, "onStateChange: " + Integer.toString(state))
        when (state) {
            BluetoothSerialService.STATE_NONE -> {
                stateTextView.setTextColor(Color.parseColor("gray"))
                stateTextView.setText(R.string.not_connected)
            }
            BluetoothSerialService.STATE_CONNECTING -> {
                stateTextView.setTextColor(Color.parseColor("gray"))
                stateTextView.setText(R.string.connecting)
            }
            BluetoothSerialService.STATE_CONNECTED -> {
                stateTextView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                stateTextView.text = getString(R.string.connected_to, bluetoothSerialService.deviceName)
                requestState()
            }
        }
    }
}
