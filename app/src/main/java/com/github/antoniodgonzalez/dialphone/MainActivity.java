package com.github.antoniodgonzalez.dialphone;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.github.antoniodgonzalez.dialphone.bluetooth.BluetoothSerialEventListener;
import com.github.antoniodgonzalez.dialphone.bluetooth.BluetoothSerialService;

public class MainActivity extends AppCompatActivity implements BluetoothSerialEventListener {
    private static final String TAG = "MainActivity";

    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 3;
    private static final int REQUEST_PHONE = 5;

    private BluetoothAdapter bluetoothAdapter = null;
    private BluetoothSerialService bluetoothSerialService = new BluetoothSerialService(this);

    private TextView numberTextView = null;
    private TextView stateTextView = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter == null) {
            Toast.makeText(this, R.string.bt_not_available_leaving, Toast.LENGTH_LONG).show();
            finish();
        }

        numberTextView = findViewById(R.id.numberTextView);
        stateTextView = findViewById(R.id.stateTextView);

        final Button button = findViewById(R.id.callButton);
        button.setOnClickListener(v -> startCall());
    }

    private void startCall() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.CALL_PHONE}, REQUEST_PHONE);
            return;
        }

        startActivity(createCallIntent());
    }

    private Intent createCallIntent() {
        Intent callIntent = new Intent(Intent.ACTION_CALL);
        callIntent.setData(Uri.parse("tel:" + numberTextView.getText()));
        return callIntent;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_PHONE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startActivity(createCallIntent());
                }
                break;
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (bluetoothSerialService != null) {
            bluetoothSerialService.stop();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (bluetoothSerialService != null) {
            if (bluetoothSerialService.getState() == BluetoothSerialService.STATE_NONE) {
                bluetoothSerialService.start();
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE:
                if (resultCode == Activity.RESULT_OK) {
                    String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                    bluetoothSerialService.connect(address);
                }
                break;
            case REQUEST_ENABLE_BT:
                if (resultCode != Activity.RESULT_OK) {
                    Log.d(TAG, "BT not enabled");
                    Toast.makeText(this, R.string.bt_not_enabled_leaving,
                            Toast.LENGTH_SHORT).show();
                    this.finish();
                }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.secure_connect_scan: {
                Intent serverIntent = new Intent(this, DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
                return true;
            }
        }
        return false;
    }

    @Override
    public void onDataReceived(byte[] buffer) {
        String message = new String(buffer);
        Log.d(TAG,"onDataReceived: " + message);
        if (message.length() == 1 && message.charAt(0) >= '0' && message.charAt(0) <= '9') {
            String number = numberTextView.getText() + message;
            numberTextView.setText(number);
            if (number.length() == 9) {
                startCall();
            }
        }
    }

    @Override
    public void onError(String error) {
        Log.e(TAG, "onError: " + error);
        stateTextView.setTextColor(ContextCompat.getColor(this, R.color.colorError));
        stateTextView.setText(getString(R.string.connection_error,  error));
    }

    @Override
    public void onStateChange(int state) {
        Log.d(TAG, "onStateChange: " + Integer.toString(state));
        switch(state) {
            case BluetoothSerialService.STATE_NONE:
                stateTextView.setTextColor(Color.parseColor("gray"));
                stateTextView.setText(R.string.not_connected);
                break;
            case BluetoothSerialService.STATE_CONNECTING:
                stateTextView.setTextColor(Color.parseColor("gray"));
                stateTextView.setText(R.string.connecting);
                break;
            case BluetoothSerialService.STATE_CONNECTED:
                stateTextView.setTextColor(ContextCompat.getColor(this, R.color.colorOk));
                stateTextView.setText(getString(R.string.connected_to, bluetoothSerialService.getDeviceName()));
                break;
        }
    }
}
