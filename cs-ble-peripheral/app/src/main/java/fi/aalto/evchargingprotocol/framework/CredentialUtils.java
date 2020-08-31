package fi.aalto.evchargingprotocol.framework;

import org.json.JSONArray;
import org.json.JSONObject;

public class CredentialUtils {

    private CredentialUtils() {}

    public static JSONObject getCSInfoCredential(PeerDID csDID, IndyDID csoDID) {
        try {
            JSONObject result = new JSONObject()
                    .put("@context", new JSONArray(new String[]{"https://www.w3.org/2018/credentials/v1", "https://www.w3.org/2020/credentials/cs-info/v1"}))
                    .put("id", "https://www.w3.org/2020/credentials/cs-info")
                    .put("type", new JSONArray(new String[]{"VerifiableCredential", "CSInfoCredential"}))
                    .put("credentialSubject", new JSONObject().put("id", csDID.getDID()).put("district", "1"))
                    .put("issuer", csoDID.getDID())
                    .put("issuanceDate", "2018-03-12T07:10:31Z")                            // Can also be dynamically set, if needed
                    .put("expirationDate", "2024-12-31T23:59:59Z");                         // Can also be dynamically set, if needed

            SignatureUtils.addJcsEd25519Signature2020LinkedProof(result, csoDID.getDID(), csoDID::sign);
            return result;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public static JSONObject getEVChargingCredential(PeerDID evDID, IndyDID erDID) {
        try {
            JSONObject result = new JSONObject()
                    .put("@context", new JSONArray(new String[]{"https://www.w3.org/2018/credentials/v1", "https://www.w3.org/2020/credentials/cs-info/v1"}))
                    .put("id", "https://www.w3.org/2020/credentials/cs-info")
                    .put("type", new JSONArray(new String[]{"VerifiableCredential", "CSInfoCredential"}))
                    .put("credentialSubject", new JSONObject().put("id", evDID.getDID()).put("district", "1"))
                    .put("issuer", erDID.getDID())
                    .put("issuanceDate", "2018-03-12T07:10:31Z")                            // Can also be dynamically set, if needed
                    .put("expirationDate", "2024-12-31T23:59:59Z");                         // Can also be dynamically set, if needed

            SignatureUtils.addJcsEd25519Signature2020LinkedProof(result, erDID.getDID(), erDID::sign);
            return result;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    // We are not verifying the validity period of the credential, but it is definitely a negligible operation here.
    public static boolean verifyCSInfoCredential(JSONObject credential) {
        try {
            String csoDID = credential.getJSONObject("proof").getString("verificationMethod");
            return SignatureUtils.verifyJCSEd25519Signature2020LinkedDocument(credential, (signerDID, message, signature) ->
                    IndyDID.verifyFrom(csoDID, message, signature)
            );
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    // We are not verifying the validity period of the credential, but it is definitely a negligible operation here.
    public static boolean verifyEVChargingCredential(JSONObject credential) {
        try {
            String erDID = credential.getJSONObject("proof").getString("verificationMethod");
            return SignatureUtils.verifyJCSEd25519Signature2020LinkedDocument(credential, (signerDID, message, signature) ->
                    IndyDID.verifyFrom(erDID, message, signature)
            );
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }
}
