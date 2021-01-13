package com.spire.bledemo.app;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.hyperledger.indy.sdk.IndyException;
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


public class IndyService extends Service {
    CommonUtils mCommonUtils;

    private IndyDID erDID, csoDID;
    private PeerDID evDID;

    private String csInvKey, csDid;

    public static final String ACTION_INDY_INITIALIZED = "com.spire.bledemo.app.ACTION_INDY_INITIALIZED";

    private SharedPreferences storage;

    private JSONObject exchangeInvitation;
    private JSONObject exchangeRequest;
    private JSONObject exchangeResponse;
    private JSONObject exchangeComplete;

    private JSONObject evChargingCredential, csCredential, csPresentation;

    private HashChain hashChain;

    long start, end;

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


    public byte[] createExchangeRequest() {

        byte[] encryptedExchangeRequestSent = null;

        try {
            // 10. Exchange request (step 6)
            JSONObject exchangeRequest = ExchangeUtils.createExchangeRequest(exchangeInvitation.getJSONObject("invitation"), evDID);
            mCommonUtils.startTimer();
            encryptedExchangeRequestSent = ExchangeUtils.encryptFor(PeerDID.getPublicKeyFromMultiBaseKey(csInvKey), exchangeRequest.toString().getBytes());
            mCommonUtils.writeLine("request encryption time");
            mCommonUtils.stopTimer();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return encryptedExchangeRequestSent;
    }


    public void parseExchangeResponse(byte[] encryptedExchangeResponseReceived) {

        try {

            mCommonUtils.stopTimer();
            byte[] decryptedExchangeResponseReceived = evDID.decrypt(encryptedExchangeResponseReceived);
            mCommonUtils.writeLine("response decryption time");
            mCommonUtils.stopTimer();

            exchangeResponse = new JSONObject(new String(decryptedExchangeResponseReceived));

            csPresentation = exchangeResponse.getJSONObject("presentation");
            csDid = exchangeResponse.getJSONObject("response").getJSONObject("connection").getString("did");
            String encodedCSCredential = exchangeResponse.getJSONArray("verifiableCredential").getString(0);
            csCredential = new JSONObject(new String(Base64.getDecoder().decode(encodedCSCredential)));

            boolean isCSPresentationValid;

            JSONArray csDidList = csCredential.getJSONObject("credentialSubject").getJSONArray("id");

            mCommonUtils.stopTimer();

            if (csDidList.length() > 1) {
                isCSPresentationValid = PresentationUtils.verifyCSPresentation(exchangeResponse.getJSONObject("presentation"), csCredential);
            } else {
                isCSPresentationValid = PresentationUtils.verifyCSPresentation(exchangeResponse.getJSONObject("presentation"));
            }

            if (!isCSPresentationValid) {
                System.exit(255);
            }

            mCommonUtils.writeLine("response verify time (cs presentation only)");
            mCommonUtils.stopTimer();

            start = System.currentTimeMillis();
            boolean isCSCredentialValid = CredentialUtils.verifyCSInfoCredential(csCredential);
            end = System.currentTimeMillis();
            log(Log.INFO, String.format("CS credential validation time: %d ms", end - start));
            log(Log.INFO, String.format("Is CSO-issued credential valid? %b", isCSCredentialValid));
            if (!isCSCredentialValid) {
                System.exit(255);
            }


            mCommonUtils.writeLine("Signature Verification Ends");

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

    public String getCsSignatureValue() {
        try {
            return csPresentation.getJSONObject("proof").getString("signatureValue");
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
            log(Log.INFO, String.format("Hash chain generation time for EV: %d ms", end - start));

            JSONArray csDidList = csCredential.getJSONObject("credentialSubject").getJSONArray("id");
            JSONObject paymentCommitment;
            if (csDidList.length() > 1) {
                paymentCommitment = CommitmentUtils.getEVCommitmentForRingSignature(csPresentation, hashChain.getRoot(), hashChain.getHashingFunction());
            } else {
                paymentCommitment = CommitmentUtils.getEVCommitment(csDid, hashChain.getRoot(), hashChain.getHashingFunction());
            }

            start = System.currentTimeMillis();
            JSONObject evPresentation = PresentationUtils.generateEVPresentation(evDID, paymentCommitment);
            end = System.currentTimeMillis();
            log(Log.INFO, String.format("Presentation creation time for EV: %d ms", end - start));

            // Step 11: EV -> CS = Exchange complete + EV presentation + payment commitment
            exchangeComplete = ExchangeUtils.createExchangeComplete(exchangeResponse.getJSONObject("response"), evChargingCredential, evPresentation);
            start = System.currentTimeMillis();
            encryptedExchangeCompleteSent = ExchangeUtils.encryptFor(PeerDID.getEncKeyFromVerKey(PeerDID.getVerkeyFromDID(csDid)), exchangeComplete.toString().getBytes());
            end = System.currentTimeMillis();
            log(Log.INFO, String.format("Exchange complete encryption time from EV: %d ms", end - start));
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
        return df.format(new Date());  // iSO 8601
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


    private final IBinder mBinder = new LocalBinder();

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }


    public void initialize() {
        mCommonUtils = new CommonUtils("IndyService");
        storage = getSharedPreferences("IndyService", MODE_PRIVATE);

        new IndyInitialisationTask().execute();
    }


    /*
    Offline tasks for Indy: intialize, create did and credentials
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

    private void generateCredentials() {
        try {
            Initialiser.init(this.getApplicationContext());
            erDID = DIDUtils.getERDID();
            csoDID = DIDUtils.getCSODID();

            evDID = DIDUtils.getEVDID();

            //long start = SystemClock.elapsedRealtime();
            evChargingCredential = CredentialUtils.getEVChargingCredential(evDID, erDID);
            //mCommonUtils.writeLine("time: " + (SystemClock.elapsedRealtime() - start) + " credential size: " + evChargingCredential.toString().length());


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void log(int priority, String message) {
        Log.println(priority, IndyService.class.toString(), message);
    }

}


