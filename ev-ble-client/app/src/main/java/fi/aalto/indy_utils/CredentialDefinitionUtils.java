package fi.aalto.indy_utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.hyperledger.indy.sdk.IndyException;
import org.hyperledger.indy.sdk.anoncreds.Anoncreds;
import org.hyperledger.indy.sdk.anoncreds.AnoncredsResults;
import org.hyperledger.indy.sdk.ledger.Ledger;
import org.hyperledger.indy.sdk.ledger.LedgerResults;
import org.hyperledger.indy.sdk.pool.Pool;
import org.hyperledger.indy.sdk.wallet.Wallet;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.ExecutionException;

public final class CredentialDefinitionUtils {

    private static final String CSO_INFO_CREDENTIAL_DEFINITION_NAME = "CSO-Info-Credential-Definition";

    private static final String DSO_DISTRICT_CREDENTIAL_DEFINITION_NAME = "DSO-District-Credential-Definition";

    private static SharedPreferences storage;

    CredentialDefinitionUtils() {}

    static void initWithAppContext(Context context) {
        CredentialDefinitionUtils.storage = context.getSharedPreferences("credential-defs", Context.MODE_PRIVATE);
    }

    public static AnoncredsResults.IssuerCreateAndStoreCredentialDefResult createAndWriteCSOInfoCredentialDefinition(String csoDID, Wallet csoWallet, JSONObject credentialSchema, Pool targetPool) throws IndyException, LedgerWriteException {

        AnoncredsResults.IssuerCreateAndStoreCredentialDefResult credentialDefinition = CredentialDefinitionUtils.retrieveCredentialDefinition(CredentialDefinitionUtils.CSO_INFO_CREDENTIAL_DEFINITION_NAME);

        try {
            if (credentialDefinition != null) {
                Log.d(CredentialDefinitionUtils.class.toString(), "Credential definition retrieved from cache.");
            } else {
                Log.d(CredentialDefinitionUtils.class.toString(), "Credential definition not found in cache. Generating...");
                credentialDefinition = Anoncreds.issuerCreateAndStoreCredentialDef(csoWallet, csoDID, credentialSchema.toString(), CredentialDefinitionUtils.CSO_INFO_CREDENTIAL_DEFINITION_NAME, null, null).get();
                CredentialDefinitionUtils.saveCredentialDefinition(CredentialDefinitionUtils.CSO_INFO_CREDENTIAL_DEFINITION_NAME, credentialDefinition);
                Log.d(CredentialDefinitionUtils.class.toString(), "Credential definition saved in cache.");
                JSONObject csoInfoCredentialDefinitionNymRequest = new JSONObject(Ledger.buildCredDefRequest(csoDID, credentialDefinition.getCredDefJson()).get());
                JSONObject requestResult = new JSONObject(Ledger.signAndSubmitRequest(targetPool, csoWallet, csoDID, csoInfoCredentialDefinitionNymRequest.toString()).get());

                if (!LedgerUtils.isLedgerResponseValid(requestResult)) {
                    throw new LedgerWriteException(LedgerUtils.getErrorMessage(requestResult));
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
        return credentialDefinition;
    }

    public static AnoncredsResults.IssuerCreateAndStoreCredentialDefResult createAndWriteDSODistrictCredentialDefinition(String dsoDID, Wallet dsoWallet, JSONObject credentialSchema, Pool targetPool) throws IndyException, LedgerWriteException {

        AnoncredsResults.IssuerCreateAndStoreCredentialDefResult credentialDefinition = CredentialDefinitionUtils.retrieveCredentialDefinition(CredentialDefinitionUtils.DSO_DISTRICT_CREDENTIAL_DEFINITION_NAME);

        try {
            if (credentialDefinition != null) {
                Log.d(CredentialDefinitionUtils.class.toString(), "Credential definition retrieved from cache.");
            } else {
                Log.d(CredentialDefinitionUtils.class.toString(), "Credential definition not found in cache. Generating...");
                credentialDefinition = Anoncreds.issuerCreateAndStoreCredentialDef(dsoWallet, dsoDID, credentialSchema.toString(), CredentialDefinitionUtils.DSO_DISTRICT_CREDENTIAL_DEFINITION_NAME, null, null).get();
                CredentialDefinitionUtils.saveCredentialDefinition(CredentialDefinitionUtils.DSO_DISTRICT_CREDENTIAL_DEFINITION_NAME, credentialDefinition);
                Log.d(CredentialDefinitionUtils.class.toString(), "Credential definition saved in cache.");
                JSONObject credentialDefinitionNymRequest = new JSONObject(Ledger.buildCredDefRequest(dsoDID, credentialDefinition.getCredDefJson()).get());
                JSONObject requestResult = new JSONObject(Ledger.signAndSubmitRequest(targetPool, dsoWallet, dsoDID, credentialDefinitionNymRequest.toString()).get());

                if (!LedgerUtils.isLedgerResponseValid(requestResult)) {
                    throw new LedgerWriteException(LedgerUtils.getErrorMessage(requestResult));
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
        return credentialDefinition;
    }

    private static AnoncredsResults.IssuerCreateAndStoreCredentialDefResult retrieveCredentialDefinition(String credentialDefinitionName) {
        String credentialDefinitionID = CredentialDefinitionUtils.storage.getString(CredentialDefinitionUtils.getCredentialDefinitionIDKeyFromName(credentialDefinitionName), null);
        String credentialDefinitionJSONSerialised = CredentialDefinitionUtils.storage.getString(CredentialDefinitionUtils.getCredentialDefinitionJSONKeyFromName(credentialDefinitionName), null);

        if (credentialDefinitionID == null || credentialDefinitionJSONSerialised == null) {
            return null;
        }
        return new AnoncredsResults.IssuerCreateAndStoreCredentialDefResult(credentialDefinitionID, credentialDefinitionJSONSerialised);
    }

    private static String getCredentialDefinitionIDKeyFromName(String credentialName) {
        return new StringBuilder().append(credentialName).append(":").append("id").toString();
    }

    private static String getCredentialDefinitionJSONKeyFromName(String credentialName) {
        return new StringBuilder().append(credentialName).append(":").append("json").toString();
    }

    private static void saveCredentialDefinition(String credentialDefinitionName, AnoncredsResults.IssuerCreateAndStoreCredentialDefResult credentialDefinition) {
        CredentialDefinitionUtils.storage.edit().putString(CredentialDefinitionUtils.getCredentialDefinitionIDKeyFromName(credentialDefinitionName), credentialDefinition.getCredDefId()).apply();
        CredentialDefinitionUtils.storage.edit().putString(CredentialDefinitionUtils.getCredentialDefinitionJSONKeyFromName(credentialDefinitionName), credentialDefinition.getCredDefJson()).apply();
    }

    public static JSONObject readCredentialDefinitionFromLedger(String readerDID, String credentialDefinitionID, Pool targetPool) throws IndyException {
        JSONObject readResult = null;
        try {
            JSONObject credentialDefinitionNymGetRequest = new JSONObject(Ledger.buildGetCredDefRequest(readerDID, credentialDefinitionID).get());
            JSONObject requestResult = new JSONObject(Ledger.submitRequest(targetPool, credentialDefinitionNymGetRequest.toString()).get());
            LedgerResults.ParseResponseResult parsedResult = Ledger.parseGetCredDefResponse(requestResult.toString()).get();
            readResult = new JSONObject().put("object", new JSONObject(parsedResult.getObjectJson())).put("id", parsedResult.getId());
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
        return readResult;
    }
}
