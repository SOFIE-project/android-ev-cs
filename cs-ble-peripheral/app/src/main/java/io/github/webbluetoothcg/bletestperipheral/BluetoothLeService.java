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

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.Arrays;
import java.util.HashSet;
import java.util.UUID;

import static android.content.ContentValues.TAG;


public class BluetoothLeService extends Service {

    private static final int REQUEST_ENABLE_BT = 1;

    private static final int INITIAL_BATTERY_LEVEL = 0;
  private static final int BATTERY_LEVEL_MAX = 100;
  private static final String BATTERY_LEVEL_DESCRIPTION = "The current charge level of a " +
      "battery. 100% represents fully charged while 0% represents fully discharged.";


    private static final UUID CHARACTERISTIC_USER_DESCRIPTION_UUID = UUID
            .fromString("00002901-0000-1000-8000-00805f9b34fb");
    private static final UUID CLIENT_CHARACTERISTIC_CONFIGURATION_UUID = UUID
            .fromString("00002902-0000-1000-8000-00805f9b34fb");

    // UUID for advertising charging service
  public static UUID CHARGING_SERVICE_UUID = UUID.fromString("D0940001-5FDC-478D-B700-029B574073AB");
  public static UUID CHARGING_STATE_UUID =  UUID.fromString("D0940002-5FDC-478D-B700-029B574073AB");

  // UART characteristics
  public static UUID TX_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");
  public static UUID RX_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");


  private final int mtuLength = 510;
  private MessageBuffer mTxBuffer;
  private MessageBuffer mRxBuffer;

  private CommonUtils mCommonUtils;


    // Event Constants

    public static final String EV_CONNECTED = "io.github.webbluetoothcg.bletestperipheral.EV_CONNECTED";
    public static final String TX_MSG_RECVD = "io.github.webbluetoothcg.bletestperipheral.TX_MSG_RECVD";
    public static final String BROADCASTING = "io.github.webbluetoothcg.bletestperipheral.BROADCASTING";
    public static final String BLE_ERROR = "io.github.webbluetoothcg.bletestperipheral.BLE_ERROR";

    public final static String EXTRA_DATA =
            "io.github.webbluetoothcg.bletestperipheral.EXTRA_DATA";
    public final static String CURRENT_STAGE =
            "io.github.webbluetoothcg.bletestperipheral.CURRENT_STAGE";



    // BLE gatt server
  private BluetoothGattService mBluetoothGattService;
    private HashSet<BluetoothDevice> mBluetoothDevices;
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private AdvertiseData mAdvData;
    private AdvertiseData mAdvScanResponse;
    private AdvertiseSettings mAdvSettings;
    private BluetoothLeAdvertiser mAdvertiser;
    private BluetoothGattServer mGattServer;


    // Service lifecycle
    private final IBinder mBinder = new LocalBinder();


    private final AdvertiseCallback mAdvCallback = new AdvertiseCallback() {
        @Override
        public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);
            Log.e(TAG, "Not broadcasting: " + errorCode);
            int statusText;
            switch (errorCode) {
                case ADVERTISE_FAILED_ALREADY_STARTED:
                    statusText = R.string.status_advertising;
                    Log.w(TAG, "App was already advertising");
                    break;
                case ADVERTISE_FAILED_DATA_TOO_LARGE:
                    statusText = R.string.status_advDataTooLarge;
                    break;
                case ADVERTISE_FAILED_FEATURE_UNSUPPORTED:
                    statusText = R.string.status_advFeatureUnsupported;
                    break;
                case ADVERTISE_FAILED_INTERNAL_ERROR:
                    statusText = R.string.status_advInternalError;
                    break;
                case ADVERTISE_FAILED_TOO_MANY_ADVERTISERS:
                    statusText = R.string.status_advTooManyAdvertisers;
                    break;
                default:
                    statusText = R.string.status_notAdvertising;
                    Log.wtf(TAG, "Unhandled error: " + errorCode);
            }
            broadcastUpdate(BLE_ERROR, null,null);
        }

        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
            Log.v(TAG, "Broadcasting");
            broadcastUpdate(BROADCASTING, null, null);

        }
    };




    private void broadcastUpdate(final String action, Integer stage, String msg) {
        final Intent intent = new Intent(action);

        if(stage != null) {
            intent.putExtra(CURRENT_STAGE, stage);
        }

        if (msg != null) {
            intent.putExtra(EXTRA_DATA, msg);
        }

        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }


    private final BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {

        @Override
        public void onConnectionStateChange(BluetoothDevice device, final int status, int newState) {
            super.onConnectionStateChange(device, status, newState);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothGatt.STATE_CONNECTED) {

                    if (mBluetoothManager.getConnectedDevices(BluetoothGattServer.GATT).size() == 1) {
                        mBluetoothDevices.add(device);
                        broadcastUpdate(EV_CONNECTED, null, device.getAddress());
                        Log.v(TAG, "Connected to device: " + device.getAddress());
                    } else {
                        mGattServer.cancelConnection(device);
                        Log.v(TAG, "Connection logically refused to " + device.getAddress());
                    }
                } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    //mGattServer.cancelConnection(device);
                    mBluetoothDevices.remove(device);
                    broadcastUpdate(BROADCASTING, null, null);
                    Log.v(TAG, "Disconnected from device");
                }
            } else {
                mBluetoothDevices.remove(device);
                broadcastUpdate(BLE_ERROR, null, null);
                // There are too many gatt errors (some of them not even in the documentation) so we just
                // show the error to the user.
                final String errorMessage = getString(R.string.status_errorWhenConnecting) + ": " + status;
                Log.e(TAG, errorMessage);
            }
        }



        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset,
                                                BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
            Log.d(TAG, "Device tried to read characteristic: " + characteristic.getUuid());
            Log.d(TAG, "Value: " + Arrays.toString(characteristic.getValue()));
            if (offset != 0) {
                mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset,
                        /* value (optional) */ null);
                return;
            }
            mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS,
                    offset, characteristic.getValue());
        }

        @Override
        public void onNotificationSent(BluetoothDevice device, int status) {
            super.onNotificationSent(device, status);
            notificationSent(device, status);
            Log.v(TAG, "Notification sent. Status: " + status);
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
                                                 BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded,
                                                 int offset, byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite,
                    responseNeeded, offset, value);
            Log.v(TAG, "Characteristic Write request: " + Arrays.toString(value));
            int status = writeCharacteristic(characteristic, offset, value);
            if (responseNeeded) {
                mGattServer.sendResponse(device, requestId, status,
                        /* No need to respond with an offset */ 0,
                        /* No need to respond with a value */ null);
            }
        }

        @Override
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId,
                                            int offset, BluetoothGattDescriptor descriptor) {
            super.onDescriptorReadRequest(device, requestId, offset, descriptor);
            Log.d(TAG, "Device tried to read descriptor: " + descriptor.getUuid());
            Log.d(TAG, "Value: " + Arrays.toString(descriptor.getValue()));
            if (offset != 0) {
                mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset,
                        /* value (optional) */ null);
                return;
            }
            mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset,
                    descriptor.getValue());
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId,
                                             BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded,
                                             int offset,
                                             byte[] value) {
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded,
                    offset, value);
            Log.v(TAG, "Descriptor Write Request " + descriptor.getUuid() + " " + Arrays.toString(value));
            int status = BluetoothGatt.GATT_SUCCESS;
            if (descriptor.getUuid() == CLIENT_CHARACTERISTIC_CONFIGURATION_UUID) {
                BluetoothGattCharacteristic characteristic = descriptor.getCharacteristic();
                boolean supportsNotifications = (characteristic.getProperties() &
                        BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0;
                boolean supportsIndications = (characteristic.getProperties() &
                        BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0;

                if (!(supportsNotifications || supportsIndications)) {
                    status = BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED;
                } else if (value.length != 2) {
                    status = BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH;
                } else if (Arrays.equals(value, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {
                    status = BluetoothGatt.GATT_SUCCESS;
//                    mCurrentServiceFragment.notificationsDisabled(characteristic);
                    descriptor.setValue(value);
                } else if (supportsNotifications &&
                        Arrays.equals(value, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                    status = BluetoothGatt.GATT_SUCCESS;
//                    mCurrentServiceFragment.notificationsEnabled(characteristic, false /* indicate */);
                    descriptor.setValue(value);
                } else if (supportsIndications &&
                        Arrays.equals(value, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)) {
                    status = BluetoothGatt.GATT_SUCCESS;
//                    mCurrentServiceFragment.notificationsEnabled(characteristic, true /* indicate */);
                    descriptor.setValue(value);
                } else {
                    status = BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED;
                }
            } else {
                status = BluetoothGatt.GATT_SUCCESS;
                descriptor.setValue(value);
            }
            if (responseNeeded) {
                mGattServer.sendResponse(device, requestId, status,
                        /* No need to respond with offset */ 0,
                        /* No need to respond with a value */ null);
            }

        }
    };



  // GATT
  private BluetoothGattService mBatteryService;
  private BluetoothGattCharacteristic mChargingStateCharacteristic;
  private BluetoothGattCharacteristic mRXCharacteristics;
  private BluetoothGattCharacteristic mTXCharacteristics;


  public BluetoothLeService() {

    mChargingStateCharacteristic =
            new BluetoothGattCharacteristic(CHARGING_STATE_UUID,
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                    BluetoothGattCharacteristic.PERMISSION_WRITE);
    mChargingStateCharacteristic.addDescriptor(
            getClientCharacteristicConfigurationDescriptor());
    mChargingStateCharacteristic.addDescriptor(
            getCharacteristicUserDescriptionDescriptor(BATTERY_LEVEL_DESCRIPTION));



    mRXCharacteristics =
            new BluetoothGattCharacteristic(RX_UUID,
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                    BluetoothGattCharacteristic.PERMISSION_READ);
    mRXCharacteristics.addDescriptor(
            getClientCharacteristicConfigurationDescriptor());
    mRXCharacteristics.addDescriptor(
            getCharacteristicUserDescriptionDescriptor(BATTERY_LEVEL_DESCRIPTION));


    mTXCharacteristics =
            new BluetoothGattCharacteristic(TX_UUID,
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                    BluetoothGattCharacteristic.PERMISSION_WRITE);
    mTXCharacteristics.addDescriptor(
            getClientCharacteristicConfigurationDescriptor());
    mTXCharacteristics.addDescriptor(
            getCharacteristicUserDescriptionDescriptor(BATTERY_LEVEL_DESCRIPTION));



    mBatteryService = new BluetoothGattService(CHARGING_SERVICE_UUID,
        BluetoothGattService.SERVICE_TYPE_PRIMARY);
    mBatteryService.addCharacteristic(mChargingStateCharacteristic);
    mBatteryService.addCharacteristic(mRXCharacteristics);
    mBatteryService.addCharacteristic(mTXCharacteristics);
  }


  // Lifecycle callbacks


  public void notificationSent(BluetoothDevice device, int status) {
        byte[] nextChunk = mRxBuffer.poll();

        if(nextChunk != null) {
          mRXCharacteristics.setValue(nextChunk);
          sendNotificationToDevices(mRXCharacteristics);
        } else {
          Log.v(TAG, "Finished sending did: " + System.currentTimeMillis());
        }
  }


  public int writeCharacteristic(BluetoothGattCharacteristic characteristic, int offset, final byte[] value) {
    try {
      if (offset != 0) {
        return BluetoothGatt.GATT_INVALID_OFFSET;
      }

      characteristic.setValue(value);

        // If Value written on state indicator
        if (characteristic.getUuid().equals(CHARGING_STATE_UUID)) {
            int newState = mChargingStateCharacteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
            mCommonUtils.writeLine("Stage: "+ newState);
            broadcastUpdate(TX_MSG_RECVD, newState, null);

        } else if (characteristic.getUuid().equals(TX_UUID)){
            // Value is written to TX characteristic
            mTxBuffer.add(mTXCharacteristics.getValue());
        }

    } catch (Exception e) {
      e.printStackTrace();
    }
    return BluetoothGatt.GATT_SUCCESS;
  }


  public byte[] getBLEMessage() {
    byte[] bleMessage = mTxBuffer.extract();
    return bleMessage;
  }


    public void sendNotificationToDevices(BluetoothGattCharacteristic characteristic) {
        boolean indicate = (characteristic.getProperties()
                & BluetoothGattCharacteristic.PROPERTY_INDICATE)
                == BluetoothGattCharacteristic.PROPERTY_INDICATE;
        for (BluetoothDevice device : mBluetoothDevices) {
            // true for indication (acknowledge) and false for notification (unacknowledge).
            mGattServer.notifyCharacteristicChanged(device, characteristic, indicate);
        }
    }

  public void writeLongLocalCharacteristic(byte[] payload) {
    int start = 0;

    while (start < payload.length) {
      int end = Math.min(payload.length, start + mtuLength);
      byte[] chunk = Arrays.copyOfRange(payload,start,end);
      mRxBuffer.add(chunk);
      start += mtuLength;
    }

    mRXCharacteristics.setValue(mRxBuffer.poll());
    sendNotificationToDevices(mRXCharacteristics);
  }


  public void initialize() {
      mTxBuffer = new MessageBuffer(mtuLength);
      mRxBuffer = new MessageBuffer(mtuLength);

      mCommonUtils = new CommonUtils();


      mBluetoothDevices = new HashSet<>();
      mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
      mBluetoothAdapter = mBluetoothManager.getAdapter();

      mAdvSettings = new AdvertiseSettings.Builder()
              .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
              .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
              .setConnectable(true)
              .build();
      mAdvData = new AdvertiseData.Builder()
              .addServiceUuid(getServiceUUID())
              .build();

      mAdvScanResponse = new AdvertiseData.Builder()
              .setIncludeDeviceName(true)
              .build();
  }

    public ParcelUuid getServiceUUID() {
        return new ParcelUuid(CHARGING_SERVICE_UUID);
    }

  public void startGattServer() {
      mGattServer = mBluetoothManager.openGattServer(this, mGattServerCallback);

      // Add a service for a total of three services (Generic Attribute and Generic Access
      // are present by default).
      mGattServer.addService(mBatteryService);

      if (mBluetoothAdapter.isMultipleAdvertisementSupported()) {
          mAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
          mAdvertiser.startAdvertising(mAdvSettings, mAdvData, mAdvScanResponse, mAdvCallback);
      } else {
          mCommonUtils.writeLine(getString(R.string.status_noLeAdv));
      }

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


    private void releaseResources() {
        if (mGattServer != null) {
            mGattServer.close();
        }
        if (mBluetoothAdapter.isEnabled() && mAdvertiser != null) {
            // If stopAdvertising() gets called before close() a null
            // pointer exception is raised.
            mAdvertiser.stopAdvertising(mAdvCallback);
        }
    }

    ///////////////////////
    ////// Bluetooth //////
    ///////////////////////
    public static BluetoothGattDescriptor getClientCharacteristicConfigurationDescriptor() {
        BluetoothGattDescriptor descriptor = new BluetoothGattDescriptor(
                CLIENT_CHARACTERISTIC_CONFIGURATION_UUID,
                (BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE));
        descriptor.setValue(new byte[]{0, 0});
        return descriptor;
    }

    public static BluetoothGattDescriptor getCharacteristicUserDescriptionDescriptor(String defaultValue) {
        BluetoothGattDescriptor descriptor = new BluetoothGattDescriptor(
                CHARACTERISTIC_USER_DESCRIPTION_UUID,
                (BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE));
        try {
            descriptor.setValue(defaultValue.getBytes("UTF-8"));
        } finally {
            return descriptor;
        }
    }


    public void disconnectFromDevices() {
        Log.d(TAG, "Disconnecting devices...");
        for (BluetoothDevice device : mBluetoothManager.getConnectedDevices(
                BluetoothGattServer.GATT)) {
            Log.d(TAG, "Devices: " + device.getAddress() + " " + device.getName());
            mGattServer.cancelConnection(device);
        }
    }
}
