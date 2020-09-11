package fi.aalto.evchargingprotocol.framework;

import android.util.Log;

import org.bitcoinj.core.Base58;
import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;

import ring.Ring;


public class RingSignatureUtils {

    private byte[] signerSEED;
    public String ringDidList;

    public RingSignatureUtils(byte[] signerSEED, JSONArray ringDidList) {
        this.signerSEED = signerSEED;
        this.ringDidList = getPbKeyListString(ringDidList);
    }

    public String getPbKeyListString(JSONArray ringDidList) {
        ArrayList<String> pbKeyList = new ArrayList<>();
        try {
            for (int i = 0; i < ringDidList.length(); i++) {
                pbKeyList.add(Base58.encode(PeerDID.getVerkeyFromDID(ringDidList.getString(i))));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return String.join("|", pbKeyList);
    }

    public byte[] sign(byte[] data) {
        byte[] signature = null;
        try {
            signature = Ring.sign(data, this.signerSEED, this.ringDidList);
//            Log.i("Measure", signaturePlainText);
//            signature = signaturePlainText.getBytes();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return signature;
    }

    public boolean verify(String unused, byte[] data, byte[] signature) {
        try {
            return Ring.verify(data, signature, this.ringDidList);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }
}