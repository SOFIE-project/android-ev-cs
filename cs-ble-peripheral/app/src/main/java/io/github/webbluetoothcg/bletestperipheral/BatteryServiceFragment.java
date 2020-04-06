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
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.UUID;

import static android.content.ContentValues.TAG;


public class BatteryServiceFragment extends ServiceFragment {


  private static final int INITIAL_BATTERY_LEVEL = 0;
  private static final int BATTERY_LEVEL_MAX = 100;
  private static final String BATTERY_LEVEL_DESCRIPTION = "The current charge level of a " +
      "battery. 100% represents fully charged while 0% represents fully discharged.";


  // UUID for advertising charging service
  public static UUID CHARGING_SERVICE_UUID = UUID.fromString("D0940001-5FDC-478D-B700-029B574073AB");
  public static UUID CHARGING_STATE_UUID =  UUID.fromString("D0940002-5FDC-478D-B700-029B574073AB");

  // UART characteristics
  public static UUID TX_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");
  public static UUID RX_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");


  private ServiceFragmentDelegate mDelegate;
  // UI
  private TextView mBatteryLevelText;
  private TextView mMessages;
  private ScrollView mScrollView;
  private TextView mHandshakeState;

  private int mtuLength = 510;
  private MessageBuffer mTxBuffer;
  private MessageBuffer mRxBuffer;

  private SeekBar mBatteryLevelSeekBar;

  private CommonUtils mCommonUtils;

  private final OnSeekBarChangeListener mOnSeekBarChangeListener = new OnSeekBarChangeListener() {
    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
      if (fromUser) {
        setBatteryLevel(progress, seekBar);
      }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }
  };


  // GATT
  private BluetoothGattService mBatteryService;
  private BluetoothGattCharacteristic mBatteryLevelCharacteristic;
  private BluetoothGattCharacteristic mRXCharacteristics;
  private BluetoothGattCharacteristic mTXCharacteristics;

  private IndyService mIndyService;

  public BatteryServiceFragment() {

    mBatteryLevelCharacteristic =
            new BluetoothGattCharacteristic(CHARGING_STATE_UUID,
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                    BluetoothGattCharacteristic.PERMISSION_WRITE);
    mBatteryLevelCharacteristic.addDescriptor(
            Peripheral.getClientCharacteristicConfigurationDescriptor());
    mBatteryLevelCharacteristic.addDescriptor(
            Peripheral.getCharacteristicUserDescriptionDescriptor(BATTERY_LEVEL_DESCRIPTION));


//MYCODE

    mRXCharacteristics =
            new BluetoothGattCharacteristic(RX_UUID,
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                    BluetoothGattCharacteristic.PERMISSION_READ);
    mRXCharacteristics.addDescriptor(
            Peripheral.getClientCharacteristicConfigurationDescriptor());
    mRXCharacteristics.addDescriptor(
            Peripheral.getCharacteristicUserDescriptionDescriptor(BATTERY_LEVEL_DESCRIPTION));


    mTXCharacteristics =
            new BluetoothGattCharacteristic(TX_UUID,
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                    BluetoothGattCharacteristic.PERMISSION_WRITE);
    mTXCharacteristics.addDescriptor(
            Peripheral.getClientCharacteristicConfigurationDescriptor());
    mTXCharacteristics.addDescriptor(
            Peripheral.getCharacteristicUserDescriptionDescriptor(BATTERY_LEVEL_DESCRIPTION));
//MYCODE ENDS

    mBatteryService = new BluetoothGattService(CHARGING_SERVICE_UUID,
        BluetoothGattService.SERVICE_TYPE_PRIMARY);
    mBatteryService.addCharacteristic(mBatteryLevelCharacteristic);
    mBatteryService.addCharacteristic(mRXCharacteristics);
    mBatteryService.addCharacteristic(mTXCharacteristics);
  }


  // Lifecycle callbacks
  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
      Bundle savedInstanceState) {

    View view = inflater.inflate(R.layout.fragment_battery, container, false);

    mBatteryLevelText = (TextView) view.findViewById(R.id.textView_batteryLevel);
    mBatteryLevelSeekBar = (SeekBar) view.findViewById(R.id.seekBar_batteryLevel);
    mBatteryLevelSeekBar.setOnSeekBarChangeListener(mOnSeekBarChangeListener);

    mMessages = (TextView) view.findViewById(R.id.messages);

    mHandshakeState = (TextView) view.findViewById(R.id.handshake_status);
    mScrollView = (ScrollView) view.findViewById(R.id.scroll_view);

    setBatteryLevel(INITIAL_BATTERY_LEVEL, null);

    return view;
  }

  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);
    try {
      mDelegate = (ServiceFragmentDelegate) activity;
      mTxBuffer = new MessageBuffer(mtuLength);
      mRxBuffer = new MessageBuffer(mtuLength);

      mIndyService = new IndyService(getContext());
      mCommonUtils = new CommonUtils();

    } catch (ClassCastException e) {
      throw new ClassCastException(activity.toString()
          + " must implement ServiceFragmentDelegate");
    }
  }

  @Override
  public void onDetach() {
    super.onDetach();
    mDelegate = null;
  }

  public BluetoothGattService getBluetoothGattService() {
    return mBatteryService;
  }

  @Override
  public ParcelUuid getServiceUUID() {
    return new ParcelUuid(CHARGING_SERVICE_UUID);
  }

  private void setBatteryLevel(int newBatteryLevel, View source) {
    mBatteryLevelCharacteristic.setValue(newBatteryLevel,
        BluetoothGattCharacteristic.FORMAT_UINT8, /* offset */ 0);
    if (source != mBatteryLevelSeekBar) {
      mBatteryLevelSeekBar.setProgress(newBatteryLevel);
    }
    if (source != mBatteryLevelText) {
      mBatteryLevelText.setText(Integer.toString(newBatteryLevel));
    }
  }

  @Override
  public void notificationsEnabled(BluetoothGattCharacteristic characteristic, boolean indicate) {
    if (!characteristic.getUuid().equals(CHARGING_STATE_UUID)) {
      return;
    }
    if (indicate) {
      return;
    }
    getActivity().runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(getActivity(), R.string.notificationsEnabled, Toast.LENGTH_SHORT)
            .show();
      }
    });
  }

  @Override
  public void notificationsDisabled(BluetoothGattCharacteristic characteristic) {
    if (!characteristic.getUuid().equals(CHARGING_STATE_UUID)) {
      return;
    }
    getActivity().runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(getActivity(), R.string.notificationsNotEnabled, Toast.LENGTH_SHORT)
            .show();
      }
    });
  }

  @Override
  public void notificationSent(BluetoothDevice device, int status) {
    getActivity().runOnUiThread(new Runnable() {
      @Override
      public void run() {
        String nextChunk = mRxBuffer.poll();

        if(nextChunk != null) {
          mRXCharacteristics.setValue(nextChunk);
          mDelegate.sendNotificationToDevices(mRXCharacteristics);
        } else {
          Log.v(TAG, "Finished sending did: " + System.currentTimeMillis());
        }
      }
    });

  }


  @Override
  public int writeCharacteristic(BluetoothGattCharacteristic characteristic, int offset, final byte[] value) {
    try {
      if (offset != 0) {
        return BluetoothGatt.GATT_INVALID_OFFSET;
      }

      characteristic.setValue(value);
      doPostCharacteristicWriteOperation(characteristic);

    } catch (Exception e) {
      e.printStackTrace();
    }
    return BluetoothGatt.GATT_SUCCESS;
  }


  public void doPostCharacteristicWriteOperation(BluetoothGattCharacteristic characteristic) {

     String postWriteMsg = null;

    // If Value written on state indicator
    if (characteristic.getUuid().equals(CHARGING_STATE_UUID)) {
      int newState = mBatteryLevelCharacteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
      postWriteMsg = "Stage: " + newState;
      handleEvent(newState);

    } else {
     // Value is written to TX characteristic
      mTxBuffer.add(mTXCharacteristics.getStringValue(0));

      if (mTXCharacteristics.getStringValue(0).endsWith("ä")) {
        String s = mTxBuffer.extract();
        postWriteMsg = s.substring(0,s.length()-1);
      }
    }

    if( postWriteMsg != null) {
      writeLine(postWriteMsg);
    }
  }

  public void handleEvent(int newState) {
    switch (newState) {
      case 0: // write did to rx
        String tempDID = mIndyService.generateTempDID1();
        Log.v(TAG, "Started sending did: " + System.currentTimeMillis());
        writeLongLocalCharacteristic(mRXCharacteristics, tempDID);
        break;

      case 1: // verify the proof request validity or throw error, reset state and disconnect client
        mIndyService.checkProofRequest();
        break;

      case 2: // write real DID
        String tempDID2 = mIndyService.generateDID2erProofAndProofRequestForEV();
        Log.v(TAG, "Started real did: " + System.currentTimeMillis());
        writeLongLocalCharacteristic(mRXCharacteristics, tempDID2);
        break;

      case 3: // verify EV customer proof
        mIndyService.checkProofRequest();
        break;

      case 4: // print connection complete
        mHandshakeState.setText("Handshake Complete");
        mHandshakeState.setBackgroundColor(getResources().getColor(R.color.accent));
        break;
    }
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
        getActivity().runOnUiThread(new Runnable() {
          @Override
          public void run() {
            String normalizedText;
            if (text.length() > 100) {
              normalizedText = text.subSequence(0, 100) + "..." + text.length();
            } else {
              normalizedText = text;
            }
            mMessages.append(normalizedText);
            mMessages.append("\n");
            final ScrollView sv = (ScrollView) mMessages.getParent();
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

//  public byte[] delimitLongData(byte[] data) {
//    ByteArrayOutputStream delimitedStream = null;
//    try {
//      delimitedStream = new ByteArrayOutputStream();
//      delimitedStream.write(data);
//      delimitedStream.write("ä".getBytes());
//    } catch (IOException e) {
//      e.printStackTrace();
//    }
//    return delimitedStream.toByteArray();
//  }

//  public void writeLongLocalCharacteristic(BluetoothGattCharacteristic bleCharacteristic, byte[] data) {
//    int start = 0;
//    byte[] payload = delimitLongData(data);
//
//    String a = "asdsdsd";
//    a = a + "ä";
//
//    while (start < payload.length) {
//      int end = Math.min(payload.length, start + mtuLength);
//      byte[] chunk = Arrays.copyOfRange(payload,start,end);
//      mRxBuffer.add(chunk);
//      start += mtuLength;
//    }
//
//    bleCharacteristic.setValue(mRxBuffer.poll());
//    mDelegate.sendNotificationToDevices(bleCharacteristic);
//  }

  public void writeLongLocalCharacteristic(BluetoothGattCharacteristic bleCharacteristic, String payload) {
    int start = 0;
    payload = payload + "ä";

    while (start < payload.length()) {
      int end = Math.min(payload.length(), start + mtuLength);
      String chunk = payload.substring(start,end);
      mRxBuffer.add(chunk);
      start += mtuLength;
    }

    bleCharacteristic.setValue(mRxBuffer.poll());
    mDelegate.sendNotificationToDevices(bleCharacteristic);
  }
}
