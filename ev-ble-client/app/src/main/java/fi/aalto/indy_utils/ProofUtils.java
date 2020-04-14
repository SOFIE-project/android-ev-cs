package fi.aalto.indy_utils;

import org.hyperledger.indy.sdk.IndyException;
import org.hyperledger.indy.sdk.anoncreds.Anoncreds;
import org.hyperledger.indy.sdk.wallet.Wallet;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.ExecutionException;

public final class ProofUtils {

    public static final String CSO_INFO_PROOF_REQUEST_MIN_REFERENT = "cso_id_min";
    public static final String CSO_INFO_PROOF_REQUEST_MAX_REFERENT = "cso_id_max";
    public static final String DSO_DISTRICT_PROOF_REQUEST_MIN_REFERENT = "dso_district_id_min";
    public static final String DSO_DISTRICT_PROOF_REQUEST_MAX_REFERENT = "dso_district_id_max";

    private ProofUtils() {}


    // Proof requests


    public static JSONObject createCSOInfoAndDSODistrictProofRequest(String csoDID, String dsoDID, String csoInfoCredentialDefinitionID, String dsoDistrictCredentialDefinitionID) throws IndyException {
        JSONObject proofRequest = null;
        int csoDIDExpectedEncodedValue = csoDID.hashCode();
        try {
            JSONArray csoInfoRestrictionsList = new JSONArray()
                    .put(new JSONObject()
                            .put("cred_def_id", csoInfoCredentialDefinitionID)
                            .put("issuer_did", csoDID)
                    );
            JSONArray dsoDistrictRestrictionsList = new JSONArray()
                    .put(new JSONObject()
                            .put("cred_def_id", dsoDistrictCredentialDefinitionID)
                            .put("issuer_did", dsoDID)
                    );
            proofRequest = new JSONObject()
                    .put("name", "CSO Ownership and DSO District Proof Request")
                    .put("version", "1.0")
                    .put("nonce", Anoncreds.generateNonce().get())
                    .put("requested_predicates", new JSONObject()
                            .put(CSO_INFO_PROOF_REQUEST_MIN_REFERENT, new JSONObject()
                                    .put("name", "CSO")
                                    .put("p_type", ">=")
                                    .put("p_value", csoDIDExpectedEncodedValue)
                                    .put("restrictions", csoInfoRestrictionsList)
                            )
                            .put(CSO_INFO_PROOF_REQUEST_MAX_REFERENT, new JSONObject()
                                    .put("name", "CSO")
                                    .put("p_type", "<=")
                                    .put("p_value", csoDIDExpectedEncodedValue)
                                    .put("restrictions", csoInfoRestrictionsList)
                            )
                            .put(DSO_DISTRICT_PROOF_REQUEST_MIN_REFERENT, new JSONObject()
                                    .put("name", "district_id")
                                    .put("p_type", ">=")
                                    .put("p_value", CredentialUtils.CS_DISTRICT_ID)
                                    .put("restrictions", dsoDistrictRestrictionsList)
                            )
                            .put(DSO_DISTRICT_PROOF_REQUEST_MAX_REFERENT, new JSONObject()
                                    .put("name", "district_id")
                                    .put("p_type", "<=")
                                    .put("p_value", CredentialUtils.CS_DISTRICT_ID)
                                    .put("restrictions", dsoDistrictRestrictionsList)
                            )
                    );
        } catch (JSONException | ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
        return proofRequest;
    }

    // Proofs

    public static JSONObject createProofCSOInfoDSODistrictProofRequest(Wallet csWallet, JSONObject csoInfodsoDistrictProofRequest, JSONObject csoInfodsoDistrictProofCredentials, String csMasterSecretID, String csoInfoCredentialSchemaID, String dsoDistrictCredentialSchemaID, JSONObject csoInfoCredentialSchema, JSONObject dsoDistrictCredentialSchema, String csoInfoCredentialDefinitionID, String dsoDistrictCredentialDefinitionID, JSONObject csoInfoCredentialDefinition, JSONObject dsoDistrictCredentialDefinition) throws IndyException {
        JSONObject proof = null;
        try {
            proof = new JSONObject(
                    Anoncreds.proverCreateProof(
                            csWallet,
                            csoInfodsoDistrictProofRequest.toString(),
                            csoInfodsoDistrictProofCredentials.toString(),
                            csMasterSecretID,
                            new JSONObject()
                                    .put(csoInfoCredentialSchemaID, csoInfoCredentialSchema)
                                    .put(dsoDistrictCredentialSchemaID, dsoDistrictCredentialSchema)
                                    .toString(),
                            new JSONObject()
                                    .put(csoInfoCredentialDefinitionID, csoInfoCredentialDefinition)
                                    .put(dsoDistrictCredentialDefinitionID, dsoDistrictCredentialDefinition)
                                    .toString(),
                            new JSONObject().toString()
                    ).get()
            );
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return proof;
    }

    // Proof verifications

    public static boolean verifyCSOInfoDSODistrictProof(JSONObject csoInfodsoDistrictProofRequest, JSONObject csoInfodsoDistrictProof, String csoInfoCredentialSchemaID, String dsoDistrictCredentialSchemaID, JSONObject csoInfoCredentialSchema, JSONObject dsoDistrictCredentialSchema, String csoInfoCredentialDefinitionID, String dsoDistrictCredentialDefinitionID, JSONObject csoInfoCredentialDefinition, JSONObject dsoDistrictCredentialDefinition) throws IndyException {
        boolean isProofValid = false;
        try {
            isProofValid = Anoncreds.verifierVerifyProof(
                    csoInfodsoDistrictProofRequest.toString(),
                    csoInfodsoDistrictProof.toString(),
                    new JSONObject()
                            .put(csoInfoCredentialSchemaID, csoInfoCredentialSchema)
                            .put(dsoDistrictCredentialSchemaID, dsoDistrictCredentialSchema)
                            .toString(),
                    new JSONObject()
                            .put(csoInfoCredentialDefinitionID, csoInfoCredentialDefinition)
                            .put(dsoDistrictCredentialDefinitionID, dsoDistrictCredentialDefinition)
                            .toString(),
                    new JSONObject().toString(),
                    new JSONObject().toString()
            ).get();
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return isProofValid;
    }
}
