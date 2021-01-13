package fi.aalto.evchargingprotocol.framework;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Base64;

import io.github.webbluetoothcg.bletestperipheral.MainActivity;

public class PresentationUtils {

    public static final String TAG = PresentationUtils.class.getName();

    private PresentationUtils() {}

    public static JSONObject generateEVPresentation(PeerDID evDID, JSONObject paymentCommitment) {
        try {
            JSONObject result = new JSONObject()
                    .put("@context", new JSONArray(new String[] {"https://www.w3.org/2018/credentials/v1", "https://www.w3.org/2018/credentials/examples/v1"}))
                    .put("id", "urn:uuid:13CB2439-CA8F-46FB-95B8-3F6F0642B9B8")
                    .put("type", new JSONArray(new String[] {"VerifiablePresentation"}))
                    .put("commitment", Base64.getEncoder().encodeToString(paymentCommitment.toString().getBytes()));

            SignatureUtils.addJcsEd25519Signature2020LinkedProof(result, evDID.getDID(), "1597155761", evDID::sign);
            return result;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public static boolean verifyEVPresentation(JSONObject presentation) {
        try {
            String evDID = presentation.getJSONObject("proof").getString("verificationMethod");
            return SignatureUtils.verifyJCSEd25519Signature2020LinkedDocument(presentation, (verificationDID, message, signature) ->
                    PeerDID.verifyFrom(evDID, message, signature)
            );
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public static JSONObject generateCSPresentation(PeerDID csDID, String evDID) {
        try {
            JSONObject result = new JSONObject()
                    .put("@context", new JSONArray(new String[] {"https://www.w3.org/2018/credentials/v1", "https://www.w3.org/2018/credentials/examples/v1"}))
                    .put("id", "urn:uuid:18E15106-E6DC-4EB5-8DEB-BFBFAC1C7A7A")
                    .put("type", new JSONArray(new String[] {"VerifiablePresentation"}))
                    .put("ev-did", evDID);

            SignatureUtils.addJcsEd25519Signature2020LinkedProof(result, csDID.getDID(), "1597155763", csDID::sign);
            return result;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public static JSONObject generateCSPresentation(PeerDID csDID, String evDID, JSONObject csInfoCredential) {
        try {
            JSONObject result = new JSONObject()
                    .put("@context", new JSONArray(new String[] {"https://www.w3.org/2018/credentials/v1", "https://www.w3.org/2018/credentials/examples/v1"}))
                    .put("id", "urn:uuid:18E15106-E6DC-4EB5-8DEB-BFBFAC1C7A7A")
                    .put("type", new JSONArray(new String[] {"VerifiablePresentation"}))
                    .put("ev-did", evDID);

            RingSignatureUtils ringSignature = new RingSignatureUtils(PeerDID.PeerEntity.CS.getSeed(), csInfoCredential.getJSONObject("credentialSubject").getJSONArray("id"));
            SignatureUtils.addJcsEd25519Signature2020LinkedProof(result, csInfoCredential.getString("id"), "1597155763", ringSignature::sign);
            return result;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public static boolean verifyCSPresentation(JSONObject presentation) {
        try {
            String csDID = presentation.getJSONObject("proof").getString("verificationMethod");
            return SignatureUtils.verifyJCSEd25519Signature2020LinkedDocument(presentation, (verificationDID, message, signature) ->
                    PeerDID.verifyFrom(csDID, message, signature)
            );
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public static boolean verifyCSPresentation(JSONObject presentation, JSONObject csInfoCredential) {
        try {
            String csCredentialId = presentation.getJSONObject("proof").getString("verificationMethod");
            if(!csInfoCredential.getString("id").equals(csCredentialId)) {
                return false;
            }

            RingSignatureUtils ringSignature = new RingSignatureUtils(null, csInfoCredential.getJSONObject("credentialSubject").getJSONArray("id"));

            return SignatureUtils.verifyJCSEd25519Signature2020LinkedDocument(presentation, ringSignature::verify);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }
}