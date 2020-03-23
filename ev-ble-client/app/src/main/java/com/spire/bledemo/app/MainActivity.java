package com.spire.bledemo.app;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelUuid;
import android.os.SystemClock;
import android.text.format.Time;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static android.content.ContentValues.TAG;

public class MainActivity extends Activity {

    // UUIDs for UAT service and associated characteristics.
    public static UUID UART_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    public static UUID TX_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");
    public static UUID RX_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");

    public static UUID CHARGING_SERVICE_UUID = UUID.fromString("D0940001-5FDC-478D-B700-029B574073AB");
    public static UUID CHARGING_STATE_UUID = UUID.fromString("D0940002-5FDC-478D-B700-029B574073AB");

    // UUID for the BTLE client characteristic which is necessary for notifications.
    public static UUID CLIENT_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    // UUID for writing user desrciption such as name
    private static final UUID CHARACTERISTIC_USER_DESCRIPTION_UUID = UUID
            .fromString("00002901-0000-1000-8000-00805f9b34fb");

    private List<ScanFilter> targetServices;

    private void createScanFilter() {
        targetServices = new ArrayList<>();
        ScanFilter filter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(CHARGING_SERVICE_UUID))
                .build();
        targetServices.add(filter);
    }

    private ScanSettings scanSettings = new ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build();

    // UI elements
    private TextView messages;
    private EditText input;
    private TextView mCsDetails;

    private static boolean appIsVisible = false;

    public static boolean isAppIsVisible() {
        return appIsVisible;
    }

    public static void setAppIsVisible(boolean appIsVisible) {
        MainActivity.appIsVisible = appIsVisible;
    }


    // BLE Operation Queue for serial operations
    private OperationManager commandQueue;
    private MessageBuffer mRxBuffer;

    // BTLE state
    private BluetoothAdapter adapter;
    private BluetoothGatt gatt;
    private BluetoothDevice peripheralDevice;
    private BluetoothGattCharacteristic chargingProtocolState;
    private BluetoothGattCharacteristic tx;
    private BluetoothGattCharacteristic rx;
    final private int mtuLength = 512;  // 512 on my phones, can't detect either


    //Timing
    private long timer_start = 0;
    private long timer_end = 0;

    private HandlerThread bleOperationHandlerThread = new HandlerThread("bleOperationHandlerThread");
    private Handler bleHandler;

    public void startTimer() {
        timer_start = SystemClock.elapsedRealtime();
        timer_end=0;
    }

    // Can be called multiple times to get lap times
    public void stopTimer() {
        timer_end = SystemClock.elapsedRealtime();
        writeLine("Timer: " + (timer_end-timer_start) + " ms.");
        //timer_start = timer_end;
        timer_end = 0;
    }

    private IndyService mIndyService;

    // Main BTLE device callback where much of the logic occurs.
    private BluetoothGattCallback callback = new BluetoothGattCallback() {
        // Called whenever the device connection state changes, i.e. from disconnected to connected.
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                writeLine("Connected!");
                // Discover services.
stopTimer();
                if (!gatt.discoverServices()) {
                    writeLine("Failed to start discovering services!");
                }


//                final BluetoothGatt staticGatt = gatt;
//                bleHandler.post(new Runnable() {
//                    @Override
//                    public void run() {
//                        staticGatt.discoverServices();
//                    }
//                });



            }

            else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                writeLine("Disconnected!");
                gatt.close();

                if(isAppIsVisible()) {
//                   adapter.getBluetoothLeScanner().startScan(targetServices, scanSettings, scanCallback);
                   gatt = peripheralDevice.connectGatt(getApplicationContext(), false, callback);
                }
            }
            else {
                writeLine("Connection state changed.  New state: " + newState);
            }
        }

        // Called when services have been discovered on the remote device.
        // It seems to be necessary to wait for this discovery to occur before
        // manipulating any services or characteristics.
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            try {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    writeLine("Service discovery completed!");
stopTimer();
                    // Save reference to each characteristic.
                    chargingProtocolState = gatt.getService(CHARGING_SERVICE_UUID).getCharacteristic(CHARGING_STATE_UUID);
                    tx = gatt.getService(CHARGING_SERVICE_UUID).getCharacteristic(TX_UUID);
                    rx = gatt.getService(CHARGING_SERVICE_UUID).getCharacteristic(RX_UUID);

                    // Setup notifications on RX characteristic changes (i.e. data received).
                    // First call setCharacteristicNotification to enable notification.
                    if (!gatt.setCharacteristicNotification(rx, true)) {
                        writeLine("Couldn't set notifications for RX characteristic!");
                    }

                    // Next update the RX characteristic's client descriptor to enable notifications.
                    if (rx.getDescriptor(CLIENT_UUID) != null) {
                        safeEnableNotification(rx);
                    } else {
                        writeLine("Couldn't get RX client descriptor!");
                    }

                } else {
                    writeLine("Service discovery failed with status: " + status);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
//            finally {
//                commandQueue.operationCompleted();
//            }
        }

        // Called when a remote characteristic changes (like the RX characteristic).
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            try {

                if(characteristic.getUuid().equals(RX_UUID)) {
                    mRxBuffer.add(characteristic.getStringValue(0));

                    if( characteristic.getStringValue(0).endsWith("ä")) {
                        String rxMessage = mRxBuffer.extract();
                        String arxMessage = rxMessage.substring(0, rxMessage.length()-1);
                        writeLine("wth: |" + arxMessage + "|");
                        stopTimer();
                        final int presetStage = chargingProtocolState.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8,0);
                        bleHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                handleEvents(RX_MSG_RECVD, presetStage);
                            }
                        });

                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }



        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);

            try {
                writeLine(characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8,0).toString());
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                commandQueue.operationCompleted();
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            try {
                if(characteristic.getUuid().equals(CHARGING_STATE_UUID)) {
                    Log.d(TAG, "onCharacteristicWrite: state written");
                    stopTimer();
                } else if(characteristic.getStringValue(0).endsWith("ä")) {
                    Log.d(TAG, "onCharacteristicWrite: long data written");
                    stopTimer();
                }


            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                commandQueue.operationCompleted();
            }
        }


        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            try {
                writeLine("descriptor written");
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                commandQueue.operationCompleted();
                if(CHARACTERISTIC_USER_DESCRIPTION_UUID.equals(descriptor.getUuid())) {
stopTimer();
                    handleEvents(CS_CONNECTED, 0);
                }
            }
        }

    };

    // BTLE device scanning callback.
    private ScanCallback scanCallback = new ScanCallback() {
        // Called when a device is found.
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            adapter.getBluetoothLeScanner().stopScan(scanCallback);
            writeLine("Barrier found!");
            stopTimer();
            peripheralDevice = result.getDevice();
            gatt = peripheralDevice.connectGatt(getApplicationContext(), false, callback);
        }
    };


    // OnCreate, called once to initialize the activity.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Grab references to UI elements.
        messages = (TextView) findViewById(R.id.messages);
        input = (EditText) findViewById(R.id.input);
        mCsDetails = findViewById(R.id.csDetails);

        adapter = BluetoothAdapter.getDefaultAdapter();
        commandQueue = new OperationManager();
        mRxBuffer = new MessageBuffer(mtuLength);
        createScanFilter();
        //refreshDeviceCache(gatt);

        bleOperationHandlerThread.start();
        bleHandler = new Handler(bleOperationHandlerThread.getLooper());

        mIndyService = new IndyService();

    }

    // OnResume, called right before UI is displayed.  Start the BTLE connection.
    @Override
    protected void onResume() {
        super.onResume();
        // Scan for all BTLE devices.
        // The first one with the UART service will be chosen--see the code in the scanCallback.
        writeLine("Scanning for devices...");
        if(gatt != null) {
            gatt.close();
            gatt = null;
        }
        startTimer();
        adapter.getBluetoothLeScanner().startScan(targetServices, scanSettings, scanCallback);
        setAppIsVisible(true);
    }



    @Override
    protected void onPause() {
        super.onPause();
        setAppIsVisible(false);
    }


    // OnStop, called right before the activity loses foreground focus.  Close the BTLE connection.
    @Override
    protected void onStop() {
        super.onStop();
        if (gatt != null) {
            // For better reliability be careful to disconnect and close the connection.
            gatt.disconnect();
            //gatt.close();
            gatt = null;
            tx = null;
            rx = null;
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        bleOperationHandlerThread.quitSafely();
    }

    // Handler for mouse click on the send button.
    public void sendClick(View view) {
        String message = input.getText().toString();
        if (tx == null || message == null || message.isEmpty()) {
            //on empty, try to write request to the screen
            //sendUseCaseViaBLE();
            // Do nothing if there is no device or message to send.
            return;
        }
        else if (message.contains("rtc")) {
            sendDateTime();
            return;
        }
        // Update TX characteristic value.  Note the setValue overload that takes a byte array must be used.
        tx.setValue(message.getBytes(Charset.forName("UTF-8")));
        if (gatt.writeCharacteristic(tx)) {
            writeLine("Sent: kkkj " + message);
        }
        else {
            writeLine("Couldn't write TX characteristic!");
        }
    }

    public void sendDID( ) {
        String  message = mIndyService.createEVdid();
        longWriteCharacteristic(tx, message);
    }



    public void nextStage( int stage ) {


        if (safeWriteCharacteristic(chargingProtocolState, stage)) {
            writeLine("Sent: " + stage);
        }
        else {
            writeLine("Couldn't write stage to stage characteristic! " + stage);
        }
    }



    //Sends the proof  from file via BLE
    private void sendProof() {
        mIndyService.createProof();

        try {
            InputStream iS = getAssets().open("proof_example.json");
            BufferedReader reader = new BufferedReader(new InputStreamReader(iS));

            StringBuffer sb = new StringBuffer();

            String line;
            while((line = reader.readLine()) != null) {
                sb.append(line);
            }

            longWriteCharacteristic(tx, sb.toString());

            reader.close();
            iS.close();
        }
        catch (IOException ex) {
            writeLine("Unable to open asset.");
            return;
        }
    }


    //New: for syncing time: current barrier implementation RTC is not battery operated
    private void sendDateTime() {

        Time now = new Time();
        now.setToNow();
        String line = "(CTRL:SET_RTC(" + now.format3339(false) + "D" + now.weekDay+ "))";

        //There is bit of copy pasting here, portion below should be separated into it's own function,see sendUseCaseViaBLE()


    }


    // Write some text to the messages text view.
    // Care is taken to do this on the main UI thread so writeLine can be called
    // from any thread (like the BTLE callback).
    private void writeLine(final String text) {

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


//    public boolean safeReadCharacteristic(final BluetoothGattCharacteristic bleCharacteristic) {
//        if (gatt == null || bleCharacteristic == null) {
//            return false;
//        }
//        commandQueue.request(new Runnable() {
//            @Override
//            public void run() {
//                gatt.readCharacteristic(bleCharacteristic);
//            }
//        });
//
//        return true;
//    }

    public boolean safeWriteCharacteristic(final BluetoothGattCharacteristic bleCharacteristic, final int number) {
        if (gatt == null || bleCharacteristic == null) {
            return false;
        }
        commandQueue.request(new Runnable() {
            @Override
            public void run() {
                bleCharacteristic.setValue(number, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                gatt.writeCharacteristic(bleCharacteristic);
            }
        });

        return true;
    }


    public boolean safeWriteCharacteristic(final BluetoothGattCharacteristic bleCharacteristic, final String payload) {
        if (gatt == null || bleCharacteristic == null) {
            return false;
        }
        commandQueue.request(new Runnable() {
            @Override
            public void run() {
                bleCharacteristic.setValue(payload);
                gatt.writeCharacteristic(bleCharacteristic);
            }
        });

        return true;
    }

//    public byte[] delimitLongData(byte[] data) {
//        ByteArrayOutputStream delimitedStream = null;
//        try {
//            delimitedStream = new ByteArrayOutputStream();
//            delimitedStream.write(data);
//            delimitedStream.write("ä".getBytes());
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        return delimitedStream.toByteArray();
//    }

    public void longWriteCharacteristic(BluetoothGattCharacteristic bleCharacteristic, String payload) {
        int start = 0;
        payload = payload + "ä";

        writeLine("Sent: " + payload);

        while (start < payload.length()) {
            int end = Math.min(payload.length(), start + mtuLength);
            String chunk = payload.substring(start,end);

            safeWriteCharacteristic(bleCharacteristic, chunk);
            start += mtuLength;
        }

    }

    public boolean safeEnableNotification(final BluetoothGattCharacteristic bleCharacteristic) {
        if (gatt == null || bleCharacteristic == null) {
            return false;
        }
        commandQueue.request(new Runnable() {
            @Override
            public void run() {
                //  update the characteristic's client descriptor to enable notifications.
                    BluetoothGattDescriptor desc = bleCharacteristic.getDescriptor(CLIENT_UUID);
                    desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    gatt.writeDescriptor(desc);
            }
        });


        commandQueue.request(new Runnable() {
            @Override
            public void run() {
                //  update the characteristic's client descriptor to write client name.
                BluetoothGattDescriptor desc = bleCharacteristic.getDescriptor(CHARACTERISTIC_USER_DESCRIPTION_UUID);
                try {
                    desc.setValue("EV_USER_1".getBytes("UTF-8"));
                    gatt.writeDescriptor(desc);
                } catch (Exception e){
                    e.printStackTrace();
                }
            }
        });

        return true;
    }

//    public  BluetoothGattDescriptor getCharacteristicUserDescriptionDescriptor() {
//        BluetoothGattDescriptor descriptor = new BluetoothGattDescriptor(
//                CLIENT_UUID,
//                (BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE));
//        try {
//            descriptor.setValue("EV_USER_1".getBytes("UTF-8"));
//        } finally {
//            return descriptor;
//        }
//    }

    private void refreshDeviceCache(BluetoothGatt bluetoothGatt) {
        try {
            Method hiddenClearCacheMethod = bluetoothGatt.getClass().getMethod("refresh");
            if (hiddenClearCacheMethod != null) {
                hiddenClearCacheMethod.invoke(bluetoothGatt);
            }
        } catch (Exception ignored) {
            Log.e("cache", "could not clear cache");
        }
    }

    public static final String CS_CONNECTED = "CS_CONNECTED";
    public static final String RX_MSG_RECVD = "RX_MSG_RECVD";


    public void handleEvents (String event, int stage) {
        if(CS_CONNECTED.equals(event)) {
            mCsDetails.setText(" Status: Connected \n CS Name: "+ peripheralDevice.getName() +"\n MAC: "+ peripheralDevice.getAddress() +" \n");
            ((RelativeLayout)mCsDetails.getParent()).setBackgroundColor(getResources().getColor(android.R.color.holo_green_light));
            nextStage(0);
        } else if (RX_MSG_RECVD.equals(event) && stage == 0) {
            mIndyService.parseCsDid1(rx.getStringValue(0));
            nextStage(1);
            sendDID();
            nextStage(2);
        } else if (RX_MSG_RECVD.equals(event) && stage == 2) {
            mIndyService.parseCSdid2AndCSOownershipProof(rx.getStringValue(0));
            nextStage(3);
            sendProof();
            nextStage(4);
        }
    }

}
