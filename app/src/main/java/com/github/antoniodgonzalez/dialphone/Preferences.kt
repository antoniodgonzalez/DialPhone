package com.github.antoniodgonzalez.dialphone

import android.content.Context
import android.content.SharedPreferences

private const val NAME = "dialphone.preferences"
private const val BLUETOOTH_DEVICE_ADDRESS= "bluetooth_device_address"

class Preferences(context: Context) {
    private val preferences: SharedPreferences = context.getSharedPreferences(NAME, 0)

    var bluetoothDeviceAddress: String?
        get() = preferences.getString(BLUETOOTH_DEVICE_ADDRESS, null)
        set(value) = preferences.edit().putString(BLUETOOTH_DEVICE_ADDRESS, value).apply()
}