package fi.aalto.indy_utils;

import org.hyperledger.indy.sdk.IndyException;
import org.hyperledger.indy.sdk.anoncreds.Anoncreds;
import org.hyperledger.indy.sdk.wallet.Wallet;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.ExecutionException;

public final class ProofUtils {

    public static final String CSO_INFO_PROOF_REQUEST_REFERENT = "CSO";
    public static final String DSO_DISTRICT_PROOF_REQUEST_REFERENT = "district";
    public static final String ER_CHARGING_PROOF_REQUEST_REFERENT = "charging";

    private ProofUtils() {}


    // Proof requests

    /**
     * @param csoDID = result of DIDUtils.createAndWriteCSODID
     * @param dsoDID = result of DIDUtils.createAndWriteDSODID
     * @param csoInfoCredentialDefinitionID = result of calling getString("id") on the JSON returned by CredentialDefinitionUtils.readCredentialDefinitionFromLedger for the CSO Info credential definition
     * @param dsoDistrictCredentialDefinitionID = result of calling getString("id") on the JSON returned by CredentialDefinitionUtils.readCredentialDefinitionFromLedger for the DSO District Info credential definition
     * @return the JSON containing the proof request for CSO Info + DSO District Info credentials.
     * @throws IndyException
     */
    public static JSONObject createCSOInfoAndDSODistrictProofRequest(String csoDID, String dsoDID, String csoInfoCredentialDefinitionID, String dsoDistrictCredentialDefinitionID) throws IndyException {
        JSONObject proofRequest = null;
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
                    .put("requested_attributes", new JSONObject()
                            .put(CSO_INFO_PROOF_REQUEST_REFERENT, new JSONObject()
                                    .put("names", new JSONArray(CredentialSchemaUtils.CSO_INFO_CREDENTIAL_SCHEMA_ATTRIBUTES))
                                    .put("restrictions", csoInfoRestrictionsList)
                            )
                            .put(DSO_DISTRICT_PROOF_REQUEST_REFERENT, new JSONObject()
                                    .put("names", new JSONArray(CredentialSchemaUtils.DSO_DISTRICT_CREDENTIAL_SCHEMA_ATTRIBUTES))
                                    .put("restrictions", dsoDistrictRestrictionsList)
                            )
                    );
        } catch (JSONException | ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
        return proofRequest;
    }

    /**
     * @param erDID = result of DIDUtils.createAndWriteERDID
     * @param erChargingCredentialDefinitionID = result of calling getString("id") on the JSON returned by CredentialDefinitionUtils.readCredentialDefinitionFromLedger for the ER Charging credential definition
     * @return the JSON containing the proof request for ER Charging credential.
     * @throws IndyException
     */
    public static JSONObject createERChargingProofRequest(String erDID, String erChargingCredentialDefinitionID) throws IndyException {
        JSONObject proofRequest = null;
        try {
            JSONArray erInfoRestrictionsList = new JSONArray()
                    .put(new JSONObject()
                            .put("cred_def_id", erChargingCredentialDefinitionID)
                            .put("issuer_did", erDID)
                    );
            proofRequest = new JSONObject()
                    .put("name", "EV Valid ER Charging Credential Proof Request")
                    .put("version", "1.0")
                    .put("nonce", Anoncreds.generateNonce().get())
                    .put("requested_attributes", new JSONObject()
                            .put(ER_CHARGING_PROOF_REQUEST_REFERENT, new JSONObject()
                                    .put("names", new JSONArray(CredentialSchemaUtils.ER_CHARGING_CREDENTIAL_SCHEMA_ATTRIBUTES))
                                    .put("restrictions", erInfoRestrictionsList)
                            )
                    );
        } catch (JSONException | ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
        return proofRequest;
    }

    // Proofs

    /**
     * @param csWallet = result of WalletUtils.openCSWallet
     * @param csoInfodsoDistrictProofRequest = result of ProofUtils.createCSOInfoAndDSODistrictProofRequest
     * @param csoInfodsoDistrictProofCredentials = result of CredentialUtils.getPredicatesForCSOInfoDSODistrictProofRequest
     * @param csMasterSecretID = result of CredentialUtils.createAndSaveCSMasterSecret
     * @param csoInfoCredentialSchemaID = result of calling getString("id") on the JSON returned by CredentialSchemaUtils.readCredentialSchemaFromLedger for the CSO Info credential schema
     * @param dsoDistrictCredentialSchemaID = result of calling getString("id") on the JSON returned by CredentialSchemaUtils.readCredentialSchemaFromLedger for the DSO District Info credential schema
     * @param csoInfoCredentialSchema = result of calling getJSONObject("object") on the JSON returned by CredentialSchemaUtils.readCredentialSchemaFromLedger for the CSO Info credential schema
     * @param dsoDistrictCredentialSchema = result of calling getJSONObject("object") on the JSON returned by CredentialSchemaUtils.readCredentialSchemaFromLedger for the DSO District Info credential schema
     * @param csoInfoCredentialDefinitionID = result of calling getString("id") on the JSON returned by CredentialDefinitionUtils.readCredentialDefinitionFromLedger for the CSO Info credential definition
     * @param dsoDistrictCredentialDefinitionID = result of calling getString("id") on the JSON returned by CredentialDefinitionUtils.readCredentialDefinitionFromLedger for the DSO District Info credential definition
     * @param csoInfoCredentialDefinition = result of calling getJSONObject("object") on the JSON returned by CredentialDefinitionUtils.readCredentialDefinitionFromLedger for the CSO Info credential definition
     * @param dsoDistrictCredentialDefinition = result of calling getJSONObject("object") on the JSON returned by CredentialDefinitionUtils.readCredentialDefinitionFromLedger for the DSO District Info credential definition
     * @return the proof for the CSO Info + DSO District Info proof request.
     * @throws IndyException
     */
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

    /**
     * @param evWallet = result of WalletUtils.openEVWallet
     * @param erChargingProofRequest = result of ProofUtils.createERChargingProofRequest
     * @param erChargingProofCredentials = result of CredentialUtils.getPredicatesForERChargingProofRequest
     * @param evMasterSecretID = result of CredentialUtils.createAndSaveEVMasterSecret
     * @param erChargingCredentialSchemaID = result of calling getString("id") on the JSON returned by CredentialSchemaUtils.readCredentialSchemaFromLedger for the ER Charging credential schema
     * @param erChargingCredentialSchema = result of calling getJSONObject("object") on the JSON returned by CredentialSchemaUtils.readCredentialSchemaFromLedger for the ER Charging credential schema
     * @param erChargingCredentialDefinitionID = result of calling getString("id") on the JSON returned by CredentialDefinitionUtils.readCredentialDefinitionFromLedger for the ER Charging credential definition
     * @param erChargingCredentialDefinition = result of calling getJSONObject("object") on the JSON returned by CredentialDefinitionUtils.readCredentialDefinitionFromLedger for the ER Charging credential definition
     * @return the proof for the ER Charging proof request.
     * @throws IndyException
     */
    public static JSONObject createProofERChargingProofRequest(Wallet evWallet, JSONObject erChargingProofRequest, JSONObject erChargingProofCredentials, String evMasterSecretID, String erChargingCredentialSchemaID, JSONObject erChargingCredentialSchema, String erChargingCredentialDefinitionID, JSONObject erChargingCredentialDefinition) throws IndyException {
        JSONObject proof = null;
        try {
            proof = new JSONObject(
                    Anoncreds.proverCreateProof(
                            evWallet,
                            erChargingProofRequest.toString(),
                            erChargingProofCredentials.toString(),
                            evMasterSecretID,
                            new JSONObject()
                                    .put(erChargingCredentialSchemaID, erChargingCredentialSchema)
                                    .toString(),
                            new JSONObject()
                                    .put(erChargingCredentialDefinitionID, erChargingCredentialDefinition)
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

    /**
     * @param csoInfodsoDistrictProofRequest = result of ProofUtils.createCSOInfoAndDSODistrictProofRequest
     * @param csoInfodsoDistrictProof = result of ProofUtils.createProofCSOInfoDSODistrictProofRequest
     * @param csoInfoCredentialSchemaID = result of calling getString("id") on the JSON returned by CredentialSchemaUtils.readCredentialSchemaFromLedger for the CSO Info credential schema
     * @param dsoDistrictCredentialSchemaID = result of calling getString("id") on the JSON returned by CredentialSchemaUtils.readCredentialSchemaFromLedger for the DSO District Info credential schema
     * @param csoInfoCredentialSchema = result of calling getJSONObject("object") on the JSON returned by CredentialSchemaUtils.readCredentialSchemaFromLedger for the CSO Info credential schema
     * @param dsoDistrictCredentialSchema = result of calling getJSONObject("object") on the JSON returned by CredentialSchemaUtils.readCredentialSchemaFromLedger for the DSO District Info credential schema
     * @param csoInfoCredentialDefinitionID = result of calling getString("id") on the JSON returned by CredentialDefinitionUtils.readCredentialDefinitionFromLedger for the CSO Info credential definition
     * @param dsoDistrictCredentialDefinitionID = result of calling getString("id") on the JSON returned by CredentialDefinitionUtils.readCredentialDefinitionFromLedger for the DSO District Info credential definition
     * @param csoInfoCredentialDefinition = result of calling getJSONObject("object") on the JSON returned by CredentialDefinitionUtils.readCredentialDefinitionFromLedger for the CSO Info credential definition
     * @param dsoDistrictCredentialDefinition = result of calling getJSONObject("object") on the JSON returned by CredentialDefinitionUtils.readCredentialDefinitionFromLedger for the DSO District Info credential definition
     * @return the verification result of the CSO Info + DSO District Info proof for the given proof request.
     * @throws IndyException
     */
    public static boolean verifyCSOInfoDSODistrictProofCrypto(JSONObject csoInfodsoDistrictProofRequest, JSONObject csoInfodsoDistrictProof, String csoInfoCredentialSchemaID, String dsoDistrictCredentialSchemaID, JSONObject csoInfoCredentialSchema, JSONObject dsoDistrictCredentialSchema, String csoInfoCredentialDefinitionID, String dsoDistrictCredentialDefinitionID, JSONObject csoInfoCredentialDefinition, JSONObject dsoDistrictCredentialDefinition) throws IndyException {
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

    /**
     * @param proof = result of ProofUtils.createProofCSOInfoDSODistrictProofRequest
     * @param expectedDistrictID = expected district ID to match in the proof
     * @param districtCredentialValidTime = time at which the DSO credential must be valid
     * @param expectedCSODID = expected CSO DID to match in the proof
     * @param csoCredentialValidTime = time at which the CSO credential must be valid
     * @return the result of the credential validation.
     * @throws JSONException
     */
    public static boolean verifyCSOInfoDSODistrictProofValues(JSONObject proof, String expectedDistrictID, long districtCredentialValidTime, String expectedCSODID, long csoCredentialValidTime) throws JSONException {
        JSONObject proofs = proof.getJSONObject("requested_proof").getJSONObject("revealed_attr_groups");
        JSONObject csoProof = proofs.getJSONObject(ProofUtils.CSO_INFO_PROOF_REQUEST_REFERENT);
        JSONObject dsoProof = proofs.getJSONObject(ProofUtils.DSO_DISTRICT_PROOF_REQUEST_REFERENT);

        String csoDID = csoProof.getJSONObject("values").getJSONObject(CredentialSchemaUtils.CSO_INFO_CREDENTIAL_SCHEMA_ATTRIBUTES[0]).getString("raw");
        long csoValidFrom = Long.valueOf(csoProof.getJSONObject("values").getJSONObject(CredentialSchemaUtils.CSO_INFO_CREDENTIAL_SCHEMA_ATTRIBUTES[1]).getString("encoded"));
        long csoValidTo = Long.valueOf(csoProof.getJSONObject("values").getJSONObject(CredentialSchemaUtils.CSO_INFO_CREDENTIAL_SCHEMA_ATTRIBUTES[2]).getString("encoded"));

        String districtID = dsoProof.getJSONObject("values").getJSONObject(CredentialSchemaUtils.DSO_DISTRICT_CREDENTIAL_SCHEMA_ATTRIBUTES[0]).getString("encoded");
        long dsoValidFrom = Long.valueOf(dsoProof.getJSONObject("values").getJSONObject(CredentialSchemaUtils.DSO_DISTRICT_CREDENTIAL_SCHEMA_ATTRIBUTES[1]).getString("encoded"));
        long dsoValidTo = Long.valueOf(dsoProof.getJSONObject("values").getJSONObject(CredentialSchemaUtils.DSO_DISTRICT_CREDENTIAL_SCHEMA_ATTRIBUTES[2]).getString("encoded"));

        return csoDID.equals(expectedCSODID) && csoValidFrom <= csoCredentialValidTime && csoValidTo >= csoCredentialValidTime && districtID.equals(expectedDistrictID) && dsoValidFrom <= districtCredentialValidTime && dsoValidTo >= districtCredentialValidTime;
    }

    /**
     * @param erChargingProofRequest = result of ProofUtils.createERChargingProofRequest
     * @param erChargingDistrictProof = result of ProofUtils.createProofERChargingProofRequest
     * @param erChargingCredentialSchemaID = result of calling getString("id") on the JSON returned by CredentialSchemaUtils.readCredentialSchemaFromLedger for the ER Charging credential schema
     * @param erChargingCredentialSchema = result of calling getJSONObject("object") on the JSON returned by CredentialSchemaUtils.readCredentialSchemaFromLedger for the ER Charging credential schema
     * @param erChargingCredentialDefinitionID = result of calling getString("id") on the JSON returned by CredentialDefinitionUtils.readCredentialDefinitionFromLedger for the ER Charging credential definition
     * @param erChargingCredentialDefinition = result of calling getJSONObject("object") on the JSON returned by CredentialDefinitionUtils.readCredentialDefinitionFromLedger for the ER Charging credential definition
     * @return the verification result of the ER Charging proof for the given proof request.
     * @throws IndyException
     */
    public static boolean verifyERChargingProofCrypto(JSONObject erChargingProofRequest, JSONObject erChargingDistrictProof, String erChargingCredentialSchemaID, JSONObject erChargingCredentialSchema, String erChargingCredentialDefinitionID, JSONObject erChargingCredentialDefinition) throws IndyException {
        boolean isProofValid = false;
        try {
            isProofValid = Anoncreds.verifierVerifyProof(
                    erChargingProofRequest.toString(),
                    erChargingDistrictProof.toString(),
                    new JSONObject()
                            .put(erChargingCredentialSchemaID, erChargingCredentialSchema)
                            .toString(),
                    new JSONObject()
                            .put(erChargingCredentialDefinitionID, erChargingCredentialDefinition)
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

    /**
     * @param proof = result of ProofUtils.createProofERChargingProofRequest
     * @param erCredentialValidTime = time at which the ER credential must be valid
     * @return the result of the credential validation.
     * @throws JSONException
     */
    public static boolean verifyERChargingProofValues(JSONObject proof, long erCredentialValidTime) throws JSONException {
        JSONObject proofs = proof.getJSONObject("requested_proof").getJSONObject("revealed_attr_groups");
        JSONObject erProof = proofs.getJSONObject(ProofUtils.ER_CHARGING_PROOF_REQUEST_REFERENT);

        long erValidFrom = Long.valueOf(erProof.getJSONObject("values").getJSONObject(CredentialSchemaUtils.ER_CHARGING_CREDENTIAL_SCHEMA_ATTRIBUTES[1]).getString("encoded"));
        long erValidTo = Long.valueOf(erProof.getJSONObject("values").getJSONObject(CredentialSchemaUtils.ER_CHARGING_CREDENTIAL_SCHEMA_ATTRIBUTES[2]).getString("encoded"));

        return erValidFrom <= erCredentialValidTime && erValidTo >= erCredentialValidTime;
    }
}
