package com.github.antoniodgonzalez.dialphone.bluetooth;

import android.support.annotation.NonNull;

import java.util.EventListener;

public interface BluetoothSerialEventListener extends EventListener {

    void onDataReceived(@NonNull String line);

    void onError(@NonNull String error);

    void onStateChange(int state);

}
