package io.github.webbluetoothcg.bletestperipheral;

import android.os.SystemClock;
import android.util.Log;

import java.util.ArrayList;

import static android.content.ContentValues.TAG;

public class CommonUtils {

    //Timing
    private volatile long timer_start = 0;
    private volatile long timer_end = 0;

    private ArrayList<String> timeList = new ArrayList<>();

    private String tag;

    public CommonUtils(String t) {
        tag = t;
    }

    public synchronized void startTimer() {
        timer_start = SystemClock.elapsedRealtime();
        timer_end = 0;
    }

    // Can be called multiple times to get lap times
    public synchronized void stopTimer() {
        timer_end = SystemClock.elapsedRealtime();
        long timeDifference = timer_end - timer_start;
        timeList.add(timeDifference + "");
       // writeLine("Timer: " + timeDifference + " ms.");
        timer_start = timer_end;
        timer_end = 0;
    }

    public void writeLine(final String text) {

        String normalizedText;
        if (text.length() > 100) {
            normalizedText = tag + " " + text.subSequence(0, 100) + "..." + text.length();
        } else {
            normalizedText = tag + " " + text;
        }
        timeList.add(normalizedText);
        //Log.i(TAG, normalizedText);
    }

    public String[] getTimeList() {
        return timeList.toArray(new String[timeList.size()]);
    }

    public void clearTimeList() {
        timeList.clear();
    }
}
