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

    static final int CS_DISTRICT_ID = 1;

    private static SharedPreferences storage;

    private CredentialUtils() {}

    static void initWithAppContext(Context context) {
        CredentialUtils.storage = context.getSharedPreferences("credentials", Context.MODE_PRIVATE);
    }

    // Master secrets

    public static String createAndSaveCSMasterSecret(Wallet csWallet) throws IndyException {
        try {
            Anoncreds.proverCreateMasterSecret(csWallet, CredentialUtils.CS_MASTER_SECRET_ID).get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException ignored) {}        // Master secret already exists
        return CredentialUtils.CS_MASTER_SECRET_ID;
    }

    //Credential offers

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


    public static AnoncredsResults.IssuerCreateCredentialResult createCSOInfoCredential(Wallet csoWallet, JSONObject csoInfoCredentialOffer, JSONObject csoInfoCredentialRequest, String csoDID) throws IndyException {
        AnoncredsResults.IssuerCreateCredentialResult credential = CredentialUtils.retrieveCredentialForCredentialRequest(csoInfoCredentialRequest);

        try {
            if (credential != null) {
                Log.d(CredentialUtils.class.toString(), "Credential retrieved from cache.");
            } else {
                Log.d(CredentialUtils.class.toString(), "Credential not found in cache. Generating...");
                JSONObject csoInfoCredentialContent = new JSONObject()
                        .put("CSO", new JSONObject()
                                .put("raw", csoDID)
                                .put("encoded", String.format("%d", csoDID.hashCode()))
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

    public static JSONObject getPredicatesForCSOInfoDSODistrictProofRequest(Wallet csWallet, JSONObject csoInfodsoDistrictProofRequest) throws IndyException {
        JSONObject credentialPredicates = null;
        try {
            CredentialsSearchForProofReq credentialsSearch = CredentialsSearchForProofReq.open(csWallet, csoInfodsoDistrictProofRequest.toString(), null).get();
            JSONObject credentialForCSOMin = CredentialUtils.getProofCredentialsForReferent(credentialsSearch, ProofUtils.CSO_INFO_PROOF_REQUEST_MIN_REFERENT);
            JSONObject credentialForCSOMax = CredentialUtils.getProofCredentialsForReferent(credentialsSearch, ProofUtils.CSO_INFO_PROOF_REQUEST_MAX_REFERENT);
            JSONObject credentialForDSODistrictMin = CredentialUtils.getProofCredentialsForReferent(credentialsSearch, ProofUtils.DSO_DISTRICT_PROOF_REQUEST_MIN_REFERENT);
            JSONObject credentialForDSODistrictMax = CredentialUtils.getProofCredentialsForReferent(credentialsSearch, ProofUtils.DSO_DISTRICT_PROOF_REQUEST_MAX_REFERENT);
            credentialsSearch.close();

            credentialPredicates = new JSONObject()
                    .put("self_attested_attributes", new JSONObject())
                    .put("requested_attributes", new JSONObject())
                    .put("requested_predicates", new JSONObject()
                            .put(ProofUtils.CSO_INFO_PROOF_REQUEST_MIN_REFERENT, new JSONObject()
                                    .put("cred_id", credentialForCSOMin.getString("referent"))
                            )
                            .put(ProofUtils.CSO_INFO_PROOF_REQUEST_MAX_REFERENT, new JSONObject()
                                    .put("cred_id", credentialForCSOMax.getString("referent"))
                            )
                            .put(ProofUtils.DSO_DISTRICT_PROOF_REQUEST_MIN_REFERENT, new JSONObject()
                                    .put("cred_id", credentialForDSODistrictMin.getString("referent"))
                            )
                            .put(ProofUtils.DSO_DISTRICT_PROOF_REQUEST_MAX_REFERENT, new JSONObject()
                                    .put("cred_id", credentialForDSODistrictMax.getString("referent"))
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
