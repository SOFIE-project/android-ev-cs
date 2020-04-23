package com.spire.bledemo.app;

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
import fi.aalto.indy_utils.DIDUtils;
import fi.aalto.indy_utils.IndyUtils;
import fi.aalto.indy_utils.PoolUtils;
import fi.aalto.indy_utils.ProofUtils;
import fi.aalto.indy_utils.WalletUtils;

//import android.support.annotation.RequiresApi;


public class IndyService extends Service {
    CommonUtils mCommonUtils;

    private Wallet evWallet;
    private Wallet csoWallet;
    private Wallet dsoWallet;
    private Wallet csWallet;
    private Wallet erWallet;
    private Wallet stewardWallet;

    DidResults.CreateAndStoreMyDidResult evDID;
    private DidResults.CreateAndStoreMyDidResult csDID;
    private DidResults.CreateAndStoreMyDidResult stewardDID;
    private DidResults.CreateAndStoreMyDidResult csoDID;
    private DidResults.CreateAndStoreMyDidResult dsoDID;
    private DidResults.CreateAndStoreMyDidResult erDID;

    private JSONObject csoInfoCredentialDefFromLedger;
    private JSONObject erChargingCredentialDefFromLedger;
    private JSONObject dsoDistrictCredentialDefFromLedger;

    private JSONObject csoInfodsoDistrictProofRequest;
    private JSONObject erChargingProofRequest;

    private String csMasterSecretID;
    private String evMasterSecretID;

    private JSONObject csoInfoCredentialSchemaFromLedger;
    private JSONObject dsoDistrictCredentialSchemaFromLedger;
    private JSONObject erChargingCredentialSchemaFromLedger;

    private Pool sofiePool;

    private String csDid1;

    public static final String ACTION_INDY_INITIALIZED = "com.spire.bledemo.app.ACTION_INDY_INITIALIZED";


    // Worker thread to unblock the ble callbacks on binder thread
    private HandlerThread indyOperationHandlerThread = new HandlerThread("indyOperationHandlerThread");
    private Handler indyHandler;
    private Context mMainContext;



    public void parseAndSaveCsDid1(String csDidPlainText) {
//        final DidJSONParameters.CreateAndStoreMyDidJSONParameter CS_DID_INFO = new DidJSONParameters.CreateAndStoreMyDidJSONParameter(csDidPlainText, null, null, null);
        csDid1 = csDidPlainText;

/*        try {
            csDID = Did.createAndStoreMyDid(evWallet, CS_DID_INFO.toString()).get();  // which wallet should it be or just use plaintext??
        } catch (InterruptedException | ExecutionException | IndyException e) {
            e.printStackTrace();
        }*/
    }

    public String createEVdidAndCSOProofRequest() {
//        create EV did
//        create proof request
//        sign with ev did
//        encrypt with cs did

        String message = "error";

        try {

            // 11. Proof requests creation
            Log.i(this.getClass().toString(), "Creating CSO Info + DSO district proof request...");
            csoInfodsoDistrictProofRequest = ProofUtils.createCSOInfoAndDSODistrictProofRequest(csoDID.getDid(), dsoDID.getDid(), csoInfoCredentialDefFromLedger.getString("id"), dsoDistrictCredentialDefFromLedger.getString("id"));
            Log.i(this.getClass().toString(), String.format("CSO Info + DSO district proof request created: %s", csoInfodsoDistrictProofRequest.toString().length()));

            mCommonUtils.stopTimer();


            message = evDID.getDid() + "|" + csoInfodsoDistrictProofRequest.toString();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return message;
    }


    public void parseCSDid2CSOProofAndEVCertificateProofRequest(String payload) {
//        unmarshall did, proof and proof request ---- gson?
//        save did
//        verify proof
//        generate proof for proof request(maybe proof request need not be sent over air?)
//        sign proof
//        encrypt proof
//        send proof

        try {
            String cs2Did = payload.split("\\|")[0];
            String csoInfodsoDistrictProofRevealingString = payload.split("\\|")[1];
            String erChargingProofRequestString = payload.split("\\|")[2];

            erChargingProofRequest = new JSONObject(erChargingProofRequestString);
            JSONObject csoInfodsoDistrictProofRevealing = new JSONObject(csoInfodsoDistrictProofRevealingString);

            // 13. Proofs verification

            Log.i(this.getClass().toString(), "Verifying proof for CSO Info + DSO district...");
            boolean isCSOInfoDSODistrictProofRevealingValid = ProofUtils.verifyCSOInfoDSODistrictProofCrypto(
                    csoInfodsoDistrictProofRequest,
                    csoInfodsoDistrictProofRevealing,
                    csoInfoCredentialSchemaFromLedger.getString("id"),
                    dsoDistrictCredentialSchemaFromLedger.getString("id"),
                    csoInfoCredentialSchemaFromLedger.getJSONObject("object"),
                    dsoDistrictCredentialSchemaFromLedger.getJSONObject("object"),
                    csoInfoCredentialDefFromLedger.getString("id"),
                    dsoDistrictCredentialDefFromLedger.getString("id"),
                    csoInfoCredentialDefFromLedger.getJSONObject("object"),
                    dsoDistrictCredentialDefFromLedger.getJSONObject("object")
            );
            Log.i(this.getClass().toString(), String.format("Proof for CSO Info + DSO district verified with result: %b", isCSOInfoDSODistrictProofRevealingValid));
            Log.i(this.getClass().toString(), String.format("Proof for CSO Info + DSO district credential values validated with result: %b", ProofUtils.verifyCSOInfoDSODistrictProofValues(csoInfodsoDistrictProofRevealing, String.valueOf(CredentialUtils.CS_DISTRICT_ID), 1, csoDID.getDid(), 1)));

            mCommonUtils.stopTimer();


        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public String createErChargingProof() {
        String proof = "error";
     try {
        Log.i(this.getClass().toString(), "Creating proof for ER charging proof request...");
        JSONObject erChargingProofRequestCredentialsRevealed = CredentialUtils.getPredicatesForERChargingProofRequest(evWallet, erChargingProofRequest, true);

        JSONObject erChargingProofRevealing = ProofUtils.createProofERChargingProofRequest(
                evWallet,
                erChargingProofRequest,
                erChargingProofRequestCredentialsRevealed,
                evMasterSecretID,
                erChargingCredentialSchemaFromLedger.getString("id"),
                erChargingCredentialSchemaFromLedger.getJSONObject("object"),
                erChargingCredentialDefFromLedger.getString("id"),
                erChargingCredentialDefFromLedger.getJSONObject("object")
        );
        Log.i(this.getClass().toString(), String.format("Proof for ER charging proof request created: %s", erChargingProofRevealing.toString().length()));

        proof = erChargingProofRevealing.toString();
        mCommonUtils.stopTimer();

    } catch (Exception e) {
        e.printStackTrace();
    }
        return proof;
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
 //       intent.putExtra(CURRENT_STAGE, stage);

        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    public void initialize() {
//        mMainContext = mainContext;
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

    private void initializeIndy() {

            try {

                mCommonUtils.startTimer();

                // 1. Indy initialisation

                Log.i(this.getClass().toString(), "Initialising Indy context...");
                IndyUtils.initialise(getApplicationContext());

                // 2. Wallets creation

                Log.i(this.getClass().toString(), "Creating EV, CSO, CS, DSO and steward wallets...");
                WalletUtils.createEVWallet();
                WalletUtils.createCSWallet();
                WalletUtils.createCSOWallet();
                WalletUtils.createDSOWallet();
                WalletUtils.createERWallet();
                WalletUtils.createStewardWallet();

                // 3. Wallets opening

                Log.i(this.getClass().toString(), "Opening EV wallet...");
                evWallet = WalletUtils.openEVWallet();
                Log.i(this.getClass().toString(), "Opening CSO wallet...");
                csoWallet = WalletUtils.openCSOWallet();
                Log.i(this.getClass().toString(), "Opening DSO wallet...");
                dsoWallet = WalletUtils.openDSOWallet();
                Log.i(this.getClass().toString(), "Opening CS wallet...");
                csWallet = WalletUtils.openCSWallet();
                Log.i(this.getClass().toString(), "Opening ER wallet...");
                erWallet = WalletUtils.openERWallet();
                Log.i(this.getClass().toString(), "Opening steward wallet...");
                stewardWallet = WalletUtils.openStewardWallet();

                // 4. Pool configuration + connection

                Log.i(this.getClass().toString(), "Creating test pool configuration...");
                PoolUtils.createSOFIEPoolConfig();
                Log.i(this.getClass().toString(), "Test pool configuration created.");

                Log.i(this.getClass().toString(), "Connecting to SOFIE pool...");
                sofiePool = PoolUtils.connectToSOFIEPool();
                Log.i(this.getClass().toString(), "Connected to SOFIE pool.");

                // 5. DIDs creation

                Log.i(this.getClass().toString(), "Calculating EV DID...");
                evDID = DIDUtils.createEVDID(evWallet);
                Log.i(this.getClass().toString(), String.format("EV DID calculated: %s - %s", evDID.getDid(), evDID.getVerkey()));

                Log.i(this.getClass().toString(), "Calculating CS DID...");
                csDID = DIDUtils.createCSDID(csWallet);
                Log.i(this.getClass().toString(), String.format("CS DID calculated: %s - %s", csDID.getDid(), csDID.getVerkey()));

                Log.i(this.getClass().toString(), "Calculating steward DID...");
                stewardDID = DIDUtils.createStewardDID(stewardWallet);
                Log.i(this.getClass().toString(), String.format("CSO steward DID calculated: %s - %s", stewardDID.getDid(), stewardDID.getVerkey()));

                Log.i(this.getClass().toString(), "Calculating and writing on ledger CSO DID...");
                csoDID = DIDUtils.createAndWriteCSODID(csoWallet, stewardWallet, stewardDID.getDid(), sofiePool);
                Log.i(this.getClass().toString(), String.format("CSO DID calculated and written on ledger: %s - %s", csoDID.getDid(), csoDID.getVerkey()));

                Log.i(this.getClass().toString(), "Calculating and writing on ledger DSO DID...");
                dsoDID = DIDUtils.createAndWriteDSODID(dsoWallet, stewardWallet, stewardDID.getDid(), sofiePool);
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

                Log.i(this.getClass().toString(), "Creating and writing on ledger credential schema for ER-EV charging info...");
                AnoncredsResults.IssuerCreateSchemaResult erChargingCredentialSchema = CredentialSchemaUtils.createAndWriteERChargingCredentialSchema(erDID.getDid(), erWallet, sofiePool);
                Log.i(this.getClass().toString(), String.format("Credential schema for ER-EV charging info created and written on ledger."));

                erChargingCredentialSchemaFromLedger = CredentialSchemaUtils.readCredentialSchemaFromLedger(erDID.getDid(), erChargingCredentialSchema.getSchemaId(), sofiePool);
                Log.i(this.getClass().toString(), String.format("ER-EV charging info credential schema fetched from ledger: %s", erChargingCredentialSchemaFromLedger));

                // 7. Credential definitions creation

                Log.i(this.getClass().toString(), "Creating and writing on ledger credential definition for CS-CSO info...");
                AnoncredsResults.IssuerCreateAndStoreCredentialDefResult csoInfoCredentialDefinition = CredentialDefinitionUtils.createAndWriteCSOInfoCredentialDefinition(csoDID.getDid(), csoWallet, csoInfoCredentialSchemaFromLedger.getJSONObject("object"), sofiePool);
                Log.i(this.getClass().toString(), String.format("Credential definition for CS-CSO info created and written on ledger."));

                csoInfoCredentialDefFromLedger = CredentialDefinitionUtils.readCredentialDefinitionFromLedger(csoDID.getDid(), csoInfoCredentialDefinition.getCredDefId(), sofiePool);
                Log.i(this.getClass().toString(), String.format("CS-CSO info credential definition fetched from ledger: %s", csoInfoCredentialDefFromLedger));

                Log.i(this.getClass().toString(), "Creating and writing on ledger credential definition for CS-DSO district info...");
                AnoncredsResults.IssuerCreateAndStoreCredentialDefResult dsoDistrictCredentialDefinition = CredentialDefinitionUtils.createAndWriteDSODistrictCredentialDefinition(dsoDID.getDid(), dsoWallet, dsoDistrictCredentialSchemaFromLedger.getJSONObject("object"), sofiePool);
                Log.i(this.getClass().toString(), String.format("Credential definition for CS-DSO district info created and written on ledger."));

                dsoDistrictCredentialDefFromLedger = CredentialDefinitionUtils.readCredentialDefinitionFromLedger(dsoDID.getDid(), dsoDistrictCredentialDefinition.getCredDefId(), sofiePool);
                Log.i(this.getClass().toString(), String.format("CS-DSO district info credential definition fetched from ledger: %s", dsoDistrictCredentialDefFromLedger));

                Log.i(this.getClass().toString(), "Creating and writing on ledger credential definition for ER-EV charging info...");
                AnoncredsResults.IssuerCreateAndStoreCredentialDefResult erChargingCredentialDefinition = CredentialDefinitionUtils.createAndWriteERChargingCredentialDefinition(erDID.getDid(), erWallet, erChargingCredentialSchemaFromLedger.getJSONObject("object"), sofiePool);
                Log.i(this.getClass().toString(), String.format("Credential definition for ER-CS charging info created and written on ledger."));

                erChargingCredentialDefFromLedger = CredentialDefinitionUtils.readCredentialDefinitionFromLedger(erDID.getDid(), erChargingCredentialDefinition.getCredDefId(), sofiePool);
                Log.i(this.getClass().toString(), String.format("ER-EV charging info credential definition fetched from ledger: %s", erChargingCredentialDefFromLedger));

                // 7.1. Revocation registries creation

//                    JSONObject revRegDefConfig = new JSONObject().put("issuance_type", "ISSUANCE_ON_DEMAND");
//                    JSONObject tailsWriterConfig = new JSONObject().put("base_dir", IndyUtils.getTailsFilePath()).put("uri_pattern", "");
//                    BlobStorageWriter tailsWriter = BlobStorageWriter.openWriter("default", tailsWriterConfig.toString()).get();
//                    String revRegDefTag = "tag";
//                    AnoncredsResults.IssuerCreateAndStoreRevocRegResult createRevRegResult = Anoncreds.issuerCreateAndStoreRevocReg(csoWallet, csoDID.getDid(), null, revRegDefTag, csoInfoCredentialDefFromLedger.getString("id"), revRegDefConfig.toString(), tailsWriter).get();

                // 8. Credential offers creation

                Log.i(this.getClass().toString(), "Creating credential offer for CS-CSO info...");
                JSONObject csoInfoCredentialOffer = CredentialUtils.createCredentialOffer(csoWallet, csoInfoCredentialDefFromLedger.getString("id"));
                Log.i(this.getClass().toString(), String.format("Credential offer for CS-CSO info created: %s", csoInfoCredentialOffer));

                Log.i(this.getClass().toString(), "Creating credential offer for CS-DSO district district info...");
                JSONObject dsoDistrictCredentialOffer = CredentialUtils.createCredentialOffer(dsoWallet, dsoDistrictCredentialDefFromLedger.getString("id"));
                Log.i(this.getClass().toString(), String.format("Credential offer for CS-DSO district info created: %s", dsoDistrictCredentialOffer));

                Log.i(this.getClass().toString(), "Creating credential offer for ER-EV charging info...");
                JSONObject erChargingCredentialOffer = CredentialUtils.createCredentialOffer(erWallet, erChargingCredentialDefFromLedger.getString("id"));
                Log.i(this.getClass().toString(), String.format("Credential offer for ER-EV charging info created: %s", erChargingCredentialOffer));


                Log.i(this.getClass().toString(), "Creating master secret for CS wallet...");
                csMasterSecretID = CredentialUtils.createAndSaveCSMasterSecret(csWallet);
                Log.i(this.getClass().toString(), String.format("Master secret for CS wallet created: %s", csMasterSecretID));

                Log.i(this.getClass().toString(), "Creating master secret for EV wallet...");
                evMasterSecretID = CredentialUtils.createAndSaveEVMasterSecret(evWallet);
                Log.i(this.getClass().toString(), String.format("Master secret for EV wallet created: %s", evMasterSecretID));

                // 9. Credential requests creation

                Log.i(this.getClass().toString(), "Creating credential request for CS-CSO info...");
                AnoncredsResults.ProverCreateCredentialRequestResult csoInfoCredentialRequest = CredentialUtils.createCSOInfoCredentialRequest(csWallet, csDID.getDid(), csoInfoCredentialOffer, csoInfoCredentialDefFromLedger.getJSONObject("object"), csMasterSecretID);
                Log.i(this.getClass().toString(), String.format("Credential request for CS-CSO info created: %s", csoInfoCredentialRequest.getCredentialRequestJson()));

                Log.i(this.getClass().toString(), "Creating credential request for CS-DSO district info...");
                AnoncredsResults.ProverCreateCredentialRequestResult dsoDistrictCredentialRequest = CredentialUtils.createDSODistrictCredentialRequest(csWallet, csDID.getDid(), dsoDistrictCredentialOffer, dsoDistrictCredentialDefFromLedger.getJSONObject("object"), csMasterSecretID);
                Log.i(this.getClass().toString(), String.format("Credential request for CS-DSO district info created: %s", dsoDistrictCredentialRequest.getCredentialRequestJson()));

                Log.i(this.getClass().toString(), "Creating credential request for ER-EV charging info...");
                AnoncredsResults.ProverCreateCredentialRequestResult erChargingCredentialRequest = CredentialUtils.createERChargingCredentialRequest(evWallet, evDID.getDid(), erChargingCredentialOffer, erChargingCredentialDefFromLedger.getJSONObject("object"), evMasterSecretID);
                Log.i(this.getClass().toString(), String.format("Credential request for ER-EV charging info created: %s", erChargingCredentialRequest.getCredentialRequestJson()));

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

                Log.i(this.getClass().toString(), "Creating credential for ER-EV charging info...");
                AnoncredsResults.IssuerCreateCredentialResult erChargingCredential = CredentialUtils.createERChargingCredential(erWallet, evDID.getDid(), erChargingCredentialOffer, new JSONObject(erChargingCredentialRequest.getCredentialRequestJson()));
                Log.i(this.getClass().toString(), String.format("Credential for ER-EV charging info created: %s", erChargingCredential.getCredentialJson()));

                Log.i(this.getClass().toString(), "Saving credential for ER-EV charging info into EV wallet...");
                WalletUtils.saveCredential(evWallet, new JSONObject(erChargingCredentialRequest.getCredentialRequestMetadataJson()), new JSONObject(erChargingCredential.getCredentialJson()), erChargingCredentialDefFromLedger.getJSONObject("object"), erChargingCredential.getRevocRegDeltaJson() != null ? new JSONObject(erChargingCredential.getRevocRegDeltaJson()) : null);
                Log.i(this.getClass().toString(), "Credential for ER-EV charging info saved into EV wallet");


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

        Log.i(this.getClass().toString(), "Closing EV wallet...");
        evWallet.close();
        Log.i(this.getClass().toString(), "EV wallet closed.");
        Log.i(this.getClass().toString(), "Closing CSO wallet...");
        csoWallet.close();
        Log.i(this.getClass().toString(), "CSO wallet closed.");
        Log.i(this.getClass().toString(), "Closing DSO wallet...");
        dsoWallet.close();
        Log.i(this.getClass().toString(), "DSO wallet closed.");
        Log.i(this.getClass().toString(), "Closing CS wallet...");
        csWallet.close();
        Log.i(this.getClass().toString(), "CS wallet closed.");
        Log.i(this.getClass().toString(), "Closing ER wallet...");
        erWallet.close();
        Log.i(this.getClass().toString(), "ER wallet closed.");
        Log.i(this.getClass().toString(), "Closing steward wallet...");
        stewardWallet.close();
        Log.i(this.getClass().toString(), "Steward wallet closed.");

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


}


