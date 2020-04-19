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
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.ExecutionException;

public final class CredentialSchemaUtils {


    public static final String CSO_INFO_CREDENTIAL_SCHEMA_NAME = "CSO-Info-Credential-Schema";
    private static final String CSO_INFO_CREDENTIAL_SCHEMA_VERSION = "2.1";
    static final String[] CSO_INFO_CREDENTIAL_SCHEMA_ATTRIBUTES = new String[]{"cso", "valid_from", "valid_until"};

    public static final String DSO_DISTRICT_CREDENTIAL_SCHEMA_NAME = "DSO-District-Credential-Schema";
    private static final String DSO_DISTRICT_CREDENTIAL_SCHEMA_VERSION = "2.1";
    static final String[] DSO_DISTRICT_CREDENTIAL_SCHEMA_ATTRIBUTES = new String[]{"district_id", "valid_from", "valid_until"};

    public static final String ER_CHARGING_CREDENTIAL_SCHEMA_NAME = "ER-Charging-Credential-Schema";
    private static final String ER_CHARGING_CREDENTIAL_SCHEMA_VERSION = "3.1";
    static final String[] ER_CHARGING_CREDENTIAL_SCHEMA_ATTRIBUTES = new String[]{"ev", "valid_from", "valid_until"};

    private static SharedPreferences storage;

    private CredentialSchemaUtils() {}

    static void initWithAppContext(Context context) {
        CredentialSchemaUtils.storage = context.getSharedPreferences("credential-schemas", Context.MODE_PRIVATE);
    }

    /**
     * @param csoDID = result of DIDUtils.createAndWriteCSODID
     * @param csoWallet = result of WalletUtils.openCSOWallet
     * @param targetPool = result of PoolUtils.connectToSOFIEPool
     * @return the schema of the credential containing information about a CS's CSO.
     * @throws IndyException
     * @throws LedgerWriteException if the credential schema already exists on the ledger
     */
    public static AnoncredsResults.IssuerCreateSchemaResult createAndWriteCSOInfoCredentialSchema(String csoDID, Wallet csoWallet, Pool targetPool) throws IndyException, LedgerWriteException {

        AnoncredsResults.IssuerCreateSchemaResult credentialSchema = CredentialSchemaUtils.retrieveCredentialSchema(CredentialSchemaUtils.CSO_INFO_CREDENTIAL_SCHEMA_NAME);

        try {
            if (credentialSchema != null) {
                Log.d(CredentialSchemaUtils.class.toString(), "Credential schema retrieved from cache.");
            } else {
                Log.d(CredentialSchemaUtils.class.toString(), "Credential schema not found in cache. Generating...");
                JSONArray attributesAsJSON = new JSONArray(CredentialSchemaUtils.CSO_INFO_CREDENTIAL_SCHEMA_ATTRIBUTES);
                credentialSchema = Anoncreds.issuerCreateSchema(csoDID, CredentialSchemaUtils.CSO_INFO_CREDENTIAL_SCHEMA_NAME, CredentialSchemaUtils.CSO_INFO_CREDENTIAL_SCHEMA_VERSION, attributesAsJSON.toString()).get();
                CredentialSchemaUtils.saveCredentialSchema(CredentialSchemaUtils.CSO_INFO_CREDENTIAL_SCHEMA_NAME, credentialSchema);
                Log.d(CredentialSchemaUtils.class.toString(), "Credential schema saved in cache.");
                JSONObject csoInfoCredentialsSchemaNymRequest = new JSONObject(Ledger.buildSchemaRequest(csoDID, credentialSchema.getSchemaJson()).get());
                JSONObject requestResult = new JSONObject(Ledger.signAndSubmitRequest(targetPool, csoWallet, csoDID, csoInfoCredentialsSchemaNymRequest.toString()).get());

                if (!LedgerUtils.isLedgerResponseValid(requestResult)) {
                    String ledgerError = LedgerUtils.getErrorMessage(requestResult);
                    if (!ledgerError.contains("UnauthorizedClientRequest")) {
                        throw new LedgerWriteException(ledgerError);
                    }
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return credentialSchema;
    }

    /**
     * @param dsoDID = result of DIDUtils.createAndWriteDSODID
     * @param dsoWallet = result of WalletUtils.openDSOWallet
     * @param targetPool = result of PoolUtils.connectToSOFIEPool
     * @return the schema of the credential containing information about a CS's DSO district.
     * @throws IndyException
     * @throws LedgerWriteException if the credential schema already exists on the ledger
     */
    public static AnoncredsResults.IssuerCreateSchemaResult createAndWriteDSODistrictCredentialSchema(String dsoDID, Wallet dsoWallet, Pool targetPool) throws IndyException, LedgerWriteException {

        AnoncredsResults.IssuerCreateSchemaResult credentialSchema = CredentialSchemaUtils.retrieveCredentialSchema(CredentialSchemaUtils.DSO_DISTRICT_CREDENTIAL_SCHEMA_NAME);

        try {
            if (credentialSchema != null) {
                Log.d(CredentialSchemaUtils.class.toString(), "Credential schema retrieved from cache.");
            } else {
                Log.d(CredentialSchemaUtils.class.toString(), "Credential schema not found in cache. Generating...");
                JSONArray attributesAsJSON = new JSONArray(CredentialSchemaUtils.DSO_DISTRICT_CREDENTIAL_SCHEMA_ATTRIBUTES);
                credentialSchema = Anoncreds.issuerCreateSchema(dsoDID, CredentialSchemaUtils.DSO_DISTRICT_CREDENTIAL_SCHEMA_NAME, CredentialSchemaUtils.DSO_DISTRICT_CREDENTIAL_SCHEMA_VERSION, attributesAsJSON.toString()).get();
                CredentialSchemaUtils.saveCredentialSchema(CredentialSchemaUtils.DSO_DISTRICT_CREDENTIAL_SCHEMA_NAME, credentialSchema);
                Log.d(CredentialSchemaUtils.class.toString(), "Credential schema saved in cache.");
                JSONObject credentialsSchemaNymRequest = new JSONObject(Ledger.buildSchemaRequest(dsoDID, credentialSchema.getSchemaJson()).get());
                JSONObject requestResult = new JSONObject(Ledger.signAndSubmitRequest(targetPool, dsoWallet, dsoDID, credentialsSchemaNymRequest.toString()).get());

                if (!LedgerUtils.isLedgerResponseValid(requestResult)) {
                    String ledgerError = LedgerUtils.getErrorMessage(requestResult);
                    if (!ledgerError.contains("UnauthorizedClientRequest")) {
                        throw new LedgerWriteException(ledgerError);
                    }
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return credentialSchema;
    }

    /**
     * @param erDID = result of DIDUtils.createAndWriteERDID
     * @param erWallet = result of WalletUtils.openERWallet
     * @param targetPool = result of PoolUtils.connectToSOFIEPool
     * @return the schema of the credential containing information about an EV's charging authorisation.
     * @throws IndyException
     * @throws LedgerWriteException if the credential schema already exists on the ledger
     */
    public static AnoncredsResults.IssuerCreateSchemaResult createAndWriteERChargingCredentialSchema(String erDID, Wallet erWallet, Pool targetPool) throws IndyException, LedgerWriteException {

        AnoncredsResults.IssuerCreateSchemaResult credentialSchema = CredentialSchemaUtils.retrieveCredentialSchema(CredentialSchemaUtils.ER_CHARGING_CREDENTIAL_SCHEMA_NAME);

        try {
            if (credentialSchema != null) {
                Log.d(CredentialSchemaUtils.class.toString(), "Credential schema retrieved from cache.");
            } else {
                Log.d(CredentialSchemaUtils.class.toString(), "Credential schema not found in cache. Generating...");
                JSONArray attributesAsJSON = new JSONArray(CredentialSchemaUtils.ER_CHARGING_CREDENTIAL_SCHEMA_ATTRIBUTES);
                credentialSchema = Anoncreds.issuerCreateSchema(erDID, CredentialSchemaUtils.ER_CHARGING_CREDENTIAL_SCHEMA_NAME, CredentialSchemaUtils.ER_CHARGING_CREDENTIAL_SCHEMA_VERSION, attributesAsJSON.toString()).get();
                CredentialSchemaUtils.saveCredentialSchema(CredentialSchemaUtils.ER_CHARGING_CREDENTIAL_SCHEMA_NAME, credentialSchema);
                Log.d(CredentialSchemaUtils.class.toString(), "Credential schema saved in cache.");
                JSONObject credentialsSchemaNymRequest = new JSONObject(Ledger.buildSchemaRequest(erDID, credentialSchema.getSchemaJson()).get());
                JSONObject requestResult = new JSONObject(Ledger.signAndSubmitRequest(targetPool, erWallet, erDID, credentialsSchemaNymRequest.toString()).get());

                if (!LedgerUtils.isLedgerResponseValid(requestResult)) {
                    String ledgerError = LedgerUtils.getErrorMessage(requestResult);
                    if (!ledgerError.contains("UnauthorizedClientRequest")) {
                        throw new LedgerWriteException(ledgerError);
                    }
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return credentialSchema;
    }

    private static AnoncredsResults.IssuerCreateSchemaResult retrieveCredentialSchema(String credentialSchemaName) {
        String credentialSchemaID = CredentialSchemaUtils.storage.getString(CredentialSchemaUtils.getCredentialSchemaIDKeyFromName(credentialSchemaName), null);
        String credentialSchemaJSONSerialised = CredentialSchemaUtils.storage.getString(CredentialSchemaUtils.getCredentialSchemaJSONKeyFromName(credentialSchemaName), null);

        if (credentialSchemaID == null || credentialSchemaJSONSerialised == null) {
            return null;
        }
        return new AnoncredsResults.IssuerCreateSchemaResult(credentialSchemaID, credentialSchemaJSONSerialised);
    }

    private static String getCredentialSchemaIDKeyFromName(String credentialName) {
        return new StringBuilder().append(credentialName).append(":").append("id").toString();
    }

    private static String getCredentialSchemaJSONKeyFromName(String credentialName) {
        return new StringBuilder().append(credentialName).append(":").append("json").toString();
    }

    private static void saveCredentialSchema(String credentialSchemaName, AnoncredsResults.IssuerCreateSchemaResult credentialSchema) {
        CredentialSchemaUtils.storage.edit().putString(CredentialSchemaUtils.getCredentialSchemaIDKeyFromName(credentialSchemaName), credentialSchema.getSchemaId()).apply();
        CredentialSchemaUtils.storage.edit().putString(CredentialSchemaUtils.getCredentialSchemaJSONKeyFromName(credentialSchemaName), credentialSchema.getSchemaJson()).apply();
    }

    /**
     * @param readerDID = whatever DID is to be used to retrieve the information from the ledger (it does not really matter since it is a read operation)
     * @param credentialSchemaID = result of getSchemaId called on an instance of AnoncredsResults.IssuerCreateAndStoreCredentialSchemaResult (as returned in the other public methods in this class)
     * @param targetPool = result of PoolUtils.connectToSOFIEPool
     * @return the credential schema of the given ID retrieved from the ledger.
     * @throws IndyException
     */
    public static JSONObject readCredentialSchemaFromLedger(String readerDID, String credentialSchemaID, Pool targetPool) throws IndyException {
        JSONObject readResult = null;
        try {
            JSONObject credentialSchemaNymGetRequest = new JSONObject(Ledger.buildGetSchemaRequest(readerDID, credentialSchemaID).get());
            JSONObject requestResult = new JSONObject(Ledger.submitRequest(targetPool, credentialSchemaNymGetRequest.toString()).get());
            LedgerResults.ParseResponseResult parsedResult = Ledger.parseGetSchemaResponse(requestResult.toString()).get();
            readResult = new JSONObject().put("object", new JSONObject(parsedResult.getObjectJson())).put("id", parsedResult.getId());
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
        return readResult;
    }
}
