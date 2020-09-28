package io.github.webbluetoothcg.bletestperipheral;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.hyperledger.indy.sdk.IndyException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.TimeZone;

import fi.aalto.evchargingprotocol.framework.RingSignatureUtils;
import fi.aalto.evchargingprotocol.framework.Utils;
import ring.Ring;

import com.goterl.lazycode.lazysodium.utils.KeyPair;

import fi.aalto.evchargingprotocol.framework.CredentialUtils;
import fi.aalto.evchargingprotocol.framework.DIDUtils;
import fi.aalto.evchargingprotocol.framework.ExchangeUtils;
import fi.aalto.evchargingprotocol.framework.HashChain;
import fi.aalto.evchargingprotocol.framework.IndyDID;
import fi.aalto.evchargingprotocol.framework.Initialiser;
import fi.aalto.evchargingprotocol.framework.PeerDID;
import fi.aalto.evchargingprotocol.framework.PresentationUtils;
import fi.aalto.evchargingprotocol.framework.SignatureUtils;


public class IndyService extends Service {

    CommonUtils mCommonUtils;

    private String evDid;

    public static final String ACTION_INDY_INITIALIZED = "com.spire.bledemo.app.ACTION_INDY_INITIALIZED";

    public static final String CRED_TYPE = "cred_type";

    private SharedPreferences storage;

    private KeyPair csInvitationKeypair;
    private JSONObject evChargingCredential, csInfoCredential;

    private JSONObject exchangeRequest;
    private JSONObject exchangeResponse;
    private JSONObject exchangeComplete;
    private JSONObject commitmentMessage;
    private JSONObject csPresentation;

    private String lastStep;

    private IndyDID erDID, csoDID;
    private PeerDID csDID;

    private long start, end;

    public String getCsDid() {
        return csDID.getDID();
    }


    public byte[] createExchangeInvitation() {

        byte[] exchangeInvitationBytes = {};
        // Step 3: CS -> EV = Exchange Invitation
        JSONObject exchangeInvitation = ExchangeUtils.createExchangeInvitation(csInvitationKeypair.getPublicKey().getAsBytes(), "ItalEnergy");
        try {
            exchangeInvitationBytes = Utils.compressJSON(exchangeInvitation);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return exchangeInvitationBytes;
    }


    public String parseExchangeRequest(byte[] encryptedExchangeRequestReceived) {

        try {
            // 11.1.1 Decrypt exchange request
            mCommonUtils.startTimer();
            byte[] decryptedExchangeRequestReceived = ExchangeUtils.decrypt(encryptedExchangeRequestReceived, csInvitationKeypair.getSecretKey().getAsBytes(), csInvitationKeypair.getPublicKey().getAsBytes());
            mCommonUtils.writeLine("request decryption time");
            mCommonUtils.stopTimer();

            exchangeRequest = Utils.decompressJSON(decryptedExchangeRequestReceived);
            Log.w(this.getClass().toString(), String.format("Exchange request decrypted: %s", exchangeRequest));

            evDid = exchangeRequest.getJSONObject("request").getJSONObject("connection").getString("did");

        } catch (Exception e) {
            if (e instanceof IndyException) {
                Log.e(this.getClass().toString(), ((IndyException) e).getSdkBacktrace());
                Log.e(this.getClass().toString(), ((IndyException) e).getSdkMessage());
                Log.e(this.getClass().toString(), String.format("%d", ((IndyException) e).getSdkErrorCode()));
            }
            e.printStackTrace();
        }
        return evDid;
    }


    public byte[] createExchangeResponse() {
        byte[] exchangeResponseEncrypted = null;
        try {

            // Step 9: CS -> EV = Exchange Response + CS Presentation

            mCommonUtils.stopTimer();
            if (getCredType()) {
                csPresentation = PresentationUtils.generateCSPresentation(csDID, evDid, csInfoCredential);
            } else {
                csPresentation = PresentationUtils.generateCSPresentation(csDID, evDid);
            }
            mCommonUtils.writeLine("response signing time");
            mCommonUtils.stopTimer();

            exchangeResponse = ExchangeUtils.createExchangeResponse(exchangeRequest.getJSONObject("request"), csInfoCredential, csPresentation, csDID);

            mCommonUtils.stopTimer();
            exchangeResponseEncrypted = ExchangeUtils.encryptFor(PeerDID.getEncKeyFromVerKey(PeerDID.getVerkeyFromDID(evDid)), Utils.compressJSON(exchangeResponse));
            mCommonUtils.writeLine("response encryption time");
            mCommonUtils.stopTimer();


        } catch (Exception e) {
            e.printStackTrace();
        }
        return exchangeResponseEncrypted;
    }


    // cs function - verifyErChargingProof
    public boolean parseExchangeComplete(byte[] encryptedExchangeCompleteReceived) {

        boolean isERChargingProofRevealingValid = false;

        try {

            start = System.currentTimeMillis();
            byte[] decryptedExchangeCompleteReceived = csDID.decrypt(encryptedExchangeCompleteReceived);
            end = System.currentTimeMillis();
            log(Log.INFO, String.format("Exchange complete decryption time from CS: %d ms", end - start));
            exchangeComplete = Utils.decompressJSON(decryptedExchangeCompleteReceived);
            log(Log.INFO, String.format("Exchange complete received: %s", exchangeComplete));

            start = System.currentTimeMillis();
            boolean isEVPresentationValid = PresentationUtils.verifyEVPresentation(exchangeComplete.getJSONObject("presentation"));
            end = System.currentTimeMillis();
            log(Log.INFO, String.format("EV presentation validation time: %d ms", end - start));
            log(Log.INFO, String.format("Is EV presentation valid? %b", isEVPresentationValid));
            if (!isEVPresentationValid) {
                System.exit(255);
            }
            String encodedEVCredential = exchangeComplete.getJSONArray("verifiableCredential").getString(0);
            evChargingCredential = new JSONObject(new String(Base64.getDecoder().decode(encodedEVCredential)));
            start = System.currentTimeMillis();
            boolean isEVCredentialValid = CredentialUtils.verifyEVChargingCredential(evChargingCredential);
            end = System.currentTimeMillis();
            log(Log.INFO, String.format("EV credential validation time: %d ms", end - start));
            log(Log.INFO, String.format("Is ER-issued credential valid? %b", isEVCredentialValid));
            if (!isEVCredentialValid) {
                System.exit(255);
            }

            commitmentMessage = new JSONObject(new String(Base64.getDecoder().decode(exchangeComplete.getJSONObject("presentation").getString("commitment"))));
            lastStep = commitmentMessage.getString("w0");

            isERChargingProofRevealingValid = true;

        } catch (Exception e) {
            if (e instanceof IndyException) {
                Log.e(this.getClass().toString(), ((IndyException) e).getSdkBacktrace());
                Log.e(this.getClass().toString(), ((IndyException) e).getSdkMessage());
                Log.e(this.getClass().toString(), String.format("%d", ((IndyException) e).getSdkErrorCode()));
            }
            e.printStackTrace();
        }

        return isERChargingProofRevealingValid;
    }


    public boolean verifyHashstep(byte[] encryptedHash) {
        try {
            String nextStep = new String(csDID.decrypt(encryptedHash));
            boolean isValidStep = HashChain.isNextStepValid("SHA-256", nextStep, lastStep);
            lastStep = nextStep;
            return isValidStep;

        } catch (Exception e) {
            if (e instanceof IndyException) {
                Log.e(this.getClass().toString(), ((IndyException) e).getSdkBacktrace());
                Log.e(this.getClass().toString(), ((IndyException) e).getSdkMessage());
                Log.e(this.getClass().toString(), String.format("%d", ((IndyException) e).getSdkErrorCode()));
            }
            e.printStackTrace();
        }
        return false;
    }

    public String getCsSignature() {
        try {
            return csPresentation.getJSONObject("proof").getString("signatureValue");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return "";
    }

    public JSONObject getTransactionDetails() {
        try {

            JSONObject transactionDetail = new JSONObject()
                    .put("commitment", commitmentMessage)
                    .put("lastHashStep", lastStep)
                    .put("csPresentation", csPresentation)
                    .put("csCred", csInfoCredential)
                    .put("evCred", evChargingCredential);
            mCommonUtils.writeLine("transaction size " + transactionDetail.toString().getBytes().length
                    + " " + commitmentMessage.toString().getBytes().length
                    + " " + lastStep.getBytes().length
                    + " " + csPresentation.toString().getBytes().length
                    + " " + csInfoCredential.toString().getBytes().length
                    + " " + evChargingCredential.toString().getBytes().length);

            return transactionDetail;

        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void saveCredType(boolean ringCred) {
        storage.edit()
                .putBoolean(CRED_TYPE, ringCred)
                .apply();
        
        if (getCredType()) {
            JSONArray csDidList = DIDUtils.getBulkCSDID(50);
            csInfoCredential = CredentialUtils.getRingCSInfoCredential(csDidList, csoDID);
        } else {
            csInfoCredential = CredentialUtils.getCSInfoCredential(csDID, csoDID);
        }
    }

    public boolean getCredType() {
        return storage.getBoolean(CRED_TYPE, false);
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
        mCommonUtils = new CommonUtils("Ring Sign");
        storage = getSharedPreferences("IndyService", MODE_PRIVATE);
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

    private void generateCredentials() {
        try {
            Initialiser.init(this.getApplicationContext());

            erDID = DIDUtils.getERDID();
            csoDID = DIDUtils.getCSODID();

            csDID = DIDUtils.getCSDID();
            //long start = SystemClock.elapsedRealtime();

            if (getCredType()) {
                JSONArray csDidList = DIDUtils.getBulkCSDID(50);
                csInfoCredential = CredentialUtils.getRingCSInfoCredential(csDidList, csoDID);
            } else {
                csInfoCredential = CredentialUtils.getCSInfoCredential(csDID, csoDID);
            }
           //mCommonUtils.writeLine("time: " + (SystemClock.elapsedRealtime() - start) + " credential size: " + csInfoCredential.toString().length());


            // Experiments Regular Sign CALCULATION
//            JSONObject fakePresentation = PresentationUtils.generateCSPresentation(csDID, "fake_ev_did", csInfoCredential );
//            String data = fakePresentation.getJSONObject("proof").getString("signatureValue");
//            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(this.openFileOutput("config.txt", Context.MODE_PRIVATE));
//            outputStreamWriter.write(data);
//            outputStreamWriter.close();
            //PresentationUtils.verifyCSPresentation(fakePresentation, csInfoCredential);
//            mCommonUtils.startTimer();
//            RingSignatureUtils rs = new RingSignatureUtils(null, csInfoCredential.getJSONObject("credentialSubject").getJSONArray("id"));
//            Ring.sign(csInfoCredential.toString().getBytes(), PeerDID.PeerEntity.CS.getSeed(), rs.ringDidList);
//            mCommonUtils.stopTimer();
//            mCommonUtils.writeLine("Ring signature creation time");

            // Step 3: CS -> EV = Exchange Invitation
            csInvitationKeypair = ExchangeUtils.getCSInvitationKeyPair();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public String getISODate() {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'"); // Quoted "Z" to indicate UTC, no timezone offset
        df.setTimeZone(tz);
        return df.format(new Date());  // iSO 8601
    }

    private static void log(int priority, String message) {
        Log.println(priority, MainActivity.class.toString(), message);
    }
}


