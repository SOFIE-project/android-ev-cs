package io.github.webbluetoothcg.bletestperipheral;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

public class TransactionDetailsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaction_details);

        Intent intent = getIntent();
        TextView textView = findViewById(R.id.transaction_view);
        textView.setText(intent.getStringExtra("details"));
    }
}
