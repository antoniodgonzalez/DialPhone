package com.github.antoniodgonzalez.dialphone

import android.app.AlertDialog
import android.app.Dialog
import android.app.DialogFragment
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import com.github.antoniodgonzalez.dialphone.bluetooth.BluetoothSerialService
import kotlinx.android.synthetic.main.activity_device_list.view.*

private const val TAG = "DeviceListActivity"

class DeviceListFragment: DialogFragment() {

    private var bluetoothAdapter: BluetoothAdapter? = null

    private var newDevicesArrayAdapter: ArrayAdapter<String>? = null

    var onSelectedDevice: (address: String) -> Unit = {}

    /**
     * The on-click listener for all devices in the ListViews
     */
    private val deviceClickListener = AdapterView.OnItemClickListener { _, view, _, _ ->
        // Cancel discovery because it's costly and we're about to connect
        bluetoothAdapter?.cancelDiscovery()

        val deviceName = (view as TextView).text.toString()

        val device = bluetoothAdapter?.bondedDevices?.find {d -> d.name == deviceName }
        if (device != null) {
            onSelectedDevice(device.address)
        }

        dismiss()
    }

    /**
     * The BroadcastReceiver that listens for discovered devices and changes the title when
     * discovery is finished
     */
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    // Get the BluetoothDevice object from the Intent
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    // If it's already paired, skip it, because it's been listed already
                    if (device.bondState != BluetoothDevice.BOND_BONDED) {
                        newDevicesArrayAdapter!!.add(device.name + "\n" + device.address)
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    dialog?.setTitle(R.string.select_device)
                    dialogView!!.scanButton.visibility = View.VISIBLE
                    dialogView!!.progressBar.visibility = View.GONE

                    if (newDevicesArrayAdapter!!.count == 0) {
                        val noDevices = resources.getText(R.string.none_found).toString()
                        newDevicesArrayAdapter!!.add(noDevices)
                    }
                }
            }
        }
    }

    private var dialogView: View? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog? {
        super.onCreate(savedInstanceState)

        dialogView = activity.layoutInflater.inflate(R.layout.activity_device_list, null)
        val alertDialog = AlertDialog.Builder(activity)
                .setTitle(R.string.select_device)
                .setView(dialogView)
                .setNegativeButton("Cancel", { _, _ -> })
                .create()

        // Initialize the button to perform device discovery
        dialogView!!.scanButton.setOnClickListener {
            doDiscovery()
        }

        val pairedDevicesArrayAdapter = ArrayAdapter<String>(activity, R.layout.device_name)
        newDevicesArrayAdapter = ArrayAdapter(activity, R.layout.device_name)

        // Set up the ListView for paired devices
        dialogView!!.pairedDevicesListView.adapter = pairedDevicesArrayAdapter
        dialogView!!.pairedDevicesListView.onItemClickListener = deviceClickListener

        // Set up the ListView for newly discovered devices
        dialogView!!.newDevicesListView.adapter = newDevicesArrayAdapter
        dialogView!!.newDevicesListView.onItemClickListener = deviceClickListener

        // Register for broadcasts when a device is discovered
        activity.registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_FOUND))

        // Register for broadcasts when discovery has finished
        activity.registerReceiver(receiver, IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED))

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        val pairedDevices = bluetoothAdapter?.bondedDevices

        if (pairedDevices == null || pairedDevices.size <= 0) {
            val noDevices = resources.getText(R.string.none_paired).toString()
            pairedDevicesArrayAdapter.add(noDevices)
            return null
        }

        pairedDevices
            .filter { d -> d.uuids.any{ x -> x.uuid == BluetoothSerialService.SERIAL_PORT_UUID } }
            .forEach{ device -> pairedDevicesArrayAdapter.add(device.name) }

        return alertDialog
    }

    override fun onDestroy() {
        super.onDestroy()

        // Make sure we're not doing discovery anymore
        bluetoothAdapter?.cancelDiscovery()

        // Unregister broadcast listeners
        activity.unregisterReceiver(receiver)
    }

    /**
     * Start device discover with the BluetoothAdapter
     */
    private fun doDiscovery() {
        Log.d(TAG, "doDiscovery()")

        // Indicate scanning in the title
        dialog!!.setTitle(R.string.scanning)

        dialogView!!.scanButton.visibility = View.GONE
        dialogView!!.progressBar.visibility = View.VISIBLE

        // Turn on sub-title for new devices
        dialogView!!.pairedDevicesListView.visibility = View.VISIBLE

        // If we're already discovering, stop it
        if (bluetoothAdapter != null && bluetoothAdapter!!.isDiscovering) {
            bluetoothAdapter!!.cancelDiscovery()
        }

        // Request discover from BluetoothAdapter
        bluetoothAdapter?.startDiscovery()
    }
}
