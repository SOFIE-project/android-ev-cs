package com.spire.bledemo.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class TimeMeasurementsActivity extends Activity {


    private final String[] bleLabels = {"Find the CS BLE peripheral",
            "Establish Connection",
            "Discover Services",
            "Enable Notifcations on Rx",
            "Request MTU (517 bytes, android max)",
            "Our protocol begins",
            "Write stage progression (1 byte)",
            "Receive CS DID1 (? bytes)",
            "Write stage progression (1 byte)",
            "Write EV DID (? bytes)",
            "Write stage progression (1 byte)",
            "Receive Proof of CS ownership (? bytes)",
            "Write stage progression (1 byte)",
            "Write Proof of being ER customer (? bytes)",
            "Write stage progression (1 byte)",
    };

    private final String[] indyLabels = {
            "CSO Info + DSO district proof request",
            "CSO Info + DSO district proof",
            "Verifying proof for CSO Info + DSO district",
            "EV charging credential proof request",
            "EV charging credential proof",
            "Verifying proof for ER charging credential",
    };

    private Long[] timeList = {};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_time_measurements);

        ListView timeListView = findViewById(R.id.time_list_view);

        Intent intent = getIntent();
        timeList = (Long[]) intent.getSerializableExtra("timeList");

//        ArrayAdapter<Long> adapter = new ArrayAdapter<>(this,
//                android.R.layout.simple_list_item_1, timeList);

        TimeListAdapter adapter = new TimeListAdapter(bleLabels, timeList);
        timeListView.setAdapter(adapter);
    }

    class TimeListAdapter extends BaseAdapter {

        private String[] labels;
        private Long[] values;

        public TimeListAdapter(String[] labels, Long[] values) {
            this.labels = labels;
            this.values = values;
        }

        @Override
        public int getCount() {
            return bleLabels.length;
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            convertView = getLayoutInflater().inflate(R.layout.time_measurement_row, null);
            TextView event = convertView.findViewById(R.id.event_text);
            TextView time = convertView.findViewById(R.id.time_text);

            event.setText(labels[position]);
            time.setText(values[position].toString());
            return convertView;
        }
    }
}
