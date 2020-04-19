package fi.aalto.indy_utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.hyperledger.indy.sdk.IndyException;
import org.hyperledger.indy.sdk.anoncreds.Anoncreds;
import org.hyperledger.indy.sdk.anoncreds.AnoncredsResults;
import org.hyperledger.indy.sdk.anoncreds.CredentialsSearchForProofReq;
import org.hyperledger.indy.sdk.wallet.Wallet;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.ExecutionException;

public final class CredentialUtils {

    private static final String CS_MASTER_SECRET_ID = "CS-secret";

    private static final String EV_MASTER_SECRET_ID = "EV-secret";

    public static final int CS_DISTRICT_ID = 1;

    private static SharedPreferences storage;

    private CredentialUtils() {}

    static void initWithAppContext(Context context) {
        CredentialUtils.storage = context.getSharedPreferences("credentials", Context.MODE_PRIVATE);
    }

    // Master secrets

    /**
     * @param csWallet = result of WalletUtils.openCSWallet
     * @return the master secret created for the CS wallet.
     * @throws IndyException
     */
    public static String createAndSaveCSMasterSecret(Wallet csWallet) throws IndyException {
        try {
            Anoncreds.proverCreateMasterSecret(csWallet, CredentialUtils.CS_MASTER_SECRET_ID).get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException ignored) {}        // Master secret already exists
        return CredentialUtils.CS_MASTER_SECRET_ID;
    }

    /**
     * @param evWallet = result of WalletUtils.openEVWallet
     * @return the master secret created for the EV wallet.
     * @throws IndyException
     */
    public static String createAndSaveEVMasterSecret(Wallet evWallet) throws IndyException {
        try {
            Anoncreds.proverCreateMasterSecret(evWallet, CredentialUtils.EV_MASTER_SECRET_ID).get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException ignored) {}        // Master secret already exists
        return CredentialUtils.EV_MASTER_SECRET_ID;
    }

    //Credential offers

    /**
     * @param offererWallet = the wallet of the actor offering the credential
     * @param credentialDefinitionID = result of getString("id") on the JSON returned by CredentialDefinitionUtils.readCredentialDefinitionFromLedger
     * @return the credential offer as JSON.
     * @throws IndyException
     */
    public static JSONObject createCredentialOffer(Wallet offererWallet, String credentialDefinitionID) throws IndyException {
        JSONObject credentialOffer = CredentialUtils.retrieveCredentialOffer(credentialDefinitionID);

        try {
            if (credentialOffer != null) {
                Log.d(CredentialUtils.class.toString(), "Credential offer retrieved from cache.");
            } else {
                Log.d(CredentialUtils.class.toString(), "Credential offer not found in cache. Generating...");
                credentialOffer = new JSONObject(Anoncreds.issuerCreateCredentialOffer(offererWallet, credentialDefinitionID).get());
                CredentialUtils.saveCredentialOffer(credentialDefinitionID, credentialOffer);
                Log.d(CredentialUtils.class.toString(), "Credential offer saved in cache.");
            }
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
        return credentialOffer;
    }

    private static JSONObject retrieveCredentialOffer(String credentialDefinitionID) {
        String credentialOfferSerialised = CredentialUtils.storage.getString(CredentialUtils.getCredentialOfferKeyFromCredentialDefinitionID(credentialDefinitionID), null);

        if (credentialOfferSerialised == null) {
            return null;
        }

        JSONObject credentialOffer = null;
        try {
            credentialOffer = new JSONObject(credentialOfferSerialised);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return credentialOffer;
    }

    private static String getCredentialOfferKeyFromCredentialDefinitionID(String credentialDefinitionID) {
        return new StringBuilder().append("cred-offer::").append("cred-def-id:").append(credentialDefinitionID).toString();
    }

    private static void saveCredentialOffer(String credentialDefinitionID, JSONObject credentialDefinition) {
        CredentialUtils.storage.edit().putString(CredentialUtils.getCredentialOfferKeyFromCredentialDefinitionID(credentialDefinitionID), credentialDefinition.toString()).apply();
    }

    // Credential requests

    /**
     * @param csWallet = result of WalletUtils.openCSWallet
     * @param csDID = result of DIDUtils.createCSDID
     * @param csoInfoCredentialOffer = result of CredentialUtils.createCredentialOffer for the CSO Info credential definition
     * @param csoInfoCredentialDefinition = result of calling getJSONObject("object") on the JSON returned by CredentialDefinitionUtils.readCredentialDefinitionFromLedger for the CSO Info credential definition
     * @param csMasterSecretID = result of CredentialUtils.createAndSaveCSMasterSecret
     * @return the credential request to obtain a CSO Info credential.
     * @throws IndyException
     */
    public static AnoncredsResults.ProverCreateCredentialRequestResult createCSOInfoCredentialRequest(Wallet csWallet, String csDID, JSONObject csoInfoCredentialOffer, JSONObject csoInfoCredentialDefinition, String csMasterSecretID) throws IndyException {
        AnoncredsResults.ProverCreateCredentialRequestResult credentialRequest = CredentialUtils.retrieveCredentialRequestForCredentialOffer(csoInfoCredentialOffer);

        try {
            if (credentialRequest != null) {
                Log.d(CredentialUtils.class.toString(), "Credential request retrieved from cache.");
            } else {
                Log.d(CredentialUtils.class.toString(), "Credential request not found in cache. Generating...");
                credentialRequest = Anoncreds.proverCreateCredentialReq(csWallet, csDID, csoInfoCredentialOffer.toString(), csoInfoCredentialDefinition.toString(), csMasterSecretID).get();
                CredentialUtils.saveCredentialRequest(csoInfoCredentialOffer, credentialRequest);
                Log.d(CredentialUtils.class.toString(), "Credential request saved in cache.");
            }
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
        return credentialRequest;
    }

    /**
     * @param csWallet = result of WalletUtils.openCSWallet
     * @param csDID = result of DIDUtils.createCSDID
     * @param dsoDistrictCredentialOffer = result of CredentialUtils.createCredentialOffer for the DSO District Info credential definition
     * @param csoInfoCredentialDefinition = result of calling getJSONObject("object") on the JSON returned by CredentialDefinitionUtils.readCredentialDefinitionFromLedger for the DSO District Info credential definition
     * @param csMasterSecretID = result of CredentialUtils.createAndSaveCSMasterSecret
     * @return the credential request to obtain a DSO District Info credential.
     * @throws IndyException
     */
    public static AnoncredsResults.ProverCreateCredentialRequestResult createDSODistrictCredentialRequest(Wallet csWallet, String csDID, JSONObject dsoDistrictCredentialOffer, JSONObject dsoDistrictCredentialDefinition, String csMasterSecretID) throws IndyException {
        AnoncredsResults.ProverCreateCredentialRequestResult credentialRequest = CredentialUtils.retrieveCredentialRequestForCredentialOffer(dsoDistrictCredentialOffer);

        try {
            if (credentialRequest != null) {
                Log.d(CredentialUtils.class.toString(), "Credential request retrieved from cache.");
            } else {
                Log.d(CredentialUtils.class.toString(), "Credential request not found in cache. Generating...");
                credentialRequest = Anoncreds.proverCreateCredentialReq(csWallet, csDID, dsoDistrictCredentialOffer.toString(), dsoDistrictCredentialDefinition.toString(), csMasterSecretID).get();
                CredentialUtils.saveCredentialRequest(dsoDistrictCredentialOffer, credentialRequest);
                Log.d(CredentialUtils.class.toString(), "Credential request saved in cache.");
            }
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
        return credentialRequest;
    }

    /**
     * @param evWallet = result of WalletUtils.openEVWallet
     * @param evDID = result of DIDUtils.createEVDID
     * @param erChargingCredentialOffer = result of CredentialUtils.createCredentialOffer for the ER Charging credential definition
     * @param erChargingCredentialDefinition = result of calling getJSONObject("object") on the JSON returned by CredentialDefinitionUtils.readCredentialDefinitionFromLedger for the ER Charging credential definition
     * @param evMasterSecretID = result of CredentialUtils.createAndSaveEVMasterSecret
     * @return the credential request to obtain a ER Charging credential.
     * @throws IndyException
     */
    public static AnoncredsResults.ProverCreateCredentialRequestResult createERChargingCredentialRequest(Wallet evWallet, String evDID, JSONObject erChargingCredentialOffer, JSONObject erChargingCredentialDefinition, String evMasterSecretID) throws IndyException {
        AnoncredsResults.ProverCreateCredentialRequestResult credentialRequest = CredentialUtils.retrieveCredentialRequestForCredentialOffer(erChargingCredentialOffer);

        try {
            if (credentialRequest != null) {
                Log.d(CredentialUtils.class.toString(), "Credential request retrieved from cache.");
            } else {
                Log.d(CredentialUtils.class.toString(), "Credential request not found in cache. Generating...");
                credentialRequest = Anoncreds.proverCreateCredentialReq(evWallet, evDID, erChargingCredentialOffer.toString(), erChargingCredentialDefinition.toString(), evMasterSecretID).get();
                CredentialUtils.saveCredentialRequest(erChargingCredentialOffer, credentialRequest);
                Log.d(CredentialUtils.class.toString(), "Credential request saved in cache.");
            }
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
        return credentialRequest;
    }

    private static AnoncredsResults.ProverCreateCredentialRequestResult retrieveCredentialRequestForCredentialOffer(JSONObject credentialOffer) {
        String credentialOfferDataSerialised = CredentialUtils.storage.getString(CredentialUtils.getCredentialRequestJSONKeyFromCredentialOffer(credentialOffer), null);
        String credentialOfferMetadataSerialised = CredentialUtils.storage.getString(CredentialUtils.getCredentialRequestMetadataKeyFromCredentialOffer(credentialOffer), null);

        if (credentialOfferDataSerialised == null || credentialOfferMetadataSerialised == null) {
            return null;
        }

        return new AnoncredsResults.ProverCreateCredentialRequestResult(credentialOfferDataSerialised, credentialOfferMetadataSerialised);
    }

    private static String getCredentialRequestJSONKeyFromCredentialOffer(JSONObject credentialOffer) {
        return new StringBuilder().append("cred-request::").append("cred-offer:").append(credentialOffer.toString().hashCode()).append(":data").toString();
    }

    private static String getCredentialRequestMetadataKeyFromCredentialOffer(JSONObject credentialOffer) {
        return new StringBuilder().append("cred-request::").append("cred-offer:").append(credentialOffer.toString().hashCode()).append(":metadata").toString();
    }

    private static void saveCredentialRequest(JSONObject credentialOffer, AnoncredsResults.ProverCreateCredentialRequestResult credentialRequest) {
        CredentialUtils.storage.edit().putString(CredentialUtils.getCredentialRequestJSONKeyFromCredentialOffer(credentialOffer), credentialRequest.getCredentialRequestJson()).apply();
        CredentialUtils.storage.edit().putString(CredentialUtils.getCredentialRequestMetadataKeyFromCredentialOffer(credentialOffer), credentialRequest.getCredentialRequestMetadataJson()).apply();
    }

    // Credentials

    /**
     * @param csoWallet = result of WalletUtils.openCSOWallet
     * @param csoInfoCredentialOffer = result of CredentialUtils.createCredentialOffer for the CSO Info credential definition
     * @param csoInfoCredentialRequest = result of CredentialUtils.createCredentialRequest for the CSO Info credential definition
     * @param csoDID = result of DIDUtils.createAndWriteCSODID
     * @return the credential containing CSO Info. Specifically, the credential has a field CSO containing the DID of the CSO. It is signed by the same CSO.
     * @throws IndyException
     */
    public static AnoncredsResults.IssuerCreateCredentialResult createCSOInfoCredential(Wallet csoWallet, JSONObject csoInfoCredentialOffer, JSONObject csoInfoCredentialRequest, String csoDID) throws IndyException {
        AnoncredsResults.IssuerCreateCredentialResult credential = CredentialUtils.retrieveCredentialForCredentialRequest(csoInfoCredentialRequest);

        try {
            if (credential != null) {
                Log.d(CredentialUtils.class.toString(), "Credential retrieved from cache.");
            } else {
                Log.d(CredentialUtils.class.toString(), "Credential not found in cache. Generating...");
                JSONObject csoInfoCredentialContent = new JSONObject()
                        .put("cso", new JSONObject()
                                .put("raw", csoDID)
                                .put("encoded", String.format("%d", csoDID.hashCode()))
                        )
                        .put("valid_from", new JSONObject()
                                .put("raw", "0")
                                .put("encoded", "0")
                        )
                        .put("valid_until", new JSONObject()
                                .put("raw", "2")
                                .put("encoded", "2")
                        );
                credential = Anoncreds.issuerCreateCredential(csoWallet, csoInfoCredentialOffer.toString(), csoInfoCredentialRequest.toString(), csoInfoCredentialContent.toString(), null, -1).get();
                CredentialUtils.saveCredential(csoInfoCredentialRequest, credential);
                Log.d(CredentialUtils.class.toString(), "Credential saved in cache.");
            }
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return credential;
    }

    /**
     * @param dsoWallet = result of WalletUtils.openDSOWallet
     * @param dsoDistrictCredentialOffer = result of CredentialUtils.createCredentialOffer for the DSO District Info credential definition
     * @param dsoDistrictCredentialRequest = result of CredentialUtils.createCredentialRequest for the DSO District Info credential definition
     * @return the credential containing DSO District Info. Specifically, the credential has a field district_id containing the id of the energy district. It is signed by the DSO.
     * @throws IndyException
     */
    public static AnoncredsResults.IssuerCreateCredentialResult createDSODistrictCredential(Wallet dsoWallet, JSONObject dsoDistrictCredentialOffer, JSONObject dsoDistrictCredentialRequest) throws IndyException {
        AnoncredsResults.IssuerCreateCredentialResult credential = CredentialUtils.retrieveCredentialForCredentialRequest(dsoDistrictCredentialRequest);

        try {
            if (credential != null) {
                Log.d(CredentialUtils.class.toString(), "Credential retrieved from cache.");
            } else {
                Log.d(CredentialUtils.class.toString(), "Credential not found in cache. Generating...");
                JSONObject dsoDistrictCredentialContent = new JSONObject()
                        .put("district_id", new JSONObject()
                                .put("raw", String.format("%d", CredentialUtils.CS_DISTRICT_ID))
                                .put("encoded", String.format("%d", CredentialUtils.CS_DISTRICT_ID))
                        )
                        .put("valid_from", new JSONObject()
                                .put("raw", "0")
                                .put("encoded", "0")
                        )
                        .put("valid_until", new JSONObject()
                                .put("raw", "2")
                                .put("encoded", "2")
                        );
                credential = Anoncreds.issuerCreateCredential(dsoWallet, dsoDistrictCredentialOffer.toString(), dsoDistrictCredentialRequest.toString(), dsoDistrictCredentialContent.toString(), null, -1).get();
                CredentialUtils.saveCredential(dsoDistrictCredentialRequest, credential);
                Log.d(CredentialUtils.class.toString(), "Credential saved in cache.");
            }
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return credential;
    }

    /**
     * @param erWallet = result of WalletUtils.openERWallet
     * @param evDID = result of DIDUtils.createEVDID
     * @param erChargingCredentialOffer = result of CredentialUtils.createCredentialOffer for the ER Charging credential definition
     * @param erChargingCredentialRequest = result of CredentialUtils.createCredentialRequest for the ER Charging credential definition
     * @return the credential containing charging authorisation. Specifically, the credential has a field expiration_time containing the time until which the credential is valid. It is signed by the ER.
     * @throws IndyException
     */
    public static AnoncredsResults.IssuerCreateCredentialResult createERChargingCredential(Wallet erWallet, String evDID, JSONObject erChargingCredentialOffer, JSONObject erChargingCredentialRequest) throws IndyException {
        AnoncredsResults.IssuerCreateCredentialResult credential = CredentialUtils.retrieveCredentialForCredentialRequest(erChargingCredentialRequest);

        try {
            if (credential != null) {
                Log.d(CredentialUtils.class.toString(), "Credential retrieved from cache.");
            } else {
                Log.d(CredentialUtils.class.toString(), "Credential not found in cache. Generating...");
                JSONObject evChargingCredentialContent = new JSONObject()
                        .put("ev", new JSONObject()
                                .put("raw", evDID)
                                .put("encoded", String.format("%d", evDID.hashCode()))
                        )
                        .put("valid_from", new JSONObject()
                                .put("raw", "0")
                                .put("encoded", "0")
                        )
                        .put("valid_until", new JSONObject()
                                .put("raw", "2")
                                .put("encoded", "2")
                        );
                credential = Anoncreds.issuerCreateCredential(erWallet, erChargingCredentialOffer.toString(), erChargingCredentialRequest.toString(), evChargingCredentialContent.toString(), null, -1).get();
                CredentialUtils.saveCredential(erChargingCredentialRequest, credential);
                Log.d(CredentialUtils.class.toString(), "Credential saved in cache.");
            }
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return credential;
    }

    private static AnoncredsResults.IssuerCreateCredentialResult retrieveCredentialForCredentialRequest(JSONObject credentialRequest) {
        String credentialJSONSerialised = CredentialUtils.storage.getString(CredentialUtils.getCredentialJSONKeyFromCredentialRequest(credentialRequest), null);
        String credentialRevocID = CredentialUtils.storage.getString(CredentialUtils.getCredentialRevocIDKeyFromCredentialRequest(credentialRequest), null);
        String credentialRevocRegDeltaJSONSerialised = CredentialUtils.storage.getString(CredentialUtils.getCredentialRevocRegDeltaJSONKeyFromCredentialRequest(credentialRequest), null);

        if (credentialJSONSerialised == null) {         //credentialRevocID and credentialRevocRegDeltaJSONSerialised can be null
            return null;
        }

        return new AnoncredsResults.IssuerCreateCredentialResult(credentialJSONSerialised, credentialRevocID, credentialRevocRegDeltaJSONSerialised);
    }

    private static String getCredentialJSONKeyFromCredentialRequest(JSONObject credentialRequest) {
        return new StringBuilder().append("cred::").append("cred-req:").append(credentialRequest.toString().hashCode()).append(":json").toString();
    }

    private static String getCredentialRevocIDKeyFromCredentialRequest(JSONObject credentialRequest) {
        return new StringBuilder().append("cred::").append("cred-req:").append(credentialRequest.toString().hashCode()).append(":revoc-id").toString();
    }

    private static String getCredentialRevocRegDeltaJSONKeyFromCredentialRequest(JSONObject credentialRequest) {
        return new StringBuilder().append("cred::").append("cred-req:").append(credentialRequest.toString().hashCode()).append(":revoc-reg-delta").toString();
    }

    private static void saveCredential(JSONObject credentialRequest, AnoncredsResults.IssuerCreateCredentialResult credential) {
        CredentialUtils.storage.edit().putString(CredentialUtils.getCredentialJSONKeyFromCredentialRequest(credentialRequest), credential.getCredentialJson()).apply();
        CredentialUtils.storage.edit().putString(CredentialUtils.getCredentialRevocIDKeyFromCredentialRequest(credentialRequest), credential.getRevocId()).apply();
        CredentialUtils.storage.edit().putString(CredentialUtils.getCredentialRevocRegDeltaJSONKeyFromCredentialRequest(credentialRequest), credential.getRevocRegDeltaJson()).apply();
    }

    /**
     * @param csWallet = result of WalletUtils.openCSWallet
     * @param csoInfodsoDistrictProofRequest = result of ProofUtils.createCSOInfoAndDSODistrictProofRequest
     * @param revealed = whether to reveal the attributes or not
     * @return the predicates, obtained from the credentials in the wallet, to generate a proof for CSO Info and DSO District information.
     * @throws IndyException
     */
    public static JSONObject getPredicatesForCSOInfoDSODistrictProofRequest(Wallet csWallet, JSONObject csoInfodsoDistrictProofRequest, boolean revealed) throws IndyException {
        JSONObject credentialPredicates = null;
        try {
            CredentialsSearchForProofReq credentialsSearch = CredentialsSearchForProofReq.open(csWallet, csoInfodsoDistrictProofRequest.toString(), null).get();
            JSONObject credentialForCSO = CredentialUtils.getProofCredentialsForReferent(credentialsSearch, ProofUtils.CSO_INFO_PROOF_REQUEST_REFERENT);
            JSONObject credentialForDSODistrict = CredentialUtils.getProofCredentialsForReferent(credentialsSearch, ProofUtils.DSO_DISTRICT_PROOF_REQUEST_REFERENT);
            credentialsSearch.close();

            credentialPredicates = new JSONObject()
                    .put("self_attested_attributes", new JSONObject())
                    .put("requested_predicates", new JSONObject())
                    .put("requested_attributes", new JSONObject()
                            .put(ProofUtils.CSO_INFO_PROOF_REQUEST_REFERENT, new JSONObject()
                                    .put("cred_id", credentialForCSO.getString("referent"))
                                    .put("revealed", revealed)
                            )
                            .put(ProofUtils.DSO_DISTRICT_PROOF_REQUEST_REFERENT, new JSONObject()
                                    .put("cred_id", credentialForDSODistrict.getString("referent"))
                                    .put("revealed", revealed)
                            )
                    );
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return credentialPredicates;
    }

    /**
     * @param evWallet = result of WalletUtils.openEVWallet
     * @param erChargingProofRequest = result of ProofUtils.createERChargingProofRequest
     * @param revealed = whether to reveal the attributes or not
     * @return the predicates, obtained from the credentials in the wallet, to generate a proof to prove authorisation to charge.
     * @throws IndyException
     */
    public static JSONObject getPredicatesForERChargingProofRequest(Wallet evWallet, JSONObject erChargingProofRequest, boolean revealed) throws IndyException {
        JSONObject credentialPredicates = null;
        try {
            CredentialsSearchForProofReq credentialsSearch = CredentialsSearchForProofReq.open(evWallet, erChargingProofRequest.toString(), null).get();
            JSONObject credentialERProof = CredentialUtils.getProofCredentialsForReferent(credentialsSearch, ProofUtils.ER_CHARGING_PROOF_REQUEST_REFERENT);
            credentialsSearch.close();

            credentialPredicates = new JSONObject()
                    .put("self_attested_attributes", new JSONObject())
                    .put("requested_predicates", new JSONObject())
                    .put("requested_attributes", new JSONObject()
                            .put(ProofUtils.ER_CHARGING_PROOF_REQUEST_REFERENT, new JSONObject()
                                    .put("cred_id", credentialERProof.getString("referent"))
                                    .put("revealed", revealed)
                            )
                    );
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return credentialPredicates;
    }

    private static JSONObject getProofCredentialsForReferent(CredentialsSearchForProofReq searchHandle, String predicateReferent) throws IndyException, ExecutionException, InterruptedException, JSONException {
        return new JSONObject(new JSONArray(searchHandle.fetchNextCredentials(predicateReferent, 1).get()).getJSONObject(0).getString("cred_info"));
    }
}
