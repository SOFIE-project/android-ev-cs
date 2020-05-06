/*
 * Copyright 2015 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.webbluetoothcg.bletestperipheral;

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
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import static android.content.ContentValues.TAG;

public class MainActivity extends Activity {

    private static final int REQUEST_ENABLE_BT = 1;

    private static final String TAG = MainActivity.class.getCanonicalName();

    BluetoothLeService mBluetoothLeService;
    IndyService mIndyService;

    // UI elements
    private TextView mStatusText, messages;
    private TextView mCsName, mCsMac, mEvDid, mCsDid1, mCsDid2, mCsoProof;
    private ImageView chargeProgress;

    private PieProgressDrawable mPieProgress;


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

    public void handleEvent(int newState) {
        switch (newState) {
            case 0: // write did to rx
                String tempDID = mIndyService.createCsDid1();
                Log.v(TAG, "Started sending did: " + System.currentTimeMillis());
                mBluetoothLeService.writeLongLocalCharacteristic(tempDID);
                break;

            case 1:
                break;

            case 2:
                // verify the proof request validity or throw error, reset state and disconnect client
                String msg = mBluetoothLeService.getBLEMessage();
                mIndyService.parseEVDIDAndCSOProofRequest(msg);

                // write real DID
                String tempDID2 = mIndyService.createCSDid2CSOProofAndEVCertificateProofRequest();
                Log.v(TAG, "Started sending real did: " + System.currentTimeMillis());
                mBluetoothLeService.writeLongLocalCharacteristic(tempDID2);
                break;

            case 3:
                break;

            case 4:
                // verify EV customer proof
                msg = mBluetoothLeService.getBLEMessage();
                mIndyService.verifyErChargingProof(msg);

                break;
        }
    }

    /////////////////////////////////
    ////// Lifecycle Callbacks //////
    /////////////////////////////////

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Grab references to UI elements.
        mStatusText = findViewById(R.id.status_text);
        messages = (TextView) findViewById(R.id.messages);
        mCsName = findViewById(R.id.cs_name_text);
        mCsMac = findViewById(R.id.cs_mac_text);
        mEvDid = findViewById(R.id.ev_did_text);
        mCsDid1 = findViewById(R.id.cs_did1_text);
        mCsDid2 = findViewById(R.id.cs_did2_text);
        mCsoProof = findViewById(R.id.csoProofText);

        mPieProgress = new PieProgressDrawable();
        mPieProgress.setColor(ContextCompat.getColor(this, R.color.f_green));
        chargeProgress = (ImageView) findViewById(R.id.charge_progress);
        chargeProgress.setImageDrawable(mPieProgress);

        updatePieProgress(30);

        LocalBroadcastManager.getInstance(this).registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());

        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mBLEServiceConnection, BIND_AUTO_CREATE);

        Intent indyServiceIntent = new Intent(this, IndyService.class);
        bindService(indyServiceIntent, mIndyServiceConnection, BIND_AUTO_CREATE);


    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(IndyService.ACTION_INDY_INITIALIZED);

        intentFilter.addAction(BluetoothLeService.EV_CONNECTED);
        intentFilter.addAction(BluetoothLeService.TX_MSG_RECVD);
        intentFilter.addAction(BluetoothLeService.BROADCASTING);
        intentFilter.addAction(BluetoothLeService.BLE_ERROR);
        return intentFilter;
    }

    // Handles various events fired by the BLE Service and Indy Service.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if(IndyService.ACTION_INDY_INITIALIZED.equals(action)) {
                writeLine("Indy Initialized");
                writeLine("Scanning for devices...");
                mBluetoothLeService.startGattServer();
            } else if (BluetoothLeService.BROADCASTING.equals(action)) {
                writeLine("Broadcasting!");
                mStatusText.setText("Broadcasting");
            } else if (BluetoothLeService.EV_CONNECTED.equals(action)) {
                String msg = intent.getStringExtra(BluetoothLeService.EXTRA_DATA);
                mCsMac.setText(msg);
                mStatusText.setText("Connected");
                writeLine("Connected");
                invalidateOptionsMenu();  // what is this??

            } else if (BluetoothLeService.TX_MSG_RECVD.equals(action)) {
                int stage = intent.getIntExtra(BluetoothLeService.CURRENT_STAGE, -1);
                if (stage >= 0) {
                    handleEvent(stage);
                } else {
                    writeLine("Unexpected Message Received");
                }
            } else if (BluetoothLeService.BLE_ERROR.equals(action)) {
                mStatusText.setText("Bluetooth Error");
            }
        }
    };



    public void updatePieProgress(int progress) {
        mPieProgress.setLevel(progress);
        chargeProgress.invalidate();
    }


    // Code to manage Service lifecycle.
    private final ServiceConnection mBLEServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            mBluetoothLeService.initialize();
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


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_peripheral, menu);
        return true /* show menu */;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                if (false) {
                    Toast.makeText(this, R.string.bluetoothAdvertisingNotSupported, Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Advertising not supported");
                }
                onStart();
            } else {
                //TODO(g-ortuno): UX for asking the user to activate bt
                Toast.makeText(this, R.string.bluetoothNotEnabled, Toast.LENGTH_LONG).show();
                Log.e(TAG, "Bluetooth not enabled");
                finish();
            }
        }
    }


    @Override
    protected void onStart() {
        super.onStart();
        // If the user disabled Bluetooth when the app was in the background,
        // openGattServer() will return null.
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_disconnect_devices) {
            mBluetoothLeService.disconnectFromDevices();
            return true /* event_consumed */;
        }
        return false /* event_consumed */;
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mBluetoothLeService.disconnectFromDevices();
    }


    private void resetStatusViews() {
        mStatusText.setText(R.string.status_notAdvertising);
    }


}