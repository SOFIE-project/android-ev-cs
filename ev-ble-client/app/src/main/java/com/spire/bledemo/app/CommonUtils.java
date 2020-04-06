package com.spire.bledemo.app;

import android.os.SystemClock;
import android.util.Log;

import static android.content.ContentValues.TAG;

public class CommonUtils {

    //Timing
    private volatile long timer_start = 0;
    private volatile long timer_end = 0;


    public synchronized void startTimer() {
        timer_start = SystemClock.elapsedRealtime();
        timer_end = 0;
    }

    // Can be called multiple times to get lap times
    public synchronized void stopTimer() {
        timer_end = SystemClock.elapsedRealtime();
        writeLine("Timer: " + (timer_end - timer_start) + " ms.");
        timer_start = timer_end;
        timer_end = 0;
    }

    public void writeLine(final String text) {

        String normalizedText;
        if (text.length() > 100) {
            normalizedText = text.subSequence(0, 100) + "..." + text.length();
        } else {
            normalizedText = text;
        }

        Log.d(TAG, normalizedText);
    }
}
