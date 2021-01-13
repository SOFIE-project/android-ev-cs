package fi.aalto.evchargingprotocol.framework;

import org.bitcoinj.core.Base58;
import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;

import ring.Ring;


public class RingSignatureUtils {

    private PeerDID signerDID;
    private JSONArray ringDidList;

    public RingSignatureUtils(PeerDID signerDID, JSONArray ringDidList) {
        this.signerDID = signerDID;
        this.ringDidList = ringDidList;
    }

    public String getPbKeyListString() {
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
            signature = Ring.sign(data, PeerDID.PeerEntity.CS.getSeed(), getPbKeyListString());
        } catch (Exception e) {
            e.printStackTrace();
        }

        return signature;
    }

    public boolean verify(String unused, byte[] data, byte[] signature) {
        try {
            return Ring.verify(data, signature, getPbKeyListString());
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }
}