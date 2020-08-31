package fi.aalto.evchargingprotocol.framework;

import org.json.JSONException;
import org.json.JSONObject;

public class CommitmentUtils {

    private CommitmentUtils() {}

    public static JSONObject getEVCommitment(PeerDID csDID, String hashChainRoot, String hashChainAlgorithm) {
        try {
            return new JSONObject()
                    .put("cs-did", csDID.getDID())
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
