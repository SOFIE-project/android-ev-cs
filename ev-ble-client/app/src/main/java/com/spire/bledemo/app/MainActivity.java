package com.spire.bledemo.app;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;


import androidx.localbroadcastmanager.content.LocalBroadcastManager;


import static android.content.ContentValues.TAG;


public class MainActivity extends Activity {


    // UI elements
    private TextView messages;
    private TextView mCsDetails;
    private TextView input;


    private IndyService mIndyService;
    private BluetoothLeService mBluetoothLeService;


    // OnCreate, called once to initialize the activity.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Grab references to UI elements.
        messages = (TextView) findViewById(R.id.messages);
        input = (EditText) findViewById(R.id.input);
        mCsDetails = findViewById(R.id.csDetails);

        LocalBroadcastManager.getInstance(this).registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());

        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mBLEServiceConnection, BIND_AUTO_CREATE);

        Intent indyServiceIntent = new Intent(this, IndyService.class);
        bindService(indyServiceIntent, mIndyServiceConnection, BIND_AUTO_CREATE);
    }


    // Start EV charging manually
    public void startCharging(View v) {
        nextStage(0);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mGattUpdateReceiver);
    }

    // Code to manage Service lifecycle.
    private final ServiceConnection mBLEServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    private final ServiceConnection mIndyServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mIndyService = ((IndyService.LocalBinder) service).getService();
            mIndyService.initialize();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mIndyService = null;
        }
    };


    // Boilerplate code from the activity creation:
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


//-------------------------------------------------------------------------------------------------
// EV - CS communication message building functions

    public void sendDID() {
        String message = mIndyService.createEVdidAndCSOProofRequest();
        mBluetoothLeService.write(message);
    }


    public void nextStage(int stage) {
        if (mBluetoothLeService.write(stage)) {
            writeLine("Sent: " + stage);
        } else {
            writeLine("Couldn't write stage to stage characteristic! " + stage);
        }
    }

    //Sends the proof  from file via BLE
    private void sendProof() {
        String msg = mIndyService.createErChargingProof();
        mBluetoothLeService.write(msg);

/*        try {
            InputStream iS = getAssets().open("proof_example.json");
            BufferedReader reader = new BufferedReader(new InputStreamReader(iS));

            StringBuffer sb = new StringBuffer();

            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }

            mBluetoothLeService.write(sb.toString());

            reader.close();
            iS.close();
        } catch (IOException ex) {
            writeLine("Unable to open asset.");
        }*/
    }


    // Write some text to the messages text view.
    // Care is taken to do this on the main UI thread so writeLine can be called
    // from any thread (like the BTLE callback).
    public void writeLine(final String text) {

        if (false) {
            String normalizedText;
            if (text.length() > 100) {
                normalizedText = text.subSequence(0, 100) + "..." + text.length();
            } else {
                normalizedText = text;
            }

            Log.d(TAG, normalizedText);
        } else {

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    String normalizedText;
                    if (text.length() > 100) {
                        normalizedText = text.subSequence(0, 100) + "..." + text.length();
                    } else {
                        normalizedText = text;
                    }
                    messages.append(normalizedText);
                    messages.append("\n");
                    final ScrollView sv = (ScrollView) messages.getParent();
                    sv.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            sv.fullScroll(View.FOCUS_DOWN);
                        }
                    }, 100L);
                }
            });
        }
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(IndyService.ACTION_INDY_INITIALIZED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BluetoothLeService.CS_CONNECTED);
        intentFilter.addAction(BluetoothLeService.RX_MSG_RECVD);
        intentFilter.addAction(BluetoothLeService.BLE_ERROR);
        return intentFilter;
    }

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if(IndyService.ACTION_INDY_INITIALIZED.equals(action)) {
                writeLine("Indy Initialized");
                writeLine("Scanning for devices...");
                mBluetoothLeService.startScan();
            } else if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                writeLine("Connected");
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                writeLine("Disconnected!");
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                writeLine("Service Discovery Completed");
            } else if (BluetoothLeService.CS_CONNECTED.equals(action)) {

                String deviceName = intent.getStringExtra(BluetoothLeService.DEVICE_NAME);
                String deviceAddress = intent.getStringExtra(BluetoothLeService.DEVICE_ADDRESS);

                mCsDetails.setText(" Status: Connected \n CS Name: " + deviceName + "\n MAC: " + deviceAddress + " \n");
                ((RelativeLayout) mCsDetails.getParent()).setBackgroundColor(getResources().getColor(android.R.color.holo_green_light));
                nextStage(0);

            } else if (BluetoothLeService.RX_MSG_RECVD.equals(action)) {
                String msg = intent.getStringExtra(BluetoothLeService.EXTRA_DATA);
                int stage = intent.getIntExtra(BluetoothLeService.CURRENT_STAGE, 0);
                if (stage == 0) {
                    mIndyService.parseAndSaveCsDid1(msg);
                    nextStage(1);
                    sendDID();
                    nextStage(2);
                } else if (stage == 2) {
                    mIndyService.parseCSDid2CSOProofAndEVCertificateProofRequest(msg);
                    nextStage(3);
                    sendProof();
                    nextStage(4);
                    //closeConnection();
                } else {
                    writeLine("Unexpected Message Received");
                }
            } else if (BluetoothLeService.BLE_ERROR.equals(action)) {
                //reattachService();
            }
        }
    };


    public void closeConnection() {
        mBluetoothLeService.cancelGattConnection();
    }
}
