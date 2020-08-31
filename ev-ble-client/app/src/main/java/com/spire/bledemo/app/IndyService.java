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

import fi.aalto.evchargingprotocol.framework.CommitmentUtils;
import fi.aalto.evchargingprotocol.framework.CredentialUtils;
import fi.aalto.evchargingprotocol.framework.DIDUtils;
import fi.aalto.evchargingprotocol.framework.ExchangeUtils;
import fi.aalto.evchargingprotocol.framework.HashChain;
import fi.aalto.evchargingprotocol.framework.IndyDID;
import fi.aalto.evchargingprotocol.framework.Initialiser;
import fi.aalto.evchargingprotocol.framework.PeerDID;
import fi.aalto.evchargingprotocol.framework.PresentationUtils;

import ring.Ring;

import static java.util.Base64.getDecoder;

//import android.support.annotation.RequiresApi;


public class IndyService extends Service {
    CommonUtils mCommonUtils;

    private IndyDID erDID, csoDID;
    private PeerDID evDID;


    private String csInvKey, csDid;

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

    private JSONObject evChargingCredential;

    private HashChain hashChain;
    private JSONObject csCred;
    private boolean mRingCredType = false;

    long start, end;

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
            return ExchangeUtils.encryptFor(PeerDID.getEncKeyFromVerKey(PeerDID.getVerkeyFromDID(csDid)), hashChain.revealNextChainStep().getBytes());
        } catch (Exception e) {
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

        byte[] encryptedExchangeRequestSent = null;

        try {
            // 10. Exchange request (step 6)

            JSONObject exchangeRequest = ExchangeUtils.createExchangeRequest(exchangeInvitation.getJSONObject("invitation"), evDID);
            encryptedExchangeRequestSent = ExchangeUtils.encryptFor(PeerDID.getPublicKeyFromMultiBaseKey(csInvKey), exchangeRequest.toString().getBytes());


        } catch (Exception e) {
            e.printStackTrace();
        }

        return encryptedExchangeRequestSent;
    }


    //        parseCSDid2CSOProofAndEVCertificateProofRequest
//        unmarshall did, proof and proof request ---- gson?
//        save did
//        verify proof
//        generate proof for proof request(maybe proof request need not be sent over air?)
//        sign proof
//        encrypt proof
//        send proof
    public void parseExchangeResponse(byte[] encryptedExchangeResponseReceived) {

        try {

            byte[] decryptedExchangeResponseReceived = evDID.decrypt(encryptedExchangeResponseReceived);
            exchangeResponse = new JSONObject(new String(decryptedExchangeResponseReceived));


            mCommonUtils.writeLine("Signature Verification");
            mCommonUtils.stopTimer();
            boolean isCSPresentationValid = PresentationUtils.verifyCSPresentation(exchangeResponse.getJSONObject("presentation"));

            if (!isCSPresentationValid) {
                System.exit(255);
            }

            csDid = exchangeResponse.getJSONObject("presentation").getJSONObject("proof").getString("verificationMethod");
            String encodedCSCredential = exchangeResponse.getJSONArray("verifiableCredential").getString(0);
            JSONObject decodedCSCredential = new JSONObject(new String(Base64.getDecoder().decode(encodedCSCredential)));
            start = System.currentTimeMillis();
            boolean isCSCredentialValid = CredentialUtils.verifyCSInfoCredential(decodedCSCredential);
            end = System.currentTimeMillis();
            log(Log.INFO, String.format("CS credential validation time: %d ms", end-start));
            log(Log.INFO, String.format("Is CSO-issued credential valid? %b", isCSCredentialValid));
            if (!isCSCredentialValid) {
                System.exit(255);
            }


/*            // EV needs verify this signature to confirm owning of DID and also make it demonstrable,
            // if it is not the same when signed by EV, CS will reject it.
            JSONObject csPresentation = exchangeResponse.getJSONObject("presentation");
            JSONObject csPresentationProof = csPresentation.getJSONObject("proof");
            csPresentation.remove("proof");


            JSONArray pbKeyList = csCred.getJSONObject("credentialSubject").getJSONArray("id");

            mCommonUtils.stopTimer();
            if (pbKeyList.length() > 1) {
                mRingCredType = true;
                // RING SIGNATURE VERIFICATION ALTERNATIVELY
                String pbKeyListString = pbKeyList.join("|").replaceAll("\"", "");
                if (!Ring.verify(csPresentation.toString().getBytes(), Base64.getDecoder().decode(csPresentationProof.getString("jws")), pbKeyListString)) {
                    System.exit(0);
                }
            } else {
                mRingCredType = false;
                if (!CryptoUtils.verifyMessageSignature(csPresentationProof.getString("verificationMethod"), csPresentation.toString().getBytes(), Base64.getDecoder().decode(csPresentationProof.getString("jws")))) {
                    System.exit(0);
                }
            }
            mCommonUtils.writeLine("resp sign verify");
            mCommonUtils.stopTimer();

            // reinstate proof
            csPresentation.put("proof", csPresentationProof);

            Log.w(this.getClass().toString(), "Checking if CS DID is the one in credential");
            // TODO: change expected CS DID to public key and check // handle RS case too
//            if (!expectedCSDID.equals(csPresentationProof.getString("verificationMethod"))) {
//                System.exit(0);
//            }


            mCommonUtils.stopTimer();
            mCommonUtils.writeLine("Signature Verificaition Ends");*/


        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public String getEvDid() {
        return evDID.getDID();
    }

    public String getCsDid() {
        return csDid;
    }

    public String getCsSignature() {
        try {
            return exchangeResponse.getJSONObject("presentation").getJSONObject("proof").getString("signatureValue");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return "";
    }


    public byte[] createExchangeComplete() {
        byte[] encryptedExchangeCompleteSent = null;
        try {

            start = System.currentTimeMillis();
            hashChain = new HashChain("CHAIN", 10, HashChain.HashingAlgorithm.SHA256);
            end = System.currentTimeMillis();
            log(Log.INFO, String.format("Hash chain generation time for EV: %d ms", end-start));
            JSONObject paymentCommitment = CommitmentUtils.getEVCommitment(csDid, hashChain.getRoot(), hashChain.getHashingFunction());
            start = System.currentTimeMillis();
            JSONObject evPresentation = PresentationUtils.generateEVPresentation(evDID, paymentCommitment);
            end = System.currentTimeMillis();
            log(Log.INFO, String.format("Presentation creation time for EV: %d ms", end-start));

            // Step 11: EV -> CS = Exchange complete + EV presentation + payment commitment
            JSONObject exchangeComplete = ExchangeUtils.createExchangeComplete(exchangeResponse.getJSONObject("response"), evChargingCredential, evPresentation);
            start = System.currentTimeMillis();
            encryptedExchangeCompleteSent = ExchangeUtils.encryptFor(PeerDID.getEncKeyFromVerKey(PeerDID.getVerkeyFromDID(csDid)), exchangeComplete.toString().getBytes());
            end = System.currentTimeMillis();
            log(Log.INFO, String.format("Exchange complete encryption time from EV: %d ms", end-start));
            log(Log.INFO, String.format("Exchange complete sent: %s", exchangeComplete));

        } catch (Exception e) {
            e.printStackTrace();
        }
        return encryptedExchangeCompleteSent;
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
   /* public void generateCredentials() {

        try {

            // 1. Indy initialisation

            Log.i(this.getClass().toString(), "Initialising Indy context...");
            IndyUtils.initialise(getApplicationContext(), false);

            // X. IF credentials are generated, skip after this step to indy Initialization.
            if (!storage.getString(EV_CRED, "").isEmpty()) {
//                if(false) {
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

            mCommonUtils.stopTimer();
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
*/

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
//            generateCredentials();

            generateNewCredentials();

            broadcastUpdate(ACTION_INDY_INITIALIZED);
            return null;                // null must be returned, as in https://www.quora.com/Why-does-doInBackground-in-the-AsyncTask-class-need-to-return-null-even-though-it%E2%80%99s-returning-type-is-set-to-void/answer/Vishal-Ratna
        }
    }

    private void generateNewCredentials() {
        try {
            Initialiser.init(this.getApplicationContext());
            erDID = DIDUtils.getERDID();
            csoDID = DIDUtils.getCSODID();

            evDID = DIDUtils.getEVDID();
            evChargingCredential = CredentialUtils.getEVChargingCredential(evDID, erDID);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void releaseResources() {
        indyOperationHandlerThread.quitSafely();

        try {
            // 14. Pool disconnection

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

    private static void log(int priority, String message) {
        Log.println(priority, IndyService.class.toString(), message);
    }

}


