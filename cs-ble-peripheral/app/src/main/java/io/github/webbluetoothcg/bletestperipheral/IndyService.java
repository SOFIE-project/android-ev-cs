package io.github.webbluetoothcg.bletestperipheral;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.hyperledger.indy.sdk.IndyException;
import org.hyperledger.indy.sdk.anoncreds.AnoncredsResults;
import org.hyperledger.indy.sdk.did.Did;
import org.hyperledger.indy.sdk.did.DidJSONParameters;
import org.hyperledger.indy.sdk.did.DidResults;
import org.hyperledger.indy.sdk.pool.Pool;
import org.hyperledger.indy.sdk.wallet.Wallet;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ResourceBundle;
import java.util.concurrent.ExecutionException;

import fi.aalto.indy_utils.CredentialDefinitionUtils;
import fi.aalto.indy_utils.CredentialSchemaUtils;
import fi.aalto.indy_utils.CredentialUtils;
import fi.aalto.indy_utils.CryptoUtils;
import fi.aalto.indy_utils.DIDUtils;
import fi.aalto.indy_utils.IndyUtils;
import fi.aalto.indy_utils.MessageSignatureException;
import fi.aalto.indy_utils.PoolUtils;
import fi.aalto.indy_utils.ProofUtils;
import fi.aalto.indy_utils.WalletUtils;

import static android.content.ContentValues.TAG;

public class IndyService extends Service {

    CommonUtils mCommonUtils;


    private Wallet csWallet;

    private DidResults.CreateAndStoreMyDidResult csDID;
    private DidResults.CreateAndStoreMyDidResult erDID;


    private JSONObject csoInfoCredentialSchemaFromLedger;
    private JSONObject dsoDistrictCredentialSchemaFromLedger;
    private JSONObject didCertifiedCredentialSchemaFromLedger;
    private JSONObject erChargingCredentialSchemaFromLedger;

    private JSONObject csoInfoCredentialDefFromLedger;
    private JSONObject erChargingCredentialDefFromLedger;
    private JSONObject dsoDistrictCredentialDefFromLedger;
    private JSONObject csDIDCertificationCredentialDefFromLedger;
    private JSONObject evDIDCertificationCredentialDefFromLedger;

    private JSONObject csoInfodsoDistrictProofRequest;
    private JSONObject erChargingProofRequest;

    private String csMasterSecretID;

    private Pool sofiePool;

    private String csDid1, evDiD, evVerkey;

    public static final String ACTION_INDY_INITIALIZED = "com.spire.bledemo.app.ACTION_INDY_INITIALIZED";


    // Worker thread to unblock the ble callbacks on binder thread
    private HandlerThread indyOperationHandlerThread = new HandlerThread("indyOperationHandlerThread");
    private Handler indyHandler;


    public String joinMessageParts(String... parts) {
        StringBuilder message = new StringBuilder();
        for(String part : parts) {
            message.append(part).append("|");
        }
        return message.toString();
    }


    public void readLedger(String erChargingCredentialSchemaId,String erChargingCredDefId, String didCertifiedCredentialSchemaId, String evDIDCertificationCredDefId){
        try {
            erChargingCredentialSchemaFromLedger = CredentialSchemaUtils.readCredentialSchemaFromLedger(erDID.getDid(), erChargingCredentialSchemaId, sofiePool);
            Log.i(this.getClass().toString(), String.format("ER-EV charging info credential schema fetched from ledger: %s", erChargingCredentialSchemaFromLedger));

            erChargingCredentialDefFromLedger = CredentialDefinitionUtils.readCredentialDefinitionFromLedger(erDID.getDid(), erChargingCredDefId, sofiePool);
            Log.i(this.getClass().toString(), String.format("ER-EV charging info credential definition fetched from ledger: %s", erChargingCredentialDefFromLedger));

            didCertifiedCredentialSchemaFromLedger = CredentialSchemaUtils.readCredentialSchemaFromLedger(erDID.getDid(), didCertifiedCredentialSchemaId, sofiePool);

            evDIDCertificationCredentialDefFromLedger = CredentialDefinitionUtils.readCredentialDefinitionFromLedger(erDID.getDid(), evDIDCertificationCredDefId, sofiePool);


        } catch (Exception e) {
            if (e instanceof  IndyException) {
                Log.e(this.getClass().toString(), ((IndyException) e).getSdkBacktrace());
                Log.e(this.getClass().toString(), ((IndyException) e).getSdkMessage());
                Log.e(this.getClass().toString(), String.format("%d", ((IndyException) e).getSdkErrorCode()));
            }
            e.printStackTrace();
        }
    }

    // CS function
    public byte[] createCsDid1() {

        try {

            String csoCredSchemaId = csoInfoCredentialSchemaFromLedger.getString("id");
            String dsoCredSchemaId = dsoDistrictCredentialSchemaFromLedger.getString("id");
            String csoCredDefId = csoInfoCredentialDefFromLedger.getString("id");
            String dsoCredDefId =  dsoDistrictCredentialDefFromLedger.getString("id") ;

            Log.i(TAG, csoCredSchemaId);
            Log.i(TAG, dsoCredSchemaId);
            Log.i(TAG,csoCredDefId );
            Log.i(TAG, dsoCredDefId );

            return joinMessageParts(csDID.getDid(), csDID.getVerkey(), csoCredSchemaId, dsoCredSchemaId, csoCredDefId, dsoCredDefId).getBytes();

        } catch (JSONException e) {
            e.printStackTrace();
        }

        return null;
    }

    private String[] splitMessage(String payload) {
        return payload.split("\\|");
    }


    // CS function
    public void parseEVDIDAndCSOProofRequest(byte[] emsg) {

        String msg = new String(emsg); // unboxMessage(emsg);
        try {
            String[] messageParts = splitMessage(msg);
            evDiD = messageParts[0];
            evVerkey = messageParts[1];
            String csoProofRequestString = messageParts[2];
            String erChargingCredentialSchemaId = messageParts[3];
            String erChargingCredDefId = messageParts[4];
            String didCertifiedCredentialSchemaId = messageParts[5];
            String evDIDCertificationCredDefId = messageParts[6];
            readLedger(erChargingCredentialSchemaId, erChargingCredDefId, didCertifiedCredentialSchemaId, evDIDCertificationCredDefId);
            csoInfodsoDistrictProofRequest = new JSONObject(csoProofRequestString);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // CS function
    public byte[] createCSDid2CSOProofAndEVCertificateProofRequest() {
        byte[] emessage = null;
        try {

            // 12. Proofs creation

            Log.i(this.getClass().toString(), "Creating proof for CSO Info + DSO district proof request...");
            JSONObject csoInfodsoDistrictProofRequestCredentialsRevealed = CredentialUtils.getPredicatesForCSOInfoDSODistrictCertifiedDIDProofRequest(csWallet, csoInfodsoDistrictProofRequest, true);

            JSONObject csoInfodsoDistrictProofRevealing = ProofUtils.createProofCSOInfoDSODistrictDIDCertifiedProofRequest(
                    csWallet,
                    csoInfodsoDistrictProofRequest,
                    csoInfodsoDistrictProofRequestCredentialsRevealed,
                    csMasterSecretID,
                    csoInfoCredentialSchemaFromLedger.getString("id"),
                    dsoDistrictCredentialSchemaFromLedger.getString("id"),
                    didCertifiedCredentialSchemaFromLedger.getString("id"),
                    csoInfoCredentialSchemaFromLedger.getJSONObject("object"),
                    dsoDistrictCredentialSchemaFromLedger.getJSONObject("object"),
                    didCertifiedCredentialSchemaFromLedger.getJSONObject("object"),
                    csoInfoCredentialDefFromLedger.getString("id"),
                    dsoDistrictCredentialDefFromLedger.getString("id"),
                    csDIDCertificationCredentialDefFromLedger.getString("id"),
                    csoInfoCredentialDefFromLedger.getJSONObject("object"),
                    dsoDistrictCredentialDefFromLedger.getJSONObject("object"),
                    csDIDCertificationCredentialDefFromLedger.getJSONObject("object")
            );
            Log.i(this.getClass().toString(), String.format("Proof for CSO Info + DSO district proof request created: %s", csoInfodsoDistrictProofRevealing.toString().length()));

            mCommonUtils.stopTimer();


            Log.i(this.getClass().toString(), "Creating EV charging credential proof request...");
            erChargingProofRequest = ProofUtils.createERChargingAndCertifiedDIDProofRequest(erDID.getDid(), erChargingCredentialDefFromLedger.getString("id"), evDIDCertificationCredentialDefFromLedger.getString("id"));
            Log.i(this.getClass().toString(), String.format("EV charging credential proof request created: %s", erChargingProofRequest.toString().length()));

            mCommonUtils.stopTimer();

            String message = csDID.getDid() + "|" + csoInfodsoDistrictProofRevealing.toString() + "|" + erChargingProofRequest.toString();
            emessage = boxMessage(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return emessage;
    }


    // cs function
    public boolean verifyErChargingProof(byte[] encryptedProof) {

        String erProofString = unboxMessage(encryptedProof);
        boolean isERChargingProofRevealingValid = false;
        try {
            JSONObject erChargingProofRevealing = new JSONObject(erProofString);

            Log.i(this.getClass().toString(), "Verifying proof for ER charging credential...");
            isERChargingProofRevealingValid = ProofUtils.verifyERChargingCertifiedDIDProofCrypto(
                    erChargingProofRequest,
                    erChargingProofRevealing,
                    erChargingCredentialSchemaFromLedger.getString("id"),
                    didCertifiedCredentialSchemaFromLedger.getString("id"),
                    erChargingCredentialSchemaFromLedger.getJSONObject("object"),
                    didCertifiedCredentialSchemaFromLedger.getJSONObject("object"),
                    erChargingCredentialDefFromLedger.getString("id"),
                    evDIDCertificationCredentialDefFromLedger.getString("id"),
                    erChargingCredentialDefFromLedger.getJSONObject("object"),
                    evDIDCertificationCredentialDefFromLedger.getJSONObject("object")
            );
            Log.i(this.getClass().toString(), String.format("Proof for ER charging credential verified with result: %b", isERChargingProofRevealingValid));

            boolean areEVProofValuesValid = ProofUtils.verifyERChargingCertifiedDIDProofValues(
                    erChargingProofRevealing,
                    1,
                    evDiD,
                    1
            );
            Log.w(this.getClass().toString(), String.format("Are EV proof values valid? %b", areEVProofValuesValid));

            mCommonUtils.stopTimer();
        } catch (JSONException | IndyException e) {
            e.printStackTrace();
        }

        return isERChargingProofRevealingValid;
    }


    public int verifyCommitment(byte[] signedCommitment) {
        String commitment = unboxMessage(signedCommitment);
        if (commitment.contains("stepValue")) {
            return 0;
        } else {
            return 10;
        }
    }


    public void sendchargeStartMessage() {

    }

    public void releaseMicroCharge() {

    }


    /*
     * Service life cycle functions
     */
    public class LocalBinder extends Binder {
        IndyService getService() {
            return IndyService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }


    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, releaseResources() is
        // invoked when the UI is disconnected from the Service.
        releaseResources();
        return super.onUnbind(intent);
    }


    private final IBinder mBinder = new LocalBinder();

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    public void initialize() {

        mCommonUtils = new CommonUtils();

        indyOperationHandlerThread.start();
        indyHandler = new Handler(indyOperationHandlerThread.getLooper());

//        indyHandler.post(new Runnable() {
//            @Override
//            public void run() {
//                initializeIndy();
//            }
//        });

        new IndyInitialisationTask().execute();

    }
    /*
    Offline tasks for Indy: intialize, create did and credentials, close wallets and connections
     */

    final class IndyInitialisationTask extends AsyncTask<Void, Void, Void> {
        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        protected Void doInBackground(Void... voids) {
            initializeIndy();
            return null;                // null must be returned, as in https://www.quora.com/Why-does-doInBackground-in-the-AsyncTask-class-need-to-return-null-even-though-it%E2%80%99s-returning-type-is-set-to-void/answer/Vishal-Ratna
        }
    }

    /*
    Full initialization requires (Only one mode is enough)
    0. Initialize indy, pool connection
    1. Wallet creation, opening CS, ER, CSO, DSO
    2. Did creation for CS, ER, CSO, DSO , stewart
    3. schema creation CSO, DSO
    4. schema definition with CSO,DSO did
    5. credential offer
    6. credential request with CS wallet master secret
    7. credential creation with offer and request
     */
    private void initializeIndy() {

        try {

            mCommonUtils.startTimer();

            // 1. Indy initialisation

            Log.i(this.getClass().toString(), "Initialising Indy context...");
            IndyUtils.initialise(getApplicationContext(), false);

            // 2. Wallets creation

            Log.i(this.getClass().toString(), "Creating EV, CSO, CS, DSO and steward wallets...");
            WalletUtils.createCSWallet();
            WalletUtils.createCSOWallet();
            WalletUtils.createDSOWallet();
            WalletUtils.createERWallet();
            WalletUtils.createStewardWallet();

            // 3. Wallets opening

            Log.i(this.getClass().toString(), "Opening CS wallet...");
            csWallet = WalletUtils.openCSWallet();
            Log.i(this.getClass().toString(), "Opening CSO wallet...");
            Wallet csoWallet = WalletUtils.openCSOWallet();
            Log.i(this.getClass().toString(), "Opening DSO wallet...");
            Wallet dsoWallet = WalletUtils.openDSOWallet();
            Log.i(this.getClass().toString(), "Opening ER wallet...");
            Wallet erWallet = WalletUtils.openERWallet();
            Log.i(this.getClass().toString(), "Opening steward wallet...");
            Wallet stewardWallet = WalletUtils.openStewardWallet();

            // 4. Pool configuration + connection

            Log.i(this.getClass().toString(), "Creating test pool configuration...");
            PoolUtils.createSOFIEPoolConfig();
            Log.i(this.getClass().toString(), "Test pool configuration created.");

            Log.i(this.getClass().toString(), "Connecting to SOFIE pool...");
            sofiePool = PoolUtils.connectToSOFIEPool();
            Log.i(this.getClass().toString(), "Connected to SOFIE pool.");


            // 5. DIDs creation

            Log.i(this.getClass().toString(), "Calculating CS DID...");
            csDID = DIDUtils.createCSDID(csWallet);
            Log.i(this.getClass().toString(), String.format("CS DID calculated: %s - %s", csDID.getDid(), csDID.getVerkey()));

            Log.i(this.getClass().toString(), "Calculating steward DID...");
            DidResults.CreateAndStoreMyDidResult stewardDID = DIDUtils.createStewardDID(stewardWallet);
            Log.i(this.getClass().toString(), String.format("CSO steward DID calculated: %s - %s", stewardDID.getDid(), stewardDID.getVerkey()));

            Log.i(this.getClass().toString(), "Calculating and writing on ledger CSO DID...");
            DidResults.CreateAndStoreMyDidResult csoDID = DIDUtils.createAndWriteCSODID(csoWallet, stewardWallet, stewardDID.getDid(), sofiePool);
            Log.i(this.getClass().toString(), String.format("CSO DID calculated and written on ledger: %s - %s", csoDID.getDid(), csoDID.getVerkey()));

            Log.i(this.getClass().toString(), "Calculating and writing on ledger DSO DID...");
            DidResults.CreateAndStoreMyDidResult dsoDID = DIDUtils.createAndWriteDSODID(dsoWallet, stewardWallet, stewardDID.getDid(), sofiePool);
            Log.i(this.getClass().toString(), String.format("DSO DID calculated and written on ledger: %s - %s", dsoDID.getDid(), dsoDID.getVerkey()));

            Log.i(this.getClass().toString(), "Calculating and writing on ledger ER DID...");
            erDID = DIDUtils.createAndWriteERDID(erWallet, stewardWallet, stewardDID.getDid(), sofiePool);
            Log.i(this.getClass().toString(), String.format("ER DID calculated and written on ledger: %s - %s", erDID.getDid(), erDID.getVerkey()));

            // 6. Credential schemas creation

            Log.i(this.getClass().toString(), "Creating and writing on ledger credential schema for CS-CSO info...");
            AnoncredsResults.IssuerCreateSchemaResult csoInfoCredentialSchema = CredentialSchemaUtils.createAndWriteCSOInfoCredentialSchema(csoDID.getDid(), csoWallet, sofiePool);
            Log.i(this.getClass().toString(), String.format("Credential schema for CS-CSO info created and written on ledger."));

            csoInfoCredentialSchemaFromLedger = CredentialSchemaUtils.readCredentialSchemaFromLedger(csoDID.getDid(), csoInfoCredentialSchema.getSchemaId(), sofiePool);
            Log.i(this.getClass().toString(), String.format("CS-CSO info credential schema fetched from ledger: %s", csoInfoCredentialSchemaFromLedger));

            Log.i(this.getClass().toString(), "Creating and writing on ledger credential schema for CS-DSO district info...");
            AnoncredsResults.IssuerCreateSchemaResult dsoDistrictCredentialSchema = CredentialSchemaUtils.createAndWriteDSODistrictCredentialSchema(dsoDID.getDid(), dsoWallet, sofiePool);
            Log.i(this.getClass().toString(), String.format("Credential schema for CS-DSO district info created and written on ledger."));

            dsoDistrictCredentialSchemaFromLedger = CredentialSchemaUtils.readCredentialSchemaFromLedger(dsoDID.getDid(), dsoDistrictCredentialSchema.getSchemaId(), sofiePool);
            Log.i(this.getClass().toString(), String.format("CS-DSO district info credential schema fetched from ledger: %s", dsoDistrictCredentialSchemaFromLedger));

            Log.w(this.getClass().toString(), "Creating and writing on ledger credential schema for DID certification...");
            AnoncredsResults.IssuerCreateSchemaResult didCertifiedCredentialSchema = CredentialSchemaUtils.createAndWriteCertifiedDIDCredentialSchema(erDID.getDid(), erWallet, sofiePool);
            didCertifiedCredentialSchemaFromLedger = CredentialSchemaUtils.readCredentialSchemaFromLedger(erDID.getDid(), didCertifiedCredentialSchema.getSchemaId(), sofiePool);


            // 7. Credential definitions creation

            Log.i(this.getClass().toString(), "Creating and writing on ledger credential definition for CS-CSO info...");
            AnoncredsResults.IssuerCreateAndStoreCredentialDefResult csoInfoCredentialDefinition = CredentialDefinitionUtils.createAndWriteCSOInfoCredentialDefinition(csoDID.getDid(), csoWallet, csoInfoCredentialSchemaFromLedger.getJSONObject("object"), sofiePool);
            csoInfoCredentialDefFromLedger = CredentialDefinitionUtils.readCredentialDefinitionFromLedger(csoDID.getDid(), csoInfoCredentialDefinition.getCredDefId(), sofiePool);

            Log.i(this.getClass().toString(), "Creating and writing on ledger credential definition for CS-DSO district info...");
            AnoncredsResults.IssuerCreateAndStoreCredentialDefResult dsoDistrictCredentialDefinition = CredentialDefinitionUtils.createAndWriteDSODistrictCredentialDefinition(dsoDID.getDid(), dsoWallet, dsoDistrictCredentialSchemaFromLedger.getJSONObject("object"), sofiePool);
            dsoDistrictCredentialDefFromLedger = CredentialDefinitionUtils.readCredentialDefinitionFromLedger(dsoDID.getDid(), dsoDistrictCredentialDefinition.getCredDefId(), sofiePool);

            Log.w(this.getClass().toString(), "Creating and writing on ledger credential definition for CS DID certification...");
            AnoncredsResults.IssuerCreateAndStoreCredentialDefResult csDIDCertificationCredentialDefinition = CredentialDefinitionUtils.createAndWriteCSCertifiedDIDCredentialDefinition(csoDID.getDid(), csoWallet, didCertifiedCredentialSchemaFromLedger.getJSONObject("object"), sofiePool);
            csDIDCertificationCredentialDefFromLedger = CredentialDefinitionUtils.readCredentialDefinitionFromLedger(csoDID.getDid(), csDIDCertificationCredentialDefinition.getCredDefId(), sofiePool);


            // 8. Credential offers creation

            Log.i(this.getClass().toString(), "Creating credential offer for CS-CSO info...");
            JSONObject csoInfoCredentialOffer = CredentialUtils.createCredentialOffer(csoWallet, csoInfoCredentialDefFromLedger.getString("id"));

            Log.i(this.getClass().toString(), "Creating credential offer for CS-DSO district district info...");
            JSONObject dsoDistrictCredentialOffer = CredentialUtils.createCredentialOffer(dsoWallet, dsoDistrictCredentialDefFromLedger.getString("id"));

            Log.w(this.getClass().toString(), "Creating credential offer for CS DID certification...");
            JSONObject csCertifiedDIDCredentialOffer = CredentialUtils.createCredentialOffer(csoWallet, csDIDCertificationCredentialDefFromLedger.getString("id"));


            // Creating wallet master secret
            Log.i(this.getClass().toString(), "Creating master secret for CS wallet...");
            csMasterSecretID = CredentialUtils.createAndSaveCSMasterSecret(csWallet);
            Log.i(this.getClass().toString(), String.format("Master secret for CS wallet created: %s", csMasterSecretID));


            // 9. Credential requests creation

            Log.i(this.getClass().toString(), "Creating credential request for CS-CSO info...");
            AnoncredsResults.ProverCreateCredentialRequestResult csoInfoCredentialRequest = CredentialUtils.createCSOInfoCredentialRequest(csWallet, csDID.getDid(), csoInfoCredentialOffer, csoInfoCredentialDefFromLedger.getJSONObject("object"), csMasterSecretID);
            Log.i(this.getClass().toString(), String.format("Credential request for CS-CSO info created: %s", csoInfoCredentialRequest.getCredentialRequestJson()));

            Log.i(this.getClass().toString(), "Creating credential request for CS-DSO district info...");
            AnoncredsResults.ProverCreateCredentialRequestResult dsoDistrictCredentialRequest = CredentialUtils.createDSODistrictCredentialRequest(csWallet, csDID.getDid(), dsoDistrictCredentialOffer, dsoDistrictCredentialDefFromLedger.getJSONObject("object"), csMasterSecretID);
            Log.i(this.getClass().toString(), String.format("Credential request for CS-DSO district info created: %s", dsoDistrictCredentialRequest.getCredentialRequestJson()));

            Log.w(this.getClass().toString(), "Creating credential request for CS DID certification...");
            AnoncredsResults.ProverCreateCredentialRequestResult csCertifiedDIDCredentialRequest = CredentialUtils.createCSCertifiedDIDCredentialRequest(csWallet, csDID.getDid(), csCertifiedDIDCredentialOffer, csDIDCertificationCredentialDefFromLedger.getJSONObject("object"), csMasterSecretID);

            // 10. Credentials creation

            Log.i(this.getClass().toString(), "Creating credential for CS-CSO info...");
            AnoncredsResults.IssuerCreateCredentialResult csoInfoCredential = CredentialUtils.createCSOInfoCredential(csoWallet, csoInfoCredentialOffer, new JSONObject(csoInfoCredentialRequest.getCredentialRequestJson()), csoDID.getDid());
            Log.i(this.getClass().toString(), String.format("Credential for CS-CSO info created: %s", csoInfoCredential.getCredentialJson()));

            Log.i(this.getClass().toString(), "Saving credential for CS-CSO info into CS wallet...");
            WalletUtils.saveCredential(csWallet, new JSONObject(csoInfoCredentialRequest.getCredentialRequestMetadataJson()), new JSONObject(csoInfoCredential.getCredentialJson()), csoInfoCredentialDefFromLedger.getJSONObject("object"), csoInfoCredential.getRevocRegDeltaJson() != null ? new JSONObject(csoInfoCredential.getRevocRegDeltaJson()) : null);
            Log.i(this.getClass().toString(), "Credential for CS-CSO info saved into CS wallet");

            Log.i(this.getClass().toString(), "Creating credential for CS-DSO district info...");
            AnoncredsResults.IssuerCreateCredentialResult dsoDistrictCredential = CredentialUtils.createDSODistrictCredential(dsoWallet, dsoDistrictCredentialOffer, new JSONObject(dsoDistrictCredentialRequest.getCredentialRequestJson()));
            Log.i(this.getClass().toString(), String.format("Credential for CS-DSO district info created: %s", dsoDistrictCredential.getCredentialJson()));

            Log.i(this.getClass().toString(), "Saving credential for CS-DSO district info into CS wallet...");
            WalletUtils.saveCredential(csWallet, new JSONObject(dsoDistrictCredentialRequest.getCredentialRequestMetadataJson()), new JSONObject(dsoDistrictCredential.getCredentialJson()), dsoDistrictCredentialDefFromLedger.getJSONObject("object"), dsoDistrictCredential.getRevocRegDeltaJson() != null ? new JSONObject(dsoDistrictCredential.getRevocRegDeltaJson()) : null);
            Log.i(this.getClass().toString(), "Credential for CS-DSO district info saved into CS wallet");

            Log.w(this.getClass().toString(), "Creating credential for CS certified DID...");
            AnoncredsResults.IssuerCreateCredentialResult csCertifiedDIDCredential = CredentialUtils.createCSCertifiedDIDCredential(csoWallet, csCertifiedDIDCredentialOffer, new JSONObject(csCertifiedDIDCredentialRequest.getCredentialRequestJson()), csDID.getDid());
            Log.w(this.getClass().toString(), "Saving credential for CS certified DID into CS wallet...");
            WalletUtils.saveCredential(csWallet, new JSONObject(csCertifiedDIDCredentialRequest.getCredentialRequestMetadataJson()), new JSONObject(csCertifiedDIDCredential.getCredentialJson()), csDIDCertificationCredentialDefFromLedger.getJSONObject("object"), csCertifiedDIDCredential.getRevocRegDeltaJson() != null ? new JSONObject(csCertifiedDIDCredential.getRevocRegDeltaJson()) : null);


            Log.i(this.getClass().toString(), "Closing CSO wallet...");
            csoWallet.close();
            Log.i(this.getClass().toString(), "CSO wallet closed.");
            Log.i(this.getClass().toString(), "Closing DSO wallet...");
            dsoWallet.close();
            Log.i(this.getClass().toString(), "DSO wallet closed.");
            Log.i(this.getClass().toString(), "Closing ER wallet...");
            erWallet.close();
            Log.i(this.getClass().toString(), "ER wallet closed.");
            Log.i(this.getClass().toString(), "Closing steward wallet...");
            stewardWallet.close();
            Log.i(this.getClass().toString(), "Steward wallet closed.");


            mCommonUtils.stopTimer();

            broadcastUpdate(ACTION_INDY_INITIALIZED);

        } catch (Exception e) {
            if (e instanceof IndyException) {
                Log.e(this.getClass().toString(), ((IndyException) e).getSdkBacktrace());
                Log.e(this.getClass().toString(), ((IndyException) e).getSdkMessage());
                Log.e(this.getClass().toString(), String.format("%d", ((IndyException) e).getSdkErrorCode()));
            }
            e.printStackTrace();
        }
    }

    public void releaseResources() {
        indyOperationHandlerThread.quitSafely();

        try{
            // 14. Pool disconnection

            Log.i(this.getClass().toString(), "Closing test pool...");
            sofiePool.close();
            Log.i(this.getClass().toString(), "Test pool closed.");

            // 15. Wallets de-initialisation

            Log.i(this.getClass().toString(), "Closing CS wallet...");
            csWallet.close();
            Log.i(this.getClass().toString(), "CS wallet closed.");

            mCommonUtils.stopTimer();

        } catch (Exception e) {
            if (e instanceof IndyException) {
                Log.e(this.getClass().toString(), ((IndyException) e).getSdkBacktrace());
                Log.e(this.getClass().toString(), ((IndyException) e).getSdkMessage());
                Log.e(this.getClass().toString(), String.format("%d", ((IndyException) e).getSdkErrorCode()));
            }
            e.printStackTrace();
        }
    }


    // Message encrypted for EV and signed by CS
    private byte [] boxMessage(String plaintext) {
        try {
            return CryptoUtils.signAndEncryptMessage(csWallet, csDID.getVerkey(), evVerkey, plaintext.getBytes());
        } catch (IndyException e) {
            e.printStackTrace();
        }
        return null;
    }

    private String unboxMessage(byte[] cipherText) {
        try {
            return new String(CryptoUtils.decryptAndVerifyMessage(csWallet, csDID.getVerkey(), evVerkey, cipherText));
        } catch (IndyException e) {
            e.printStackTrace();
        } catch (MessageSignatureException e) {
            e.printStackTrace();
        }
        return null;
    }
}


