package com.github.antoniodgonzalez.dialphone.bluetooth;

import java.util.EventListener;

public interface BluetoothSerialEventListener extends EventListener {

    void onDataReceived(byte[] buffer);

    void onError(String error);

    void onStateChange(int state);

}
