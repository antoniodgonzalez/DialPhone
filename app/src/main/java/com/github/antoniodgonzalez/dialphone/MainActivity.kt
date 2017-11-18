package com.github.antoniodgonzalez.dialphone

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast

import com.github.antoniodgonzalez.dialphone.bluetooth.BluetoothSerialEventListener
import com.github.antoniodgonzalez.dialphone.bluetooth.BluetoothSerialService
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity(), BluetoothSerialEventListener {

    private var bluetoothAdapter: BluetoothAdapter? = null
    private val bluetoothSerialService = BluetoothSerialService(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        if (bluetoothAdapter == null) {
            Toast.makeText(this, R.string.bt_not_available_leaving, Toast.LENGTH_LONG).show()
            finish()
        }

        callButton.setOnClickListener { startCall() }
        deleteButton.setOnClickListener { deleteNumber() }
    }

    private fun deleteNumber() {
        numberTextView.text = numberTextView.text.dropLast(1)
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

        if (!bluetoothAdapter!!.isEnabled) {
            val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT)
        }
    }

    public override fun onDestroy() {
        super.onDestroy()
        bluetoothSerialService.stop()
    }

    public override fun onResume() {
        super.onResume()

        if (bluetoothSerialService.state == BluetoothSerialService.STATE_NONE) {
            bluetoothSerialService.start()
        }
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        when (requestCode) {
            REQUEST_CONNECT_DEVICE -> if (resultCode == Activity.RESULT_OK) {
                val address = data.extras!!.getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS)
                bluetoothSerialService.connect(address)
            }
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
            R.id.secure_connect_scan -> {
                val serverIntent = Intent(this, DeviceListActivity::class.java)
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE)
                return true
            }
        }
        return false
    }

    override fun onDataReceived(buffer: ByteArray) {
        val message = String(buffer)
        Log.d(TAG, "onDataReceived: " + message)
        if (message.length == 1 && message[0] >= '0' && message[0] <= '9') {
            val number = numberTextView.text.toString() + message
            numberTextView.text = number
            if (number.length == 9) {
                startCall()
            }
        }
    }

    override fun onError(error: String) {
        Log.e(TAG, "onError: " + error)
        stateTextView.setTextColor(ContextCompat.getColor(this, R.color.colorError))
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
                stateTextView.setTextColor(ContextCompat.getColor(this, R.color.colorOk))
                stateTextView.text = getString(R.string.connected_to, bluetoothSerialService.deviceName)
            }
        }
    }

    companion object {
        private val TAG = "MainActivity"

        private val REQUEST_CONNECT_DEVICE = 1
        private val REQUEST_ENABLE_BT = 3
        private val REQUEST_PHONE = 5
    }
}
