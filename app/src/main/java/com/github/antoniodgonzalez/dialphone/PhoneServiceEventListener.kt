package com.github.antoniodgonzalez.dialphone

interface PhoneServiceEventListener {
    fun onDial(digit: String)
    fun onHangUp()
    fun onPickUp()
    fun onError(error: String)
    fun onStateChange(state: PoneServiceState)
}