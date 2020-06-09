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
import org.hyperledger.indy.sdk.anoncreds.AnoncredsResults;
import org.hyperledger.indy.sdk.did.DidResults;
import org.hyperledger.indy.sdk.pool.Pool;
import org.hyperledger.indy.sdk.wallet.Wallet;
import org.json.JSONObject;

import java.util.Base64;

import fi.aalto.indy_utils.ConnectionUtils;
import fi.aalto.indy_utils.CredentialDefinitionUtils;
import fi.aalto.indy_utils.CredentialSchemaUtils;
import fi.aalto.indy_utils.CredentialUtils;
import fi.aalto.indy_utils.CryptoUtils;
import fi.aalto.indy_utils.DIDUtils;
import fi.aalto.indy_utils.HashChain;
import fi.aalto.indy_utils.IndyUtils;
import fi.aalto.indy_utils.MessageSignatureException;
import fi.aalto.indy_utils.PoolUtils;
import fi.aalto.indy_utils.ProofUtils;
import fi.aalto.indy_utils.WalletUtils;

//import android.support.annotation.RequiresApi;


public class IndyService extends Service {
    CommonUtils mCommonUtils;

    private Wallet evWallet;

    DidResults.CreateAndStoreMyDidResult evDID;
    private String evMasterSecretID;

    private JSONObject mCsoInfoCredentialSchemaFromLedger;
    private JSONObject mErChargingCredentialSchemaFromLedger;
    private JSONObject mDidCertifiedCredentialSchemaFromLedger;

    private JSONObject mCsoInfoCredentialDefFromLedger;
    private JSONObject mErChargingCredentialDefFromLedger;
    private JSONObject mCsDIDCertificationCredentialDefFromLedger;
    private JSONObject mEvDIDCertificationCredentialDefFromLedger;

    private JSONObject csProofRequestSent;
    private JSONObject erChargingProofRequest;

    private Pool mSofiePool;

    private String csInvKey, csDid, csVerKey;

    public static final String ACTION_INDY_INITIALIZED = "com.spire.bledemo.app.ACTION_INDY_INITIALIZED";


    // Worker thread to unblock the ble callbacks on binder thread
    private HandlerThread indyOperationHandlerThread = new HandlerThread("indyOperationHandlerThread");
    private Handler indyHandler;  // Used Async Task instead
    private SharedPreferences storage;

    public static final String ER_DID = "er_did";
    public static final String ER_SCHEMA_ID = "er_schema_id";
    public static final String ER_DEF_ID = "er_def_id";
    public static final String CSO_DID = "cso_did";
    public static final String CSO_SCHEMA_ID = "cso_schema_id";
    public static final String CSO_DEF_ID = "cso_def_id";
    public static final String DID_SCHEMA_ID = "did_schema_id";
    public static final String DID_DEF_ID = "did_def_id";

    private String erDid;
    private String erCredSchemaId;
    private String erCredDefId;
    private String csoDid;
    private String csoCredSchemaId;
    private String csoCredDefId;
    private String didSchemaId;
    private String didCsoDefId;
    private String didErDefId;

    private JSONObject exchangeInvitation;
    private JSONObject exchangeRequest;
    private JSONObject exchangeResponse;
    private JSONObject evProofRequestReceived;

    private String proofSignature;
    private HashChain c;

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

    // Message encrypted for CS and signed by EV
    private byte[] boxMessage(String plaintext) {
        try {
            return CryptoUtils.signAndEncryptMessage(evWallet, evDID.getVerkey(), csInvKey, plaintext.getBytes());
        } catch (IndyException e) {
            e.printStackTrace();
        }
        return null;
    }

    private String unboxMessage(byte[] cipherText) {
        try {
            return new String(CryptoUtils.decryptAndVerifyMessage(evWallet, evDID.getVerkey(), csInvKey, cipherText));
        } catch (IndyException e) {
            e.printStackTrace();
        } catch (MessageSignatureException e) {
            e.printStackTrace();
        }
        return null;
    }

    public byte[] createMicroChargeRequest() {
        try {
            return CryptoUtils.encryptMessage(csVerKey, c.revealNextChainStep().getBytes());
        } catch (IndyException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void readLedger() {
        try {
            mErChargingCredentialSchemaFromLedger = CredentialSchemaUtils.readCredentialSchemaFromLedger(erDid, erCredSchemaId, mSofiePool);
            mErChargingCredentialDefFromLedger = CredentialDefinitionUtils.readCredentialDefinitionFromLedger(erDid, erCredDefId, mSofiePool);

            mDidCertifiedCredentialSchemaFromLedger = CredentialSchemaUtils.readCredentialSchemaFromLedger(erDid, didSchemaId, mSofiePool);
            mEvDIDCertificationCredentialDefFromLedger = CredentialDefinitionUtils.readCredentialDefinitionFromLedger(erDid, didErDefId, mSofiePool);
            mCsDIDCertificationCredentialDefFromLedger = CredentialDefinitionUtils.readCredentialDefinitionFromLedger(csoDid, didCsoDefId, mSofiePool);

            mCsoInfoCredentialSchemaFromLedger = CredentialSchemaUtils.readCredentialSchemaFromLedger(csoDid, csoCredSchemaId, mSofiePool);
            mCsoInfoCredentialDefFromLedger = CredentialDefinitionUtils.readCredentialDefinitionFromLedger(csoDid, csoCredDefId, mSofiePool);


        } catch (Exception e) {
            if (e instanceof IndyException) {
                Log.e(this.getClass().toString(), ((IndyException) e).getSdkBacktrace());
                Log.e(this.getClass().toString(), ((IndyException) e).getSdkMessage());
                Log.e(this.getClass().toString(), String.format("%d", ((IndyException) e).getSdkErrorCode()));
            }
            e.printStackTrace();
        }
    }

    public void parseExchangeInvitation(byte[] exchangeInvitationBytes) {


        try {
            exchangeInvitation = new JSONObject(new String(exchangeInvitationBytes));

            csInvKey = exchangeInvitation.getJSONObject("invitation").getJSONArray("recipientKeys").getString(0);

            JSONObject credentialIds = exchangeInvitation.getJSONObject("credential_ids");
            csoDid = credentialIds.getString(CSO_DID);
            csoCredSchemaId = credentialIds.getString(CSO_SCHEMA_ID);
            csoCredDefId = credentialIds.getString(CSO_DEF_ID);
            didSchemaId = credentialIds.getString(DID_SCHEMA_ID);
            didCsoDefId = credentialIds.getString(DID_DEF_ID);

            readLedger();

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
            Log.w(this.getClass().toString(), String.format("Exchange request message created: %s", exchangeRequest));


            // 11. Proof requests creation             // 9.3 Create CS proof request
            Log.i(this.getClass().toString(), "Creating CSO Info + DSO district proof request...");
            csProofRequestSent = ProofUtils.createCSOInfoAndDSODistrictAndCertifiedDIDProofRequest(csoDid, mCsoInfoCredentialDefFromLedger.getString("id"), mCsDIDCertificationCredentialDefFromLedger.getString("id"));
            Log.i(this.getClass().toString(), String.format("CSO Info + DSO district proof request created: %s", csProofRequestSent.toString().length()));


            // Adding Credential Ids
            erDid = storage.getString(ER_DID, null);
            erCredSchemaId = storage.getString(ER_SCHEMA_ID, null);
            erCredDefId = storage.getString(ER_DEF_ID, null);
            didSchemaId = storage.getString(DID_SCHEMA_ID, null);
            didErDefId = storage.getString(DID_DEF_ID, null);

            JSONObject credentialIds = new JSONObject()
                    .put(ER_DID, erDid)
                    .put(ER_SCHEMA_ID, erCredSchemaId)
                    .put(ER_DEF_ID, erCredDefId)
                    .put(DID_SCHEMA_ID, didSchemaId)
                    .put(DID_DEF_ID, didErDefId);


            // complete request message = {proof_request: <proof request for EV>, proof: <proof for CS>, request: <request invitation message as in protocol spec>, signature: <signature over all previous content>}
            JSONObject completeExchangeRequestSent = new JSONObject()
                    .put("proof_request", csProofRequestSent)
                    .put("request", exchangeRequest)
                    .put("credential_ids", credentialIds);


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


            // 11.1.3 Calculate expected DID to be found in the proof

            String expectedCSDID = DIDUtils.getDIDForVerkey(csVerKey);
            Log.w(this.getClass().toString(), String.format("Calculated expected DID for CS from verkey: %s", expectedCSDID));

            // 11.1 Verify CS info

            // 11.1.4 Verify proof provided by CS

            JSONObject csProofReceived = exchangeResponse.getJSONObject("proof");
            boolean isCSProofSignatureValid = ProofUtils.verifyCSOInfoDSODistrictCertifiedDIDProofCrypto(
                    csProofRequestSent,
                    csProofReceived,
                    mCsoInfoCredentialSchemaFromLedger.getString("id"),
                    mDidCertifiedCredentialSchemaFromLedger.getString("id"),
                    mCsoInfoCredentialSchemaFromLedger.getJSONObject("object"),
                    mDidCertifiedCredentialSchemaFromLedger.getJSONObject("object"),
                    mCsoInfoCredentialDefFromLedger.getString("id"),
                    mCsDIDCertificationCredentialDefFromLedger.getString("id"),
                    mCsoInfoCredentialDefFromLedger.getJSONObject("object"),
                    mCsDIDCertificationCredentialDefFromLedger.getJSONObject("object")
            );
            Log.w(this.getClass().toString(), String.format("Is CS proof signature valid? %b", isCSProofSignatureValid));
            if (!isCSProofSignatureValid) {
                System.exit(0);
            }

            boolean areCSProofValuesValid = ProofUtils.verifyCSOInfoDSODistrictCertifiedDIDProofValues(
                    csProofReceived,
                    csoDid,
                    1,
                    expectedCSDID,
                    1
            );

            Log.w(this.getClass().toString(), String.format("Are CS proof values valid? %b", areCSProofValuesValid));
            if (!areCSProofValuesValid) {
                System.exit(0);
            }

            evProofRequestReceived = exchangeResponse.getJSONObject("proof_request");

            // EV need not verify this signature, if it is not the same when signed by EV, CS will reject it.
            proofSignature = exchangeResponse.getString("signature");

            mCommonUtils.stopTimer();

        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    public byte[] createExchangeComplete() {
        byte[] exchangeCompleteSentEncrypted = null;
        try {

            // 12 Send exchange complete (step 9)
            // 11.5 Generate exchange complete message

             JSONObject exchangeCompleteSent = ConnectionUtils.createExchangeComplete(exchangeInvitation.getJSONObject("invitation"), exchangeResponse.getJSONObject("response"));


            // 11.3 Generate information to send back to the CS
            // 11.3.1 Retrieve proof request received by CS and generate proof

            JSONObject credentialsForEVProof = CredentialUtils.getPredicatesForERChargingCertifiedDIDProofRequest(evWallet, evProofRequestReceived, true);
            JSONObject evProofSent = ProofUtils.createProofERChargingCertifiedDIDProofRequest(
                    evWallet,
                    evProofRequestReceived,
                    credentialsForEVProof,
                    evMasterSecretID,
                    mErChargingCredentialSchemaFromLedger.getString("id"),
                    mDidCertifiedCredentialSchemaFromLedger.getString("id"),
                    mErChargingCredentialSchemaFromLedger.getJSONObject("object"),
                    mDidCertifiedCredentialSchemaFromLedger.getJSONObject("object"),
                    mErChargingCredentialDefFromLedger.getString("id"),
                    mEvDIDCertificationCredentialDefFromLedger.getString("id"),
                    mErChargingCredentialDefFromLedger.getJSONObject("object"),
                    mEvDIDCertificationCredentialDefFromLedger.getJSONObject("object")
            );

            Log.i(this.getClass().toString(), "Creating proof for ER charging proof request...");


            long start = System.currentTimeMillis();
            c = new HashChain("SEED", 10, "SHA-256");
            long end = System.currentTimeMillis();
            Log.w(this.getClass().toString(), String.format("Total time: %d ms", (end-start)));
            String rootStep = c.revealNextChainStep();

            JSONObject commitmentMessage = CryptoUtils.getSignedAndEncryptedCommitmentMessage(evDID.getDid(), evWallet, evProofSent, proofSignature, rootStep, 10,  "SHA-256", System.currentTimeMillis() / 1000L, 1, 0.1);


            exchangeCompleteSent.put("proof", evProofSent).put("commitment", commitmentMessage);

            exchangeCompleteSentEncrypted = CryptoUtils.encryptMessage(csVerKey, exchangeCompleteSent.toString().getBytes());

            mCommonUtils.stopTimer();

        } catch (Exception e) {
            e.printStackTrace();
        }
        return exchangeCompleteSentEncrypted;
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
            // TODO: IF credentials are generated, skip after this step to indy Initialization. But how is it going to know?

            Log.i(this.getClass().toString(), "Initialising Indy context...");
            IndyUtils.initialise(getApplicationContext(), false);

            // 2. Wallets creation

            Log.i(this.getClass().toString(), "Creating ER and steward wallets...");
            WalletUtils.createEVWallet();
            WalletUtils.createERWallet();
            WalletUtils.createStewardWallet();


            // 3. Wallets opening

            Log.i(this.getClass().toString(), "Opening EV wallet...");
            Wallet evWallet = WalletUtils.openEVWallet();
            Log.i(this.getClass().toString(), "Opening ER wallet...");
            Wallet erWallet = WalletUtils.openERWallet();
            Log.i(this.getClass().toString(), "Opening steward wallet...");
            Wallet stewardWallet = WalletUtils.openStewardWallet();


            // 4. Pool configuration + connection

            Log.i(this.getClass().toString(), "Creating test pool configuration...");
            PoolUtils.createSOFIEPoolConfig();

            Log.i(this.getClass().toString(), "Connecting to SOFIE pool...");
            mSofiePool = PoolUtils.connectToSOFIEPool();


            // 5. DIDs creation

            Log.i(this.getClass().toString(), "Calculating EV DID...");
            DidResults.CreateAndStoreMyDidResult evDID = DIDUtils.createEVDID(evWallet);

            Log.i(this.getClass().toString(), "Calculating steward DID...");
            DidResults.CreateAndStoreMyDidResult stewardDID = DIDUtils.createStewardDID(stewardWallet);

            Log.i(this.getClass().toString(), "Calculating and writing on ledger ER DID...");
            DidResults.CreateAndStoreMyDidResult erDID = DIDUtils.createAndWriteERDID(erWallet, stewardWallet, stewardDID.getDid(), mSofiePool);


            // 6. Credential schemas creation

            Log.i(this.getClass().toString(), "Creating and writing on ledger credential schema for ER-EV charging info...");
            AnoncredsResults.IssuerCreateSchemaResult erChargingCredentialSchema = CredentialSchemaUtils.createAndWriteERChargingCredentialSchema(erDID.getDid(), erWallet, mSofiePool);
            mErChargingCredentialSchemaFromLedger = CredentialSchemaUtils.readCredentialSchemaFromLedger(erDID.getDid(), erChargingCredentialSchema.getSchemaId(), mSofiePool);


            Log.w(this.getClass().toString(), "Creating and writing on ledger credential schema for DID certification...");
            AnoncredsResults.IssuerCreateSchemaResult didCertifiedCredentialSchema = CredentialSchemaUtils.createAndWriteCertifiedDIDCredentialSchema(erDID.getDid(), erWallet, mSofiePool);
            mDidCertifiedCredentialSchemaFromLedger = CredentialSchemaUtils.readCredentialSchemaFromLedger(erDID.getDid(), didCertifiedCredentialSchema.getSchemaId(), mSofiePool);


            // 7. Credential definitions creation

            Log.i(this.getClass().toString(), "Creating and writing on ledger credential definition for ER-EV charging info...");
            AnoncredsResults.IssuerCreateAndStoreCredentialDefResult erChargingCredentialDefinition = CredentialDefinitionUtils.createAndWriteERChargingCredentialDefinition(erDID.getDid(), erWallet, mErChargingCredentialSchemaFromLedger.getJSONObject("object"), mSofiePool);
            mErChargingCredentialDefFromLedger = CredentialDefinitionUtils.readCredentialDefinitionFromLedger(erDID.getDid(), erChargingCredentialDefinition.getCredDefId(), mSofiePool);

            Log.w(this.getClass().toString(), "Creating and writing on ledger credential definition for EV DID certification...");
            AnoncredsResults.IssuerCreateAndStoreCredentialDefResult evDIDCertificationCredentialDefinition = CredentialDefinitionUtils.createAndWriteEVCertifiedDIDCredentialDefinition(erDID.getDid(), erWallet, mDidCertifiedCredentialSchemaFromLedger.getJSONObject("object"), mSofiePool);
            mEvDIDCertificationCredentialDefFromLedger = CredentialDefinitionUtils.readCredentialDefinitionFromLedger(erDID.getDid(), evDIDCertificationCredentialDefinition.getCredDefId(), mSofiePool);


            // 8. Credential offers creation

            Log.i(this.getClass().toString(), "Creating credential offer for ER-EV charging info...");
            JSONObject erChargingCredentialOffer = CredentialUtils.createCredentialOffer(erWallet, mErChargingCredentialDefFromLedger.getString("id"));

            Log.w(this.getClass().toString(), "Creating credential offer for EV DID certification...");
            JSONObject evCertifiedDIDCredentialOffer = CredentialUtils.createCredentialOffer(erWallet, mEvDIDCertificationCredentialDefFromLedger.getString("id"));


            // Creating master secret

            Log.i(this.getClass().toString(), "Creating master secret for EV wallet...");
            evMasterSecretID = CredentialUtils.createAndSaveEVMasterSecret(evWallet);
            Log.i(this.getClass().toString(), String.format("Master secret for EV wallet created: %s", evMasterSecretID));


            // 9. Credential requests creation

            Log.i(this.getClass().toString(), "Creating credential request for ER-EV charging info...");
            AnoncredsResults.ProverCreateCredentialRequestResult erChargingCredentialRequest = CredentialUtils.createERChargingCredentialRequest(evWallet, evDID.getDid(), erChargingCredentialOffer, mErChargingCredentialDefFromLedger.getJSONObject("object"), evMasterSecretID);
            Log.i(this.getClass().toString(), String.format("Credential request for ER-EV charging info created: %s", erChargingCredentialRequest.getCredentialRequestJson()));

            Log.w(this.getClass().toString(), "Creating credential request for EV DID certification...");
            AnoncredsResults.ProverCreateCredentialRequestResult evCertifiedDIDCredentialRequest = CredentialUtils.createEVCertifiedDIDCredentialRequest(evWallet, evDID.getDid(), evCertifiedDIDCredentialOffer, mEvDIDCertificationCredentialDefFromLedger.getJSONObject("object"), evMasterSecretID);


            // 10. Credentials creation

            Log.i(this.getClass().toString(), "Creating credential for ER-EV charging info...");
            AnoncredsResults.IssuerCreateCredentialResult erChargingCredential = CredentialUtils.createERChargingCredential(erWallet, evDID.getDid(), erChargingCredentialOffer, new JSONObject(erChargingCredentialRequest.getCredentialRequestJson()));
            Log.i(this.getClass().toString(), String.format("Credential for ER-EV charging info created: %s", erChargingCredential.getCredentialJson()));

            Log.i(this.getClass().toString(), "Saving credential for ER-EV charging info into EV wallet...");
            WalletUtils.saveCredential(evWallet, new JSONObject(erChargingCredentialRequest.getCredentialRequestMetadataJson()), new JSONObject(erChargingCredential.getCredentialJson()), mErChargingCredentialDefFromLedger.getJSONObject("object"), erChargingCredential.getRevocRegDeltaJson() != null ? new JSONObject(erChargingCredential.getRevocRegDeltaJson()) : null);
            Log.i(this.getClass().toString(), "Credential for ER-EV charging info saved into EV wallet");

            Log.w(this.getClass().toString(), "Creating credential for EV certified DID...");
            AnoncredsResults.IssuerCreateCredentialResult evCertifiedDIDCredential = CredentialUtils.createEVCertifiedDIDCredential(erWallet, evDID.getDid(), evCertifiedDIDCredentialOffer, new JSONObject(evCertifiedDIDCredentialRequest.getCredentialRequestJson()));
            Log.w(this.getClass().toString(), "Saving credential for EV certified DID into EV wallet...");
            WalletUtils.saveCredential(evWallet, new JSONObject(evCertifiedDIDCredentialRequest.getCredentialRequestMetadataJson()), new JSONObject(evCertifiedDIDCredential.getCredentialJson()), mEvDIDCertificationCredentialDefFromLedger.getJSONObject("object"), evCertifiedDIDCredential.getRevocRegDeltaJson() != null ? new JSONObject(evCertifiedDIDCredential.getRevocRegDeltaJson()) : null);


            // 15. Pool disconnection


            Log.i(this.getClass().toString(), "Closing test pool...");
            mSofiePool.close();
            Log.i(this.getClass().toString(), "Test pool closed.");


            // 16. Wallets de-initialisation

            Log.i(this.getClass().toString(), "Closing ER wallet...");
            erWallet.close();
            Log.i(this.getClass().toString(), "ER wallet closed.");
            Log.i(this.getClass().toString(), "Closing steward wallet...");
            stewardWallet.close();
            Log.i(this.getClass().toString(), "Steward wallet closed.");
            Log.i(this.getClass().toString(), "Closing EV wallet...");
            evWallet.close();
            Log.i(this.getClass().toString(), "EV wallet closed.");

            // Saving ids to local cache.
            storage.edit()
                    .putString(ER_DID, erDID.getDid())
                    .putString(ER_SCHEMA_ID, erChargingCredentialSchema.getSchemaId())
                    .putString(ER_DEF_ID, erChargingCredentialDefinition.getCredDefId())
                    .putString(DID_SCHEMA_ID, didCertifiedCredentialSchema.getSchemaId())
                    .putString(DID_DEF_ID, evDIDCertificationCredentialDefinition.getCredDefId())
                    .apply();

            // Adding Credential Ids
            erDid = storage.getString(ER_DID, null);
            erCredSchemaId = storage.getString(ER_SCHEMA_ID, null);
            erCredDefId = storage.getString(ER_DEF_ID, null);
            didSchemaId = storage.getString(DID_SCHEMA_ID, null);
            didErDefId = storage.getString(DID_DEF_ID, null);

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
            generateCredentials();
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

            // 3. Wallets opening

            Log.i(this.getClass().toString(), "Opening EV wallet...");
            evWallet = WalletUtils.openEVWallet();


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

            // Creating master secret

            Log.i(this.getClass().toString(), "Creating master secret for EV wallet...");
            evMasterSecretID = CredentialUtils.createAndSaveEVMasterSecret(evWallet);
            Log.i(this.getClass().toString(), String.format("Master secret for EV wallet created: %s", evMasterSecretID));

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

        try {
            // 14. Pool disconnection

            Log.i(this.getClass().toString(), "Closing test pool...");
            mSofiePool.close();
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


