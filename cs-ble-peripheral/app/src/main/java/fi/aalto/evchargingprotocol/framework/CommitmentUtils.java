package fi.aalto.evchargingprotocol.framework;

import org.json.JSONException;
import org.json.JSONObject;

public class CommitmentUtils {

    private CommitmentUtils() {}

    public static JSONObject getEVCommitment(String csDID, String hashChainRoot, String hashChainAlgorithm) {
        try {
            return new JSONObject()
                    .put("cs-did", csDID)
                    .put("w0", hashChainRoot)
                    .put("alg", hashChainAlgorithm)
                    .put("n", 50)
                    .put("D", "2020-06-29T14:44:20Z")
                    .put("p", 0.20);
        } catch (JSONException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public static JSONObject getEVCommitmentForRingSignature(JSONObject csSignature, String hashChainRoot, String hashChainAlgorithm) {
        try {
            return new JSONObject()
                    .put("cs-signature", csSignature)
                    .put("w0", hashChainRoot)
                    .put("alg", hashChainAlgorithm)
                    .put("n", 50)
                    .put("D", "2020-06-29T14:44:20Z")
                    .put("p", 0.20);
        } catch (JSONException e) {
            throw new RuntimeException(e.getMessage());
        }
    }
}
