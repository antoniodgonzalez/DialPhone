package com.github.antoniodgonzalez.dialphone.bluetooth;

import java.util.EventListener;

public interface BluetoothSerialEventListener extends EventListener {

    void onDataReceived(String line);

    void onError(String error);

    void onStateChange(int state);

}
