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

    private static final String CSO_INFO_CREDENTIAL_DEFINITION_NAME = "CSO-Info-Credential-Definition-Revocable";

    private static final String DSO_DISTRICT_CREDENTIAL_DEFINITION_NAME = "DSO-District-Credential-Definition-Revocable";

    private static final String ER_CHARGING_CREDENTIAL_DEFINITION_NAME = "ER-Charging-Credential-Definition-Revocable";

    private static SharedPreferences storage;

    CredentialDefinitionUtils() {}

    static void initWithAppContext(Context context) {
        CredentialDefinitionUtils.storage = context.getSharedPreferences("credential-defs", Context.MODE_PRIVATE);
    }

    /**
     * @param csoDID = result of DIDUtils.createAndWriteCSODID
     * @param csoWallet = result of WalletUtils.openCSOWallet
     * @param credentialSchema = result of calling getJSONObject("object") on the JSON returned by CredentialSchemaUtils.readCredentialSchemaFromLedger for the CSO Info credential schema
     * @param targetPool = result of PoolUtils.connectToSOFIEPool
     * @return the definition of the credential containing information about a CS's CSO.
     * @throws IndyException
     * @throws LedgerWriteException if the credential definition already exists on the ledger
     */
    public static AnoncredsResults.IssuerCreateAndStoreCredentialDefResult createAndWriteCSOInfoCredentialDefinition(String csoDID, Wallet csoWallet, JSONObject credentialSchema, Pool targetPool) throws IndyException, LedgerWriteException {

        AnoncredsResults.IssuerCreateAndStoreCredentialDefResult credentialDefinition = CredentialDefinitionUtils.retrieveCredentialDefinition(CredentialDefinitionUtils.CSO_INFO_CREDENTIAL_DEFINITION_NAME);

        try {
            if (credentialDefinition != null) {
                Log.d(CredentialDefinitionUtils.class.toString(), "Credential definition retrieved from cache.");
            } else {
                Log.d(CredentialDefinitionUtils.class.toString(), "Credential definition not found in cache. Generating...");
                JSONObject credentialDefinitionConfig = new JSONObject().put("support_revocation", true);
                credentialDefinition = Anoncreds.issuerCreateAndStoreCredentialDef(csoWallet, csoDID, credentialSchema.toString(), CredentialDefinitionUtils.CSO_INFO_CREDENTIAL_DEFINITION_NAME, null, credentialDefinitionConfig.toString()).get();
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

    /**
     * @param dsoDID = result of DIDUtils.createAndWriteDSODID
     * @param dsoWallet = result of WalletUtils.openDSOWallet
     * @param credentialSchema = result of calling getJSONObject("object") on the JSON returned by CredentialSchemaUtils.readCredentialSchemaFromLedger for the DSO District Info credential schema
     * @param targetPool = result of PoolUtils.connectToSOFIEPool
     * @return the definition of the credential containing information about a CS's DSO district.
     * @throws IndyException
     * @throws LedgerWriteException if the credential definition already exists on the ledger
     */
    public static AnoncredsResults.IssuerCreateAndStoreCredentialDefResult createAndWriteDSODistrictCredentialDefinition(String dsoDID, Wallet dsoWallet, JSONObject credentialSchema, Pool targetPool) throws IndyException, LedgerWriteException {

        AnoncredsResults.IssuerCreateAndStoreCredentialDefResult credentialDefinition = CredentialDefinitionUtils.retrieveCredentialDefinition(CredentialDefinitionUtils.DSO_DISTRICT_CREDENTIAL_DEFINITION_NAME);

        try {
            if (credentialDefinition != null) {
                Log.d(CredentialDefinitionUtils.class.toString(), "Credential definition retrieved from cache.");
            } else {
                Log.d(CredentialDefinitionUtils.class.toString(), "Credential definition not found in cache. Generating...");
                JSONObject credentialDefinitionConfig = new JSONObject().put("support_revocation", true);
                credentialDefinition = Anoncreds.issuerCreateAndStoreCredentialDef(dsoWallet, dsoDID, credentialSchema.toString(), CredentialDefinitionUtils.DSO_DISTRICT_CREDENTIAL_DEFINITION_NAME, null, credentialDefinitionConfig.toString()).get();
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

    /**
     * @param erDID = result of DIDUtils.createAndWriteERDID
     * @param erWallet = result of WalletUtils.openERWallet
     * @param credentialSchema = result of calling getJSONObject("object") on the JSON returned by CredentialSchemaUtils.readCredentialSchemaFromLedger for the ER Charging credential schema
     * @param targetPool = result of PoolUtils.connectToSOFIEPool
     * @return the definition of the credential containing information about an EV's charging authorisation.
     * @throws IndyException
     * @throws LedgerWriteException if the credential definition already exists on the ledger
     */
    public static AnoncredsResults.IssuerCreateAndStoreCredentialDefResult createAndWriteERChargingCredentialDefinition(String erDID, Wallet erWallet, JSONObject credentialSchema, Pool targetPool) throws IndyException, LedgerWriteException {

        AnoncredsResults.IssuerCreateAndStoreCredentialDefResult credentialDefinition = CredentialDefinitionUtils.retrieveCredentialDefinition(CredentialDefinitionUtils.ER_CHARGING_CREDENTIAL_DEFINITION_NAME);

        try {
            if (credentialDefinition != null) {
                Log.d(CredentialDefinitionUtils.class.toString(), "Credential definition retrieved from cache.");
            } else {
                Log.d(CredentialDefinitionUtils.class.toString(), "Credential definition not found in cache. Generating...");
                JSONObject credentialDefinitionConfig = new JSONObject().put("support_revocation", true);
                credentialDefinition = Anoncreds.issuerCreateAndStoreCredentialDef(erWallet, erDID, credentialSchema.toString(), CredentialDefinitionUtils.ER_CHARGING_CREDENTIAL_DEFINITION_NAME, null, credentialDefinitionConfig.toString()).get();
                CredentialDefinitionUtils.saveCredentialDefinition(CredentialDefinitionUtils.ER_CHARGING_CREDENTIAL_DEFINITION_NAME, credentialDefinition);
                Log.d(CredentialDefinitionUtils.class.toString(), "Credential definition saved in cache.");
                JSONObject credentialDefinitionNymRequest = new JSONObject(Ledger.buildCredDefRequest(erDID, credentialDefinition.getCredDefJson()).get());
                JSONObject requestResult = new JSONObject(Ledger.signAndSubmitRequest(targetPool, erWallet, erDID, credentialDefinitionNymRequest.toString()).get());

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

    /**
     * @param readerDID = whatever DID is to be used to retrieve the information from the ledger (it does not really matter since it is a read operation)
     * @param credentialDefinitionID = result of getCredDefId called on an instance of AnoncredsResults.IssuerCreateAndStoreCredentialDefResult (as returned in the other public methods in this class)
     * @param targetPool = result of PoolUtils.connectToSOFIEPool
     * @return the credential definition of the given ID retrieved from the ledger.
     * @throws IndyException
     */
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
