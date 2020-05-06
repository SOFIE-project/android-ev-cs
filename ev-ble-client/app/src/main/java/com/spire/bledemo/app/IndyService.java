package com.spire.bledemo.app;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import org.hyperledger.indy.sdk.did.DidResults;
import org.hyperledger.indy.sdk.pool.Pool;
import org.hyperledger.indy.sdk.wallet.Wallet;
import org.json.JSONObject;

import fi.aalto.indy_utils.CredentialDefinitionUtils;
import fi.aalto.indy_utils.CredentialSchemaUtils;
import fi.aalto.indy_utils.CredentialUtils;
import fi.aalto.indy_utils.DIDUtils;
import fi.aalto.indy_utils.IndyUtils;
import fi.aalto.indy_utils.PoolUtils;
import fi.aalto.indy_utils.ProofUtils;
import fi.aalto.indy_utils.WalletUtils;

import static android.content.ContentValues.TAG;

//import android.support.annotation.RequiresApi;


public class IndyService extends Service {
    CommonUtils mCommonUtils;

    private Wallet evWallet;
    private Wallet csoWallet;
    private Wallet dsoWallet;

    DidResults.CreateAndStoreMyDidResult evDID;
    private DidResults.CreateAndStoreMyDidResult csoDID;
    private DidResults.CreateAndStoreMyDidResult dsoDID;

    private JSONObject mCsoInfoCredentialSchemaFromLedger;
    private JSONObject mDsoDistrictCredentialSchemaFromLedger;
    private JSONObject mErChargingCredentialSchemaFromLedger;
    private JSONObject mCsoInfoCredentialDefFromLedger;
    private JSONObject mErChargingCredentialDefFromLedger;
    private JSONObject mDsoDistrictCredentialDefFromLedger;

    private JSONObject csoInfodsoDistrictProofRequest;
    private JSONObject erChargingProofRequest;

    private Pool mSofiePool;

    private String csDid1, csVerkey1;

    public static final String ACTION_INDY_INITIALIZED = "com.spire.bledemo.app.ACTION_INDY_INITIALIZED";


    // Worker thread to unblock the ble callbacks on binder thread
    private HandlerThread indyOperationHandlerThread = new HandlerThread("indyOperationHandlerThread");
    private Handler indyHandler;
    private Context mMainContext;
    private SharedPreferences storage;


    private String[] splitMessage(String payload) {
        return payload.split("\\|");
    }


    public String joinMessageParts(String... parts) {
        StringBuilder message = new StringBuilder();
        for(String part : parts) {
            message.append(part).append("|");
        }
        return message.toString();
    }

    public void parseAndSaveCsDid1(String csDidPlainText) {

        //csDID = Did.createAndStoreMyDid(evWallet, CS_DID_INFO.toString()).get();  // which wallet should it be or just use plaintext??

        String[] messageParts = splitMessage(csDidPlainText);
        csDid1 = messageParts[0];
        csVerkey1 = messageParts[1];

        String csoCredSchemaId = "7KgT5tfdCewACRt6VwXz9s:2:CSO-Info-Credential-Schema:2.1"; //messageParts[2];
        String dsoCredSchemaId = "97tNmW3eVxkNCKowD2GbJi:2:DSO-District-Credential-Schema:2.1"; //messageParts[3];
        String csoCredDefId = "7KgT5tfdCewACRt6VwXz9s:3:CL:780:CSO-Info-Credential-Definition-Revocable"; // messageParts[4];
        String dsoCredDefId = "97tNmW3eVxkNCKowD2GbJi:3:CL:781:DSO-District-Credential-Definition-Revocable";  //messageParts[5];


        try {

            mCsoInfoCredentialSchemaFromLedger = CredentialSchemaUtils.readCredentialSchemaFromLedger(csoDID.getDid(), csoCredSchemaId, mSofiePool);
            Log.i(this.getClass().toString(), String.format("CS-CSO info credential schema fetched from ledger: %s", mCsoInfoCredentialSchemaFromLedger));


            mDsoDistrictCredentialSchemaFromLedger = CredentialSchemaUtils.readCredentialSchemaFromLedger(dsoDID.getDid(), dsoCredSchemaId, mSofiePool);
            Log.i(this.getClass().toString(), String.format("CS-DSO district info credential schema fetched from ledger: %s", mDsoDistrictCredentialSchemaFromLedger));


            mCsoInfoCredentialDefFromLedger = CredentialDefinitionUtils.readCredentialDefinitionFromLedger(csoDID.getDid(), csoCredDefId, mSofiePool);
            Log.i(this.getClass().toString(), String.format("CS-CSO info credential definition fetched from ledger: %s", mCsoInfoCredentialDefFromLedger));


            mDsoDistrictCredentialDefFromLedger = CredentialDefinitionUtils.readCredentialDefinitionFromLedger(dsoDID.getDid(), dsoCredDefId, mSofiePool);
            Log.i(this.getClass().toString(), String.format("CS-DSO district info credential definition fetched from ledger: %s", mDsoDistrictCredentialDefFromLedger));


            // Read ER credentials too
            Log.i(this.getClass().toString(), "Creating and writing on ledger credential schema for ER-EV charging info...");
            mErChargingCredentialSchemaFromLedger = new JSONObject(CredentialSchemaUtils.createAndWriteERChargingCredentialSchema(null, null, null).getSchemaJson());
            Log.i(this.getClass().toString(), String.format("Credential schema for ER-EV charging info created and written on ledger."));

            Log.i(this.getClass().toString(), "Creating and writing on ledger credential definition for ER-EV charging info...");
            mErChargingCredentialDefFromLedger = new JSONObject(CredentialDefinitionUtils.createAndWriteERChargingCredentialDefinition(null, null, null, null).getCredDefJson());
            Log.i(this.getClass().toString(), String.format("Credential definition for ER-CS charging info created and written on ledger."));


        } catch (Exception e) {
            if (e instanceof IndyException) {
                Log.e(this.getClass().toString(), ((IndyException) e).getSdkBacktrace());
                Log.e(this.getClass().toString(), ((IndyException) e).getSdkMessage());
                Log.e(this.getClass().toString(), String.format("%d", ((IndyException) e).getSdkErrorCode()));
            }
            e.printStackTrace();        }


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
            csoInfodsoDistrictProofRequest = ProofUtils.createCSOInfoAndDSODistrictProofRequest(csoDID.getDid(), dsoDID.getDid(), mCsoInfoCredentialDefFromLedger.getString("id"), mDsoDistrictCredentialDefFromLedger.getString("id"));
            Log.i(this.getClass().toString(), String.format("CSO Info + DSO district proof request created: %s", csoInfodsoDistrictProofRequest.toString().length()));

            mCommonUtils.stopTimer();

            String erChargingSchemaId = mErChargingCredentialSchemaFromLedger.getString("id");
            String erChargingDefId = mErChargingCredentialDefFromLedger.getString("id");
            Log.i(TAG, erChargingSchemaId);
            Log.i(TAG, erChargingDefId);

            message =  joinMessageParts(evDID.getDid(),evDID.getVerkey(),csoInfodsoDistrictProofRequest.toString(),erChargingSchemaId, erChargingDefId);

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
                    mCsoInfoCredentialSchemaFromLedger.getString("id"),
                    mDsoDistrictCredentialSchemaFromLedger.getString("id"),
                    mCsoInfoCredentialSchemaFromLedger.getJSONObject("object"),
                    mDsoDistrictCredentialSchemaFromLedger.getJSONObject("object"),
                    mCsoInfoCredentialDefFromLedger.getString("id"),
                    mDsoDistrictCredentialDefFromLedger.getString("id"),
                    mCsoInfoCredentialDefFromLedger.getJSONObject("object"),
                    mDsoDistrictCredentialDefFromLedger.getJSONObject("object")
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
                CredentialUtils.EV_MASTER_SECRET_ID,
                mErChargingCredentialSchemaFromLedger.getString("id"),
                mErChargingCredentialSchemaFromLedger, //.getJSONObject("object"),
                mErChargingCredentialDefFromLedger.getString("id"),
                mErChargingCredentialDefFromLedger //.getJSONObject("object")
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
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }


    /*
    Full initialization requires
    0. Initialize indy, pool connection
    1. Wallet creation / opening EV, ER, stewart
    2. Did creation for EV, ER, stewart
    3. schema creation
    4. schema definition with ER did
    5. credential offer
    6. credential request with EV wallet master secret
    7. credential creation with offer and request
    */

    // Refresh credential cache and initialize fully
    public void generateCredentials() {

        try {

            //TODO: clear sharedPreferences cache.

            // 1. Indy initialisation

//            Log.i(this.getClass().toString(), "Initialising Indy context...");
//            IndyUtils.initialise(getApplicationContext());

            // 2. Wallets creation

            Log.i(this.getClass().toString(), "Creating ER and steward wallets...");
//            WalletUtils.createEVWallet();
            WalletUtils.createERWallet();
            WalletUtils.createStewardWallet();


            // 3. Wallets opening

//            Log.i(this.getClass().toString(), "Opening EV wallet...");
//            Wallet evWallet = WalletUtils.openEVWallet();
            Log.i(this.getClass().toString(), "Opening ER wallet...");
            Wallet erWallet = WalletUtils.openERWallet();
            Log.i(this.getClass().toString(), "Opening steward wallet...");
            Wallet stewardWallet = WalletUtils.openStewardWallet();


            // 4. Pool configuration + connection

/*
            Log.i(this.getClass().toString(), "Creating test pool configuration...");
            PoolUtils.createSOFIEPoolConfig();
            Log.i(this.getClass().toString(), "Test pool configuration created.");

            Log.i(this.getClass().toString(), "Connecting to SOFIE pool...");
            Pool sofiePool = PoolUtils.connectToSOFIEPool();
            Log.i(this.getClass().toString(), "Connected to SOFIE pool.");

*/


            // 5. DIDs creation

            Log.i(this.getClass().toString(), "Calculating EV DID...");
            DidResults.CreateAndStoreMyDidResult evDID = DIDUtils.createEVDID(evWallet);
            Log.i(this.getClass().toString(), String.format("EV DID calculated: %s - %s", evDID.getDid(), evDID.getVerkey()));

            Log.i(this.getClass().toString(), "Calculating steward DID...");
            DidResults.CreateAndStoreMyDidResult stewardDID = DIDUtils.createStewardDID(stewardWallet);
            Log.i(this.getClass().toString(), String.format("Steward DID calculated: %s - %s", stewardDID.getDid(), stewardDID.getVerkey()));

            Log.i(this.getClass().toString(), "Calculating and writing on ledger ER DID...");
            DidResults.CreateAndStoreMyDidResult erDID = DIDUtils.createAndWriteERDID(erWallet, stewardWallet, stewardDID.getDid(), mSofiePool);
            Log.i(this.getClass().toString(), String.format("ER DID calculated and written on ledger: %s - %s", erDID.getDid(), erDID.getVerkey()));


            // 6. Credential schemas creation

            Log.i(this.getClass().toString(), "Creating and writing on ledger credential schema for ER-EV charging info...");
            AnoncredsResults.IssuerCreateSchemaResult erChargingCredentialSchema = CredentialSchemaUtils.createAndWriteERChargingCredentialSchema(erDID.getDid(), erWallet, mSofiePool);
            Log.i(this.getClass().toString(), String.format("Credential schema for ER-EV charging info created and written on ledger."));

            mErChargingCredentialSchemaFromLedger = CredentialSchemaUtils.readCredentialSchemaFromLedger(erDID.getDid(), erChargingCredentialSchema.getSchemaId(), mSofiePool);
            Log.i(this.getClass().toString(), String.format("ER-EV charging info credential schema fetched from ledger: %s", mErChargingCredentialSchemaFromLedger));


            // 7. Credential definitions creation


            Log.i(this.getClass().toString(), "Creating and writing on ledger credential definition for ER-EV charging info...");
            AnoncredsResults.IssuerCreateAndStoreCredentialDefResult erChargingCredentialDefinition = CredentialDefinitionUtils.createAndWriteERChargingCredentialDefinition(erDID.getDid(), erWallet, mErChargingCredentialSchemaFromLedger.getJSONObject("object"), mSofiePool);
            Log.i(this.getClass().toString(), String.format("Credential definition for ER-CS charging info created and written on ledger."));

            mErChargingCredentialDefFromLedger = CredentialDefinitionUtils.readCredentialDefinitionFromLedger(erDID.getDid(), erChargingCredentialDefinition.getCredDefId(), mSofiePool);
            Log.i(this.getClass().toString(), String.format("ER-EV charging info credential definition fetched from ledger: %s", mErChargingCredentialDefFromLedger));


            // 8. Credential offers creation


            Log.i(this.getClass().toString(), "Creating credential offer for ER-EV charging info...");
            JSONObject erChargingCredentialOffer = CredentialUtils.createCredentialOffer(erWallet, mErChargingCredentialDefFromLedger.getString("id"));
            Log.i(this.getClass().toString(), String.format("Credential offer for ER-EV charging info created: %s", erChargingCredentialOffer));


            // Creating master secret

            Log.i(this.getClass().toString(), "Creating master secret for EV wallet...");
            String evMasterSecretID = CredentialUtils.createAndSaveEVMasterSecret(evWallet);
            Log.i(this.getClass().toString(), String.format("Master secret for EV wallet created: %s", evMasterSecretID));


            // 9. Credential requests creation

            Log.i(this.getClass().toString(), "Creating credential request for ER-EV charging info...");
            AnoncredsResults.ProverCreateCredentialRequestResult erChargingCredentialRequest = CredentialUtils.createERChargingCredentialRequest(evWallet, evDID.getDid(), erChargingCredentialOffer, mErChargingCredentialDefFromLedger.getJSONObject("object"), evMasterSecretID);
            Log.i(this.getClass().toString(), String.format("Credential request for ER-EV charging info created: %s", erChargingCredentialRequest.getCredentialRequestJson()));


            // 10. Credentials creation


            Log.i(this.getClass().toString(), "Creating credential for ER-EV charging info...");
            AnoncredsResults.IssuerCreateCredentialResult erChargingCredential = CredentialUtils.createERChargingCredential(erWallet, evDID.getDid(), erChargingCredentialOffer, new JSONObject(erChargingCredentialRequest.getCredentialRequestJson()));
            Log.i(this.getClass().toString(), String.format("Credential for ER-EV charging info created: %s", erChargingCredential.getCredentialJson()));

            Log.i(this.getClass().toString(), "Saving credential for ER-EV charging info into EV wallet...");
            WalletUtils.saveCredential(evWallet, new JSONObject(erChargingCredentialRequest.getCredentialRequestMetadataJson()), new JSONObject(erChargingCredential.getCredentialJson()), mErChargingCredentialDefFromLedger.getJSONObject("object"), erChargingCredential.getRevocRegDeltaJson() != null ? new JSONObject(erChargingCredential.getRevocRegDeltaJson()) : null);
            Log.i(this.getClass().toString(), "Credential for ER-EV charging info saved into EV wallet");


            // 15. Pool disconnection

/*
            Log.i(this.getClass().toString(), "Closing test pool...");
            sofiePool.close();
            Log.i(this.getClass().toString(), "Test pool closed.");
*/

            // 16. Wallets de-initialisation

            Log.i(this.getClass().toString(), "Closing ER wallet...");
            erWallet.close();
            Log.i(this.getClass().toString(), "ER wallet closed.");
            Log.i(this.getClass().toString(), "Closing steward wallet...");
            stewardWallet.close();
            Log.i(this.getClass().toString(), "Steward wallet closed.");

        } catch (Exception e) {
                if (e instanceof IndyException) {
                    Log.e(this.getClass().toString(), ((IndyException) e).getSdkBacktrace());
                    Log.e(this.getClass().toString(), ((IndyException) e).getSdkMessage());
                    Log.e(this.getClass().toString(), String.format("%d", ((IndyException) e).getSdkErrorCode()));
                }
                e.printStackTrace();
            }
    }


    public void initialize() {
        mCommonUtils = new CommonUtils();

        storage = getSharedPreferences("IndyService", MODE_PRIVATE);


        indyOperationHandlerThread.start();
        indyHandler = new Handler(indyOperationHandlerThread.getLooper());


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
    Initialization for every run
    0. Initialize indy, pool connection
    1. Wallet creating / opening EV, CSO, DSO
    2. Create/read did for EV, CSO, DSO  (required for proof request creation)
    --------------------------------
    Do online:  Read credential schema, credential definition -- need to read schema id from cs
    */
    private void initializeIndy() {

            try {

                mCommonUtils.startTimer();

                // 1. Indy initialisation

                Log.i(this.getClass().toString(), "Initialising Indy context...");
                IndyUtils.initialise(getApplicationContext());


                // 2. Wallets creation

                Log.i(this.getClass().toString(), "Creating EV, CSO and DSO wallets...");
                WalletUtils.createEVWallet();
                WalletUtils.createCSOWallet();
                WalletUtils.createDSOWallet();

                // 3. Wallets opening

                Log.i(this.getClass().toString(), "Opening EV wallet...");
                evWallet = WalletUtils.openEVWallet();
                Log.i(this.getClass().toString(), "Opening CSO wallet...");
                csoWallet = WalletUtils.openCSOWallet();
                Log.i(this.getClass().toString(), "Opening DSO wallet...");
                dsoWallet = WalletUtils.openDSOWallet();


                // 4. Pool configuration + connection

                Log.i(this.getClass().toString(), "Creating test pool configuration...");
                PoolUtils.createSOFIEPoolConfig();
                Log.i(this.getClass().toString(), "Test pool configuration created.");

                Log.i(this.getClass().toString(), "Connecting to SOFIE pool...");
                mSofiePool = PoolUtils.connectToSOFIEPool();
                Log.i(this.getClass().toString(), "Connected to SOFIE pool.");


                // X5. DIDs creation - Read DID from wallet

                Log.i(this.getClass().toString(), "Calculating EV DID...");
                evDID = DIDUtils.createEVDID(evWallet);
                Log.i(this.getClass().toString(), String.format("EV DID calculated: %s - %s", evDID.getDid(), evDID.getVerkey()));

                Log.i(this.getClass().toString(), "Calculating and writing on ledger CSO DID...");
                csoDID = Did.createAndStoreMyDid(csoWallet, DIDUtils.CSO_DID_INFO.toString()).get();
                Log.i(this.getClass().toString(), String.format("CSO DID calculated: %s - %s", csoDID.getDid(), csoDID.getVerkey()));

                Log.i(this.getClass().toString(), "Calculating and writing on ledger DSO DID...");
                dsoDID = Did.createAndStoreMyDid(csoWallet, DIDUtils.DSO_DID_INFO.toString()).get();
                Log.i(this.getClass().toString(), String.format("DSO DID calculated: %s - %s", dsoDID.getDid(), dsoDID.getVerkey()));


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
        mSofiePool.close();
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


