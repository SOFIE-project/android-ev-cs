package com.spire.bledemo.app;

import android.app.Service;
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
import org.hyperledger.indy.sdk.did.Did;
import org.hyperledger.indy.sdk.did.DidResults;
import org.hyperledger.indy.sdk.pool.Pool;
import org.hyperledger.indy.sdk.wallet.Wallet;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.TimeZone;

import fi.aalto.indy_utils.ConnectionUtils;
import fi.aalto.indy_utils.CredentialUtils;
import fi.aalto.indy_utils.CryptoUtils;
import fi.aalto.indy_utils.DIDUtils;
import fi.aalto.indy_utils.HashChain;
import fi.aalto.indy_utils.IndyUtils;
import fi.aalto.indy_utils.PoolUtils;
import fi.aalto.indy_utils.ProofUtils;
import fi.aalto.indy_utils.WalletUtils;

import ring.Ring;

import static java.util.Base64.getDecoder;

//import android.support.annotation.RequiresApi;


public class IndyService extends Service {
    CommonUtils mCommonUtils;

    private Wallet evWallet;

    DidResults.CreateAndStoreMyDidResult evDID;

    private JSONObject csProofRequestSent;

    private Pool mSofiePool;

    private String csInvKey, csDid, csVerKey;

    public static final String ACTION_INDY_INITIALIZED = "com.spire.bledemo.app.ACTION_INDY_INITIALIZED";


    // Worker thread to unblock the ble callbacks on binder thread
    private HandlerThread indyOperationHandlerThread = new HandlerThread("indyOperationHandlerThread");
    private Handler indyHandler;  // Used Async Task instead
    private SharedPreferences storage;

    public static final String ER_DID = "er_did";
    public static final String EV_CRED = "er_cred";

    private String erDid;
    private JSONObject evCred;

    private JSONObject exchangeInvitation;
    private JSONObject exchangeRequest;
    private JSONObject exchangeResponse;

    private HashChain c;
    private JSONObject csCred;

    private String[] splitMessage(String payload) {
        return payload.split("\\|");
    }


    public String joinMessageParts(String... parts) {
        StringBuilder message = new StringBuilder();
        for (String part : parts) {
            message.append(part).append("|");
        }
        return message.toString();
    }

    public byte[] createMicroChargeRequest() {
        try {
            return CryptoUtils.encryptMessage(csVerKey, c.revealNextChainStep().getBytes());
        } catch (IndyException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void parseExchangeInvitation(byte[] exchangeInvitationBytes) {

        try {
            exchangeInvitation = new JSONObject(new String(exchangeInvitationBytes));

            csInvKey = exchangeInvitation.getJSONObject("invitation").getJSONArray("recipientKeys").getString(0);


        } catch (Exception e) {
            if (e instanceof IndyException) {
                Log.e(this.getClass().toString(), ((IndyException) e).getSdkBacktrace());
                Log.e(this.getClass().toString(), ((IndyException) e).getSdkMessage());
                Log.e(this.getClass().toString(), String.format("%d", ((IndyException) e).getSdkErrorCode()));
            }
            e.printStackTrace();
        }


    }


//        createEVdidAndCSOProofRequest
//        create EV did
//        create proof request
//        sign with ev did
//        encrypt with cs did

    public byte[] createExchangeRequest() {

        byte[] completeExchangeRequestSentEncrypted = null;

        try {
            // 10. Exchange request (step 6)

            // 10.2 Create exchange request
            exchangeRequest = ConnectionUtils.createExchangeRequest(exchangeInvitation.getJSONObject("invitation"), "CS", evDID.getVerkey());
            exchangeRequest.put("did", evDID.getDid());
            Log.w(this.getClass().toString(), String.format("XXL Exchange request message(EV DID length) created: %s", exchangeRequest.toString().getBytes().length));


            // complete request message = {proof_request: <proof request for EV>, proof: <proof for CS>, request: <request invitation message as in protocol spec>, signature: <signature over all previous content>}
            JSONObject completeExchangeRequestSent = new JSONObject()
                    .put("request", exchangeRequest);

            // 10.6 Encrypt+sign and send message

            byte[] completeExchangeRequestSentRaw = completeExchangeRequestSent.toString().getBytes();
            completeExchangeRequestSentEncrypted = CryptoUtils.encryptMessage(csInvKey, completeExchangeRequestSentRaw);
            Log.w(this.getClass().toString(), String.format("Signature added and message encrypted. Original message: %s", completeExchangeRequestSent));


            mCommonUtils.stopTimer();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return completeExchangeRequestSentEncrypted;
    }


    //        parseCSDid2CSOProofAndEVCertificateProofRequest
//        unmarshall did, proof and proof request ---- gson?
//        save did
//        verify proof
//        generate proof for proof request(maybe proof request need not be sent over air?)
//        sign proof
//        encrypt proof
//        send proof
    public void parseExchangeResponse(byte[] exchangeResponseBytes) {

        try {

            exchangeResponse = new JSONObject(new String(CryptoUtils.decryptMessage(evWallet, evDID.getVerkey(), exchangeResponseBytes)));

            csDid = exchangeResponse.getJSONObject("response").getString("did");
            csVerKey = exchangeResponse.getJSONObject("response").getString("verkey");


            // Parse credential and get cs did

            csCred = new JSONObject(new String(Base64.getDecoder().decode(exchangeResponse.getString("verifiableCredential"))));
            JSONObject csCredProof = csCred.getJSONObject("proof");
            csCred.remove("proof");

            String expectedCSDID = csCred.getJSONObject("credentialSubject").getString("id");

            if (!CryptoUtils.verifyMessageSignature(csCredProof.getString("verificationMethod"), csCred.toString().getBytes(), Base64.getDecoder().decode(csCredProof.getString("jws")))) {
                System.exit(0);
            }

            csCred.put("proof", csCredProof);


            // 11.1 Verify CS info
            // 11.1.4 Verify proof provided by CS


            mCommonUtils.writeLine("Signature Verification");
            mCommonUtils.stopTimer();


            // Ring Signature call ALTERNATIVELY
//            String pbKeyList = csProofReceived.getString("dsd");
//
//            if(! Ring.verify(csProofReceived.toString().getBytes(), Base64.getDecoder().decode(proofSignature), pbKeyList)) {
//                System.exit(0);
//            }

            // EV needs verify this signature to confirm owning of DID and also make it demonstrable,
            // if it is not the same when signed by EV, CS will reject it.
            JSONObject csPresentation = exchangeResponse.getJSONObject("presentation");
            JSONObject csPresentationProof = csPresentation.getJSONObject("proof");
            csPresentation.remove("proof");

            if (!CryptoUtils.verifyMessageSignature(csPresentationProof.getString("verificationMethod"), csPresentation.toString().getBytes(), Base64.getDecoder().decode(csPresentationProof.getString("jws")))) {
                System.exit(0);
            }

            // reinstate proof
            csPresentation.put("proof", csPresentationProof);

            Log.w(this.getClass().toString(), "Checking if CS DID is the one in credential");
            // TODO: change expected CS DID to public key and check // handle RS case too
//            if (!expectedCSDID.equals(csPresentationProof.getString("verificationMethod"))) {
//                System.exit(0);
//            }


            mCommonUtils.stopTimer();
            mCommonUtils.writeLine("Signature Verificaition Ends");


        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public String getEvDid() {
        return evDID.getDid();
    }

    public String getCsDid() {
        return csDid;
    }

    public String getCsSignature() {
        try {
            return exchangeResponse.getJSONObject("presentation").getJSONObject("proof").getString("jws");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return "";
    }


    public byte[] createExchangeComplete() {
        byte[] exchangeCompleteSentEncrypted = null;
        try {

            // 12 Send exchange complete (step 9)
            // 11.5 Generate exchange complete message

             JSONObject exchangeCompleteSent = ConnectionUtils.createExchangeComplete(exchangeInvitation.getJSONObject("invitation"), exchangeResponse.getJSONObject("response"));


            // 11.3 Generate information to send back to the CS
            // 11.3.1 Retrieve proof request received by CS and generate proof

            String verifiableCredential = Base64.getEncoder().encodeToString(evCred.toString().getBytes());

            // Creating hashchain

            long start = System.currentTimeMillis();
            c = new HashChain("SEED", 10, "SHA-256");
            long end = System.currentTimeMillis();
            Log.w(this.getClass().toString(), String.format("Total time: %d ms", (end-start)));
            String rootStep = c.revealNextChainStep();


            // Creating payment commitment
            JSONObject commitmentMessage = new JSONObject()
                    .put("cs_signature", csDid)
                    .put("hashchain_root", rootStep)
                    .put("maxChainLength", 10)
                    .put("timestamp", System.currentTimeMillis() / 1000L)
                    .put("hashchain_value", 0.1);

            String jwsSignature = Base64.getEncoder().encodeToString(CryptoUtils.generateMessageSignature(evWallet, evDID.getVerkey(), commitmentMessage.toString().getBytes()));

            String isoDate = getISODate();
            JSONObject proof = new JSONObject()
                    .put("type", "Ed25519Signature2018")
                    .put("created", isoDate)
                    .put("proofPurpose", "assertionMethod")
                    .put("verificationMethod", evDID.getVerkey())
                    .put("jws", jwsSignature);

            commitmentMessage.put("proof", proof);

            exchangeCompleteSent
                    .put("commitment", commitmentMessage)
                    .put("verifiableCredential", verifiableCredential);

            exchangeCompleteSentEncrypted = CryptoUtils.encryptMessage(csVerKey, exchangeCompleteSent.toString().getBytes());

            mCommonUtils.stopTimer();

        } catch (Exception e) {
            e.printStackTrace();
        }
        return exchangeCompleteSentEncrypted;
    }

    public String getISODate() {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'"); // Quoted "Z" to indicate UTC, no timezone offset
        df.setTimeZone(tz);
        return  df.format(new Date());  // iSO 8601
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

            // 1. Indy initialisation

            Log.i(this.getClass().toString(), "Initialising Indy context...");
            IndyUtils.initialise(getApplicationContext(), false);

            // X. IF credentials are generated, skip after this step to indy Initialization.
            if (!storage.getString(EV_CRED, "").isEmpty()) {
                erDid = storage.getString(ER_DID, null);
                evCred = new JSONObject(storage.getString(EV_CRED, null));

                Log.i(this.getClass().toString(), "Opening EV wallet...");
                evWallet = WalletUtils.openEVWallet();
                evDID = DIDUtils.createEVDID(evWallet);

                return;
            }

            // 2. Wallets creation

            Log.i(this.getClass().toString(), "Creating ER and steward wallets...");
            WalletUtils.createEVWallet();
            WalletUtils.createERWallet();
            WalletUtils.createStewardWallet();


            // 3. Wallets opening

            Log.i(this.getClass().toString(), "Opening EV wallet...");
            evWallet = WalletUtils.openEVWallet();
            Log.i(this.getClass().toString(), "Opening ER wallet...");
            Wallet erWallet = WalletUtils.openERWallet();
            Log.i(this.getClass().toString(), "Opening steward wallet...");
            Wallet stewardWallet = WalletUtils.openStewardWallet();


            // 4. Pool configuration + connection

            Log.i(this.getClass().toString(), "Creating test pool configuration...");
            PoolUtils.createSOFIEPoolConfig();

            Log.i(this.getClass().toString(), "Connecting to SOFIE pool...");
            //mSofiePool = PoolUtils.connectToSOFIEPool();


            // 5. DIDs creation

            Log.i(this.getClass().toString(), "Calculating EV DID...");
            evDID = DIDUtils.createEVDID(evWallet);

            Log.i(this.getClass().toString(), "Calculating steward DID...");
            DidResults.CreateAndStoreMyDidResult stewardDID = DIDUtils.createStewardDID(stewardWallet);

            Log.i(this.getClass().toString(), "Calculating and writing on ledger ER DID...");
            DidResults.CreateAndStoreMyDidResult erDID = DIDUtils.createAndWriteERDID(erWallet, stewardWallet, stewardDID.getDid(), mSofiePool);


            // 10. Credentials creation

            TimeZone tz = TimeZone.getTimeZone("UTC");
            DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'"); // Quoted "Z" to indicate UTC, no timezone offset
            df.setTimeZone(tz);
            String isoDate = df.format(new Date());  // iSO 8601

             evCred = new JSONObject()
                    .put("@context", new JSONArray(new String[] {"https://www.w3.org/2018/credentials/v1",
                            "https://www.w3.org/2020/credentials/ev-info/v1"}))
                    .put("id", "https://www.w3.org/2020/credentials/ev-info")
                    .put("type", new JSONArray(new String[] { "VerifiableCredential",
                            "EVInfoCredential"}))
                    .put("credentialSubject", new JSONObject().put("id", evDID.getDid()))   // Simple credential
                    .put("issuer", erDID.getDid())
                    .put("issuanceDate", isoDate)
                    .put("expirationDate","2024-12-31T23:59:59Z");

            String jwsSignature = Base64.getEncoder().encodeToString(CryptoUtils.generateMessageSignature(erWallet, erDID.getVerkey(), evCred.toString().getBytes()));
            mCommonUtils.writeLine("signature size = " + jwsSignature.getBytes().length);

            JSONObject proof = new JSONObject()
                    .put("type", "Ed25519Signature2018")
                    .put("created", isoDate)
                    .put("proofPurpose", "assertionMethod")
                    .put("verificationMethod", erDID.getVerkey())
                    .put("jws", jwsSignature);   // what fields to include in JWS??

            evCred.put("proof", proof);

            mCommonUtils.writeLine("CREDENTIAL CREATION, size = " + evCred.toString().getBytes().length);
            mCommonUtils.stopTimer();

            // 15. Pool disconnection


            Log.i(this.getClass().toString(), "Closing test pool...");
            //mSofiePool.close();
            Log.i(this.getClass().toString(), "Test pool closed.");


            // 16. Wallets de-initialisation

            Log.i(this.getClass().toString(), "Closing ER wallet...");
            erWallet.close();
            Log.i(this.getClass().toString(), "ER wallet closed.");
            Log.i(this.getClass().toString(), "Closing steward wallet...");
            stewardWallet.close();
            Log.i(this.getClass().toString(), "Steward wallet closed.");

            // Saving ids to local cache.
            storage.edit()
                    .putString(ER_DID, erDID.getDid())
                    .putString(EV_CRED, evCred.toString())
                    .apply();

            // Adding Credential Ids
            erDid = storage.getString(ER_DID, null);
            evCred = new JSONObject(storage.getString(EV_CRED, null));

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
        mCommonUtils = new CommonUtils("IndyService");
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
            generateCredentials();
            broadcastUpdate(ACTION_INDY_INITIALIZED);
            return null;                // null must be returned, as in https://www.quora.com/Why-does-doInBackground-in-the-AsyncTask-class-need-to-return-null-even-though-it%E2%80%99s-returning-type-is-set-to-void/answer/Vishal-Ratna
        }
    }


    public void releaseResources() {
        indyOperationHandlerThread.quitSafely();

        try {
            // 14. Pool disconnection

            Log.i(this.getClass().toString(), "Closing test pool...");
            //mSofiePool.close();
            Log.i(this.getClass().toString(), "Test pool closed.");

            // 15. Wallets de-initialisation

            Log.i(this.getClass().toString(), "Closing EV wallet...");
            evWallet.close();
            Log.i(this.getClass().toString(), "EV wallet closed.");

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


