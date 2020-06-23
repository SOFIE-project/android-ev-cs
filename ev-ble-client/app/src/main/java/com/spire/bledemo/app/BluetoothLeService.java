package com.spire.bledemo.app;


import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static android.content.ContentValues.TAG;

public class BluetoothLeService extends Service {

    // UUIDs for UAT service and associated characteristics.
    private static UUID UART_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    private static UUID TX_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");
    private static UUID RX_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");

    private static UUID CHARGING_SERVICE_UUID = UUID.fromString("D0940001-5FDC-478D-B700-029B574073AB");
    private static UUID CHARGING_STATE_UUID = UUID.fromString("D0940002-5FDC-478D-B700-029B574073AB");

    // UUID for the BTLE client characteristic which is necessary for notifications.
    private static UUID CLIENT_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    // UUID for writing user desrciption such as name
    private static final UUID CHARACTERISTIC_USER_DESCRIPTION_UUID = UUID
            .fromString("00002901-0000-1000-8000-00805f9b34fb");


    // Event Constants
    public static final String CS_CONNECTED = "com.spire.bledemo.app.CS_CONNECTED";
    public static final String RX_MSG_RECVD = "com.spire.bledemo.app.RX_MSG_RECVD";
    public static final String BLE_ERROR = "com.spire.bledemo.app.BLE_ERROR";

    public final static String ACTION_GATT_CONNECTED =
            "com.spire.bledemo.app.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.spire.bledemo.app.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.spire.bledemo.app.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.spire.bledemo.app.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.spire.bledemo.app.le.EXTRA_DATA";
    public final static String CURRENT_STAGE =
            "com.spire.bledemo.app.le.CURRENT_STAGE";
    public final static String DEVICE_NAME =
            "com.spire.bledemo.app.le.DEVICE_NAME";
    public final static String DEVICE_ADDRESS =
            "com.spire.bledemo.app.le.DEVICE_ADDRESS";

    // Creating scanner settings for finding the CS service
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
//           .setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH)  // Required for using Nokia 8.1 as EV
            .build();

    private BluetoothLeScanner mbleScanner;

    // BLE Operation Queue for serial operations
    private OperationManager commandQueue;
    private MessageBuffer mRxBuffer;

    // Common functions for logging and time measurement
    private CommonUtils mCommonUtils;

    // BTLE state
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothDevice peripheralDevice;
    private BluetoothGattCharacteristic chargingProtocolState;
    private BluetoothGattCharacteristic tx;
    private BluetoothGattCharacteristic rx;
    final private int mtuLength = 510;  // 514 on my phones, can't detect either

    // Worker thread to unblock the ble callbacks on binder thread
    private HandlerThread bleOperationHandlerThread = new HandlerThread("bleOperationHandlerThread");
    private Handler bleHandler;


    // BTLE device scanning callback.
    private ScanCallback scanCallback = new ScanCallback() {
        // Called when a device is found.
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            mbleScanner.stopScan(scanCallback);
            mCommonUtils.writeLine("Barrier found!");
            mCommonUtils.stopTimer();
            peripheralDevice = result.getDevice();
            mBluetoothGatt = peripheralDevice.connectGatt(getApplicationContext(), false, callback, BluetoothDevice.TRANSPORT_LE);
                    //BluetoothDevice.PHY_LE_2M);
        }
    };


    // Main BTLE device callback where much of the logic occurs.
    private BluetoothGattCallback callback = new BluetoothGattCallback() {
        // Called whenever the device connection state changes, i.e. from disconnected to connected.


//        @Override
//        public void onPhyUpdate(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
//            super.onPhyUpdate(gatt, txPhy, rxPhy, status);
//            commandQueue.nudge();
//        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
            mCommonUtils.writeLine("mtu negotiated");
            mCommonUtils.stopTimer();
            commandQueue.operationCompleted();
            broadcastUpdate(CS_CONNECTED, 0, null);
//            mBluetoothGatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                mCommonUtils.writeLine("Connected!");
                // Discover services.
                mCommonUtils.stopTimer();


                bleHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        commandQueue.request(new Runnable() {
                            @Override
                            public void run() {
                                if (!mBluetoothGatt.discoverServices()) {
                                    mCommonUtils.writeLine("Failed to start discovering services!");
                                }
                            }
                        });

                    }
                });

            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                mCommonUtils.writeLine("Disconnected!");
                commandQueue.operationCompleted();
                releaseResources();
            } else {
                mCommonUtils.writeLine("Connection state changed.  New state: " + newState);
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
                    mCommonUtils.writeLine("Service discovery completed!");
                    mCommonUtils.stopTimer();

                    bleHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {

                            // Save reference to each characteristic.
                            chargingProtocolState = mBluetoothGatt.getService(CHARGING_SERVICE_UUID).getCharacteristic(CHARGING_STATE_UUID);
                            tx = mBluetoothGatt.getService(CHARGING_SERVICE_UUID).getCharacteristic(TX_UUID);
                            rx = mBluetoothGatt.getService(CHARGING_SERVICE_UUID).getCharacteristic(RX_UUID);

                            // Setup notifications on RX characteristic changes (i.e. data received).
                            // First call setCharacteristicNotification to enable notification.
                            if (!gatt.setCharacteristicNotification(rx, true)) {
                                mCommonUtils.writeLine("Couldn't set notifications for RX characteristic!");
                            }

                            // Next update the RX characteristic's client descriptor to enable notifications.
                            if (rx.getDescriptor(CLIENT_UUID) != null) {
                                safeEnableNotification(rx);
                            } else {
                                mCommonUtils.writeLine("Couldn't get RX client descriptor!");
                            }
                        }
                    },1);


                } else {
                    mCommonUtils.writeLine("Service discovery failed with status: " + status);
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.v(TAG, "Error Service discovery malfunctioning");
            } finally {
                commandQueue.operationCompleted();
            }
        }

        // Called when a remote characteristic changes (like the RX characteristic).
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            try {

                if (characteristic.getUuid().equals(RX_UUID)) {
                    mRxBuffer.add(characteristic.getValue());

                    if (characteristic.getValue().length < mtuLength) {

                        byte[] rxMessage = mRxBuffer.extract();
                        mCommonUtils.writeLine("recieved mesg from cs: " + rxMessage.length);
                        mCommonUtils.stopTimer();

                        int presetStage = chargingProtocolState.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                        broadcastUpdate(RX_MSG_RECVD, presetStage, rxMessage);
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
                mCommonUtils.writeLine(characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0).toString());
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
                if (characteristic.getUuid().equals(CHARGING_STATE_UUID)) {
                    Log.d(TAG, "onCharacteristicWrite: state written");
                    mCommonUtils.stopTimer();
                } else if (characteristic.getStringValue(0).endsWith("ä")) {
                    Log.d(TAG, "onCharacteristicWrite: long data written");
                    mCommonUtils.stopTimer();
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
                mCommonUtils.writeLine("descriptor written");
                mCommonUtils.stopTimer();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                commandQueue.operationCompleted();

//                if (CHARACTERISTIC_USER_DESCRIPTION_UUID.equals(descriptor.getUuid())) {
//
//                }
            }
        }

    };


    public boolean safeEnableNotification(final BluetoothGattCharacteristic bleCharacteristic) {

        if (mBluetoothGatt == null || bleCharacteristic == null) {
            return false;
        }

        // enable notification in server by default?
        commandQueue.request(new Runnable() {
            @Override
            public void run() {
                //  update the characteristic's client descriptor to enable notifications.
                BluetoothGattDescriptor desc = bleCharacteristic.getDescriptor(CLIENT_UUID);
                desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                mBluetoothGatt.writeDescriptor(desc);
            }
        });


        // does it matter time wise??
        commandQueue.request(new Runnable() {
            @Override
            public void run() {
                //  update the characteristic's client descriptor to write client name.
                BluetoothGattDescriptor desc = bleCharacteristic.getDescriptor(CHARACTERISTIC_USER_DESCRIPTION_UUID);
                try {
                    desc.setValue("EV_USER_1".getBytes("UTF-8"));
                    mBluetoothGatt.writeDescriptor(desc);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        commandQueue.request(new Runnable() {
            @Override
            public void run() {
                mBluetoothGatt.requestMtu(mtuLength + 7);
            }
        });

        return true;
    }

    public void longWriteCharacteristic(byte[] payload) {
        int start = 0;

        while (start < payload.length) {
            int end = Math.min(payload.length, start + mtuLength);
            byte[] chunk = Arrays.copyOfRange(payload,start,end);
            safeWriteCharacteristic(tx, chunk);
            start += mtuLength;
        }

    }


    public boolean safeWriteCharacteristic(final BluetoothGattCharacteristic bleCharacteristic, final int number) {
        if (mBluetoothGatt == null || bleCharacteristic == null) {
            return false;
        }
        commandQueue.request(new Runnable() {
            @Override
            public void run() {
                bleCharacteristic.setValue(number, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                mBluetoothGatt.writeCharacteristic(bleCharacteristic);
            }
        });

        return true;
    }


    public boolean safeWriteCharacteristic(final BluetoothGattCharacteristic bleCharacteristic, final byte[] payload) {
        if (mBluetoothGatt == null || bleCharacteristic == null) {
            return false;
        }
        commandQueue.request(new Runnable() {
            @Override
            public void run() {
                bleCharacteristic.setValue(payload);
                mBluetoothGatt.writeCharacteristic(bleCharacteristic);
            }
        });

        return true;
    }

    public boolean write(byte[] message) {
        longWriteCharacteristic(message);
        return true;
    }

    public boolean write(int number) {
        return safeWriteCharacteristic(chargingProtocolState, number);
    }

    public class LocalBinder extends Binder {
        BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }


    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, releaseResources() is
        // invoked when the UI is disconnected from the Service.
        releaseResources();
        return super.onUnbind(intent);
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void releaseResources() {
        bleOperationHandlerThread.quitSafely();

        if (mBluetoothGatt != null) {
            // For better reliability be careful to disconnect and close the connection.
            //mBluetoothGatt.disconnect();
            mBluetoothGatt.close();
        }

        mBluetoothGatt = null;
        tx = null;
        rx = null;
        chargingProtocolState = null;
    }

    public void cancelGattConnection() {
        Log.v(TAG, "cancel requested");
        commandQueue.request(new Runnable() {
            @Override
            public void run() {
                if(mBluetoothGatt != null) {
                    Log.v(TAG, "cancel called");
                    mBluetoothGatt.disconnect();
                }
            }
        });
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * Initializes a reference to the local Bluetooth mBluetoothAdapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        commandQueue = new OperationManager();
        mRxBuffer = new MessageBuffer(mtuLength);
        createScanFilter();

        mCommonUtils = new CommonUtils("BluetoothLeService");

        bleOperationHandlerThread.start();
        bleHandler = new Handler(bleOperationHandlerThread.getLooper());

        return true;
    }

    public void startScan() {
        if (mBluetoothGatt != null) {
            mBluetoothGatt.close();
            mBluetoothGatt = null;
        }
        mCommonUtils.startTimer();
        mbleScanner= mBluetoothAdapter.getBluetoothLeScanner();
        mbleScanner.startScan(targetServices, scanSettings, scanCallback);
    }

    private void broadcastUpdate(final String action, int stage, byte[] msg) {
        final Intent intent = new Intent(action);
        intent.putExtra(CURRENT_STAGE, stage);
        if (msg != null) {
            intent.putExtra(EXTRA_DATA, msg);
        }

        if (stage == 0) {
            intent.putExtra(DEVICE_NAME, peripheralDevice.getName());
            intent.putExtra(DEVICE_ADDRESS, peripheralDevice.getAddress());
        }

        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    public Long[] getTimeList() {
        return mCommonUtils.getTimeList();
    }

    public void clearTimeList() {
        mCommonUtils.clearTimeList();
    }



//-------------------------------------------------------------------------------------------------
// BLE service related code


}
