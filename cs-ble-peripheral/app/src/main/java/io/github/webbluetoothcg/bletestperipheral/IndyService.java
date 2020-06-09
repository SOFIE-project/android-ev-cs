package io.github.webbluetoothcg.bletestperipheral;

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

public class IndyService extends Service {

    CommonUtils mCommonUtils;

    private Wallet csWallet;

    private DidResults.CreateAndStoreMyDidResult csDID;

    private JSONObject mCsoInfoCredentialSchemaFromLedger;
    private JSONObject mDidCertifiedCredentialSchemaFromLedger;
    private JSONObject mErChargingCredentialSchemaFromLedger;

    private JSONObject mCsoInfoCredentialDefFromLedger;
    private JSONObject mErChargingCredentialDefFromLedger;
    private JSONObject mCsDIDCertificationCredentialDefFromLedger;
    private JSONObject mEvDIDCertificationCredentialDefFromLedger;

    private JSONObject csoInfodsoDistrictProofRequest;
    private JSONObject erChargingProofRequest;

    private String csMasterSecretID;

    private Pool mSofiePool;

    private String csDid1, evDid, evVerKey;

    public static final String ACTION_INDY_INITIALIZED = "com.spire.bledemo.app.ACTION_INDY_INITIALIZED";

    public static final String ER_DID = "er_did";
    public static final String ER_SCHEMA_ID = "er_schema_id";
    public static final String ER_DEF_ID = "er_def_id";
    public static final String CSO_DID = "cso_did";
    public static final String CSO_SCHEMA_ID = "cso_schema_id";
    public static final String CSO_DEF_ID = "cso_def_id";
    public static final String DID_SCHEMA_ID = "did_schema_id";
    public static final String DID_DEF_ID = "did_def_id";

    // Worker thread to unblock the ble callbacks on binder thread
    private HandlerThread indyOperationHandlerThread = new HandlerThread("indyOperationHandlerThread");
    private Handler indyHandler;
    private SharedPreferences storage;

    private String erDid;
    private String erCredSchemaId;
    private String erCredDefId;
    private String csoDid;
    private String csoCredSchemaId;
    private String csoCredDefId;
    private String didSchemaId;
    private String didCsoDefId;
    private String didErDefId;
    private String csInvKey;

    private JSONObject exchangeRequest;
    private JSONObject exchangeComplete;
    private JSONObject commitmentMessage;
    private JSONObject erChargingProofRevealing;
    private String lastStep;
    private String proofSignature;


    // Message encrypted for EV and signed by CS
    private byte[] boxMessage(String plaintext) {
        try {
            return CryptoUtils.signAndEncryptMessage(csWallet, csDID.getVerkey(), evVerKey, plaintext.getBytes());
        } catch (IndyException e) {
            e.printStackTrace();
        }
        return null;
    }

    private String unboxMessage(byte[] cipherText) {
        try {
            return new String(CryptoUtils.decryptAndVerifyMessage(csWallet, csDID.getVerkey(), evVerKey, cipherText));
        } catch (IndyException e) {
            e.printStackTrace();
        } catch (MessageSignatureException e) {
            e.printStackTrace();
        }
        return null;
    }


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

    // CS function
    // createCsDid1
    public byte[] createExchangeInvitation() {

        try {

            // 9. Exchange invitation (step 4)

            // 9.1 Create static key

            csInvKey = CryptoUtils.generateAndStoreKey(csWallet, "00000000000000000000000000CS-INV");
            Log.w(this.getClass().toString(), String.format("Static key generated to kick-off protocol: %s", csInvKey));

            // 9.2 Create exchange invitation

            JSONObject exchangeInvitationSent = ConnectionUtils.createExchangeInvitation("CS", csInvKey);
            Log.w(this.getClass().toString(), String.format("Exchange invitation message created: %s", exchangeInvitationSent));


            // Send ids of credentials for helping

            csoDid = storage.getString(CSO_DID, null);
            csoCredSchemaId = storage.getString(CSO_SCHEMA_ID, null);
            csoCredDefId = storage.getString(CSO_DEF_ID, null);
            didSchemaId = storage.getString(DID_SCHEMA_ID, null);
            didCsoDefId = storage.getString(DID_DEF_ID, null);

            JSONObject credentialIds = new JSONObject()
                    .put(CSO_DID, csoDid)
                    .put(CSO_SCHEMA_ID, csoCredSchemaId)
                    .put(CSO_DEF_ID, csoCredDefId)
                    .put(DID_SCHEMA_ID, didSchemaId)
                    .put(DID_DEF_ID, didCsoDefId);

            // 9.4 Send message

            JSONObject completeExchangeInvitationSent = new JSONObject().put("invitation", exchangeInvitationSent).put("credential_ids", credentialIds);

            return completeExchangeInvitationSent.toString().getBytes();

        } catch (Exception e) {
            if (e instanceof IndyException) {
                Log.e(this.getClass().toString(), ((IndyException) e).getSdkBacktrace());
                Log.e(this.getClass().toString(), ((IndyException) e).getSdkMessage());
                Log.e(this.getClass().toString(), String.format("%d", ((IndyException) e).getSdkErrorCode()));
            }
            e.printStackTrace();
        }

        return null;
    }


    // CS function
    // parseEVDIDAndCSOProofRequest
    public void parseExchangeRequest(byte[] rawBytes) {

        try {

            // 11.1.1 Decrypt exchange request

            byte[] completeExchangeRequestReceivedRaw = CryptoUtils.decryptMessage(csWallet, csInvKey, rawBytes);
            exchangeRequest = new JSONObject(new String(completeExchangeRequestReceivedRaw));
            Log.w(this.getClass().toString(), String.format("Exchange request decrypted: %s", exchangeRequest));

            evDid = exchangeRequest.getJSONObject("request").getString("did");
            evVerKey = exchangeRequest.getJSONObject("request").getString("verkey");

            csoInfodsoDistrictProofRequest = exchangeRequest.getJSONObject("proof_request");

            JSONObject credential_ids = exchangeRequest.getJSONObject("credential_ids");


            erDid = credential_ids.getString(ER_DID);
            erCredSchemaId = credential_ids.getString(ER_SCHEMA_ID);
            erCredDefId = credential_ids.getString(ER_DEF_ID);
            didSchemaId = credential_ids.getString(DID_SCHEMA_ID);
            didErDefId = credential_ids.getString(DID_DEF_ID);

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

    // CS function
    // createCSDid2CSOProofAndEVCertificateProofRequest
    public byte[] createExchangeResponse() {
        byte[] exchangeResponseEncrypted = null;
        try {

            // 11. Exchange response (step 8)
            JSONObject exchangeResponseSent = ConnectionUtils.createExchangeResponse(exchangeRequest.getJSONObject("request"), csDID.getVerkey());
            exchangeResponseSent.put("did", csDID.getDid());


            // 12. Proofs creation

            Log.i(this.getClass().toString(), "Creating proof for CSO Info + DSO district proof request...");
            JSONObject csoInfodsoDistrictProofRequestCredentialsRevealed = CredentialUtils.getPredicatesForCSOInfoDSODistrictCertifiedDIDProofRequest(csWallet, csoInfodsoDistrictProofRequest, true);

            JSONObject csoInfodsoDistrictProofRevealing = ProofUtils.createProofCSOInfoDSODistrictDIDCertifiedProofRequest(
                    csWallet,
                    csoInfodsoDistrictProofRequest,
                    csoInfodsoDistrictProofRequestCredentialsRevealed,
                    csMasterSecretID,
                    mCsoInfoCredentialSchemaFromLedger.getString("id"),
                    mDidCertifiedCredentialSchemaFromLedger.getString("id"),
                    mCsoInfoCredentialSchemaFromLedger.getJSONObject("object"),
                    mDidCertifiedCredentialSchemaFromLedger.getJSONObject("object"),
                    mCsoInfoCredentialDefFromLedger.getString("id"),
                    mCsDIDCertificationCredentialDefFromLedger.getString("id"),
                    mCsoInfoCredentialDefFromLedger.getJSONObject("object"),
                    mCsDIDCertificationCredentialDefFromLedger.getJSONObject("object")
            );
            Log.i(this.getClass().toString(), String.format("Proof for CSO Info + DSO district proof request created: %s", csoInfodsoDistrictProofRevealing.toString().length()));

            // Creating signature on cs proof
            byte[] proofSignatureRaw = CryptoUtils.generateMessageSignature(csWallet, csDID.getVerkey(), csoInfodsoDistrictProofRevealing.toString().getBytes());
            proofSignature = Base64.getEncoder().encodeToString(proofSignatureRaw);

            Log.i(this.getClass().toString(), "Creating EV charging credential proof request...");
            erChargingProofRequest = ProofUtils.createERChargingAndCertifiedDIDProofRequest(erDid, mErChargingCredentialDefFromLedger.getString("id"), mEvDIDCertificationCredentialDefFromLedger.getString("id"));
            Log.i(this.getClass().toString(), String.format("EV charging credential proof request created: %s", erChargingProofRequest.toString().length()));

            mCommonUtils.stopTimer();


            JSONObject completeExchangeResponseSent = new JSONObject()
                    .put("response", exchangeResponseSent)
                    .put("proof", csoInfodsoDistrictProofRevealing)
                    .put("proof_request", erChargingProofRequest)
                    .put("signature", proofSignature);


            exchangeResponseEncrypted = CryptoUtils.encryptMessage(evVerKey, completeExchangeResponseSent.toString().getBytes());
            mCommonUtils.stopTimer();

        } catch (Exception e) {
            e.printStackTrace();
        }
        return exchangeResponseEncrypted;
    }


    // cs function - verifyErChargingProof
    public boolean parseExchangeComplete(byte[] encryptedProof) {

        boolean isERChargingProofRevealingValid = false;

        try {

            exchangeComplete = new JSONObject(new String(CryptoUtils.decryptMessage(csWallet, csDID.getVerkey(), encryptedProof)));

            erChargingProofRevealing = exchangeComplete.getJSONObject("proof");

            Log.i(this.getClass().toString(), "Verifying proof for ER charging credential...");
            isERChargingProofRevealingValid = ProofUtils.verifyERChargingCertifiedDIDProofCrypto(
                    erChargingProofRequest,
                    erChargingProofRevealing,
                    mErChargingCredentialSchemaFromLedger.getString("id"),
                    mDidCertifiedCredentialSchemaFromLedger.getString("id"),
                    mErChargingCredentialSchemaFromLedger.getJSONObject("object"),
                    mDidCertifiedCredentialSchemaFromLedger.getJSONObject("object"),
                    mErChargingCredentialDefFromLedger.getString("id"),
                    mEvDIDCertificationCredentialDefFromLedger.getString("id"),
                    mErChargingCredentialDefFromLedger.getJSONObject("object"),
                    mEvDIDCertificationCredentialDefFromLedger.getJSONObject("object")
            );
            Log.i(this.getClass().toString(), String.format("Proof for ER charging credential verified with result: %b", isERChargingProofRevealingValid));

            boolean areEVProofValuesValid = ProofUtils.verifyERChargingCertifiedDIDProofValues(
                    erChargingProofRevealing,
                    1,
                    evDid,
                    1
            );
            Log.w(this.getClass().toString(), String.format("Are EV proof values valid? %b", areEVProofValuesValid));

            if (!areEVProofValuesValid) {
                System.exit(0);
            }

            commitmentMessage = exchangeComplete.getJSONObject("commitment");
            lastStep = exchangeComplete.getJSONObject("commitment").getString("hashchain_root");

            verifyCommitmentSignature();

            mCommonUtils.stopTimer();
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


    private void verifyCommitmentSignature() {
        try {
            JSONObject paymentCommitmentSignatureData = new JSONObject()
                    .put("ev_proof", erChargingProofRevealing)
                    .put("cs_signature", proofSignature)
                    .put("hashchain_root", commitmentMessage.getString("hashchain_root"))
                    .put("maxChainLength", commitmentMessage.getInt("maxChainLength"))
                    .put("timestamp", commitmentMessage.getLong("timestamp"))
                    .put("hashchain_value", commitmentMessage.getDouble("hashchain_value"));

            String signature = commitmentMessage.getString("signature");
//
//            CryptoUtils.generateAndStoreKey(csWallet, "00000000000000000000000000EV-DID");
//
//            String calculatedSign = Base64.getEncoder().encodeToString(CryptoUtils.generateMessageSignature(csWallet, evVerKey, paymentCommitmentSignatureData.toString().getBytes()));
//
//            if(!calculatedSign.equals(signature)) {
//                System.exit(0);
//            }

            if (!CryptoUtils.verifyMessageSignature(evVerKey, paymentCommitmentSignatureData.toString().getBytes(), Base64.getDecoder().decode(signature))) {
                System.exit(0);
            }

        } catch (Exception e) {
            if (e instanceof IndyException) {
                Log.e(this.getClass().toString(), ((IndyException) e).getSdkBacktrace());
                Log.e(this.getClass().toString(), ((IndyException) e).getSdkMessage());
                Log.e(this.getClass().toString(), String.format("%d", ((IndyException) e).getSdkErrorCode()));
            }
            e.printStackTrace();
        }

    }

    public boolean verifyHashstep(byte[] encryptedHash) {
        try {
            String nextStep = new String(CryptoUtils.decryptMessage(csWallet, csDID.getVerkey(), encryptedHash));
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

        storage = getSharedPreferences("IndyService", MODE_PRIVATE);

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
            generateCredentials();
            initializeIndy();
            return null;                // null must be returned, as in https://www.quora.com/Why-does-doInBackground-in-the-AsyncTask-class-need-to-return-null-even-though-it%E2%80%99s-returning-type-is-set-to-void/answer/Vishal-Ratna
        }
    }

    private void generateCredentials() {

        try {
            // 1. Indy initialisation

            Log.i(this.getClass().toString(), "Initialising Indy context...");
            IndyUtils.initialise(getApplicationContext(), false);

            // 2. Wallets creation

            Log.i(this.getClass().toString(), "Creating EV, CSO, CS, DSO and steward wallets...");
            WalletUtils.createCSWallet();
            WalletUtils.createCSOWallet();
            WalletUtils.createERWallet();
            WalletUtils.createStewardWallet();

            // 3. Wallets opening

            Log.i(this.getClass().toString(), "Opening CS wallet...");
            csWallet = WalletUtils.openCSWallet();
            Log.i(this.getClass().toString(), "Opening CSO wallet...");
            Wallet csoWallet = WalletUtils.openCSOWallet();
            Log.w(this.getClass().toString(), "Opening ER wallet...");
            Wallet erWallet = WalletUtils.openERWallet();
            Log.i(this.getClass().toString(), "Opening steward wallet...");
            Wallet stewardWallet = WalletUtils.openStewardWallet();

            // 4. Pool configuration + connection

            Log.i(this.getClass().toString(), "Creating test pool configuration...");
            PoolUtils.createSOFIEPoolConfig();
            Log.i(this.getClass().toString(), "Test pool configuration created.");

            Log.i(this.getClass().toString(), "Connecting to SOFIE pool...");
            mSofiePool = PoolUtils.connectToSOFIEPool();
            Log.i(this.getClass().toString(), "Connected to SOFIE pool.");


            // 5. DIDs creation
            // TODO: Create option for creating multiple CS DIDs and create Anonymity set Credential

            Log.i(this.getClass().toString(), "Calculating CS DID...");
            csDID = DIDUtils.createCSDID(csWallet);
            Log.i(this.getClass().toString(), String.format("CS DID calculated: %s - %s", csDID.getDid(), csDID.getVerkey()));

            Log.i(this.getClass().toString(), "Calculating steward DID...");
            DidResults.CreateAndStoreMyDidResult stewardDID = DIDUtils.createStewardDID(stewardWallet);
            Log.i(this.getClass().toString(), String.format("CSO steward DID calculated: %s - %s", stewardDID.getDid(), stewardDID.getVerkey()));

            Log.i(this.getClass().toString(), "Calculating and writing on ledger CSO DID...");
            DidResults.CreateAndStoreMyDidResult csoDID = DIDUtils.createAndWriteCSODID(csoWallet, stewardWallet, stewardDID.getDid(), mSofiePool);
            Log.i(this.getClass().toString(), String.format("CSO DID calculated and written on ledger: %s - %s", csoDID.getDid(), csoDID.getVerkey()));

            Log.w(this.getClass().toString(), "Calculating and writing on ledger ER DID...");
            DidResults.CreateAndStoreMyDidResult erDID = DIDUtils.createAndWriteERDID(erWallet, stewardWallet, stewardDID.getDid(), mSofiePool);
            Log.w(this.getClass().toString(), String.format("ER DID calculated and written on ledger: %s - %s", erDID.getDid(), erDID.getVerkey()));


            // 6. Credential schemas creation

            Log.i(this.getClass().toString(), "Creating and writing on ledger credential schema for CS-CSO info...");
            AnoncredsResults.IssuerCreateSchemaResult csoInfoCredentialSchema = CredentialSchemaUtils.createAndWriteCSOInfoCredentialSchema(csoDID.getDid(), csoWallet, mSofiePool);
            Log.i(this.getClass().toString(), String.format("Credential schema for CS-CSO info created and written on ledger."));

            mCsoInfoCredentialSchemaFromLedger = CredentialSchemaUtils.readCredentialSchemaFromLedger(csoDID.getDid(), csoInfoCredentialSchema.getSchemaId(), mSofiePool);
            Log.i(this.getClass().toString(), String.format("CS-CSO info credential schema fetched from ledger: %s", mCsoInfoCredentialSchemaFromLedger));

            Log.w(this.getClass().toString(), "Creating and writing on ledger credential schema for DID certification...");
            AnoncredsResults.IssuerCreateSchemaResult didCertifiedCredentialSchema = CredentialSchemaUtils.createAndWriteCertifiedDIDCredentialSchema(erDID.getDid(), erWallet, mSofiePool);
            mDidCertifiedCredentialSchemaFromLedger = CredentialSchemaUtils.readCredentialSchemaFromLedger(erDID.getDid(), didCertifiedCredentialSchema.getSchemaId(), mSofiePool);

            // 7. Credential definitions creation

            Log.i(this.getClass().toString(), "Creating and writing on ledger credential definition for CS-CSO info...");
            AnoncredsResults.IssuerCreateAndStoreCredentialDefResult csoInfoCredentialDefinition = CredentialDefinitionUtils.createAndWriteCSOInfoCredentialDefinition(csoDID.getDid(), csoWallet, mCsoInfoCredentialSchemaFromLedger.getJSONObject("object"), mSofiePool);
            mCsoInfoCredentialDefFromLedger = CredentialDefinitionUtils.readCredentialDefinitionFromLedger(csoDID.getDid(), csoInfoCredentialDefinition.getCredDefId(), mSofiePool);

            Log.w(this.getClass().toString(), "Creating and writing on ledger credential definition for CS DID certification...");
            AnoncredsResults.IssuerCreateAndStoreCredentialDefResult csDIDCertificationCredentialDefinition = CredentialDefinitionUtils.createAndWriteCSCertifiedDIDCredentialDefinition(csoDID.getDid(), csoWallet, mDidCertifiedCredentialSchemaFromLedger.getJSONObject("object"), mSofiePool);
            mCsDIDCertificationCredentialDefFromLedger = CredentialDefinitionUtils.readCredentialDefinitionFromLedger(csoDID.getDid(), csDIDCertificationCredentialDefinition.getCredDefId(), mSofiePool);


            // 8. Credential offers creation

            Log.i(this.getClass().toString(), "Creating credential offer for CS-CSO info...");
            JSONObject csoInfoCredentialOffer = CredentialUtils.createCredentialOffer(csoWallet, mCsoInfoCredentialDefFromLedger.getString("id"));

            Log.w(this.getClass().toString(), "Creating credential offer for CS DID certification...");
            JSONObject csCertifiedDIDCredentialOffer = CredentialUtils.createCredentialOffer(csoWallet, mCsDIDCertificationCredentialDefFromLedger.getString("id"));


            // Creating wallet master secret
            Log.i(this.getClass().toString(), "Creating master secret for CS wallet...");
            csMasterSecretID = CredentialUtils.createAndSaveCSMasterSecret(csWallet);
            Log.i(this.getClass().toString(), String.format("Master secret for CS wallet created: %s", csMasterSecretID));


            // 9. Credential requests creation

            Log.i(this.getClass().toString(), "Creating credential request for CS-CSO info...");
            AnoncredsResults.ProverCreateCredentialRequestResult csoInfoCredentialRequest = CredentialUtils.createCSOInfoCredentialRequest(csWallet, csDID.getDid(), csoInfoCredentialOffer, mCsoInfoCredentialDefFromLedger.getJSONObject("object"), csMasterSecretID);
            Log.i(this.getClass().toString(), String.format("Credential request for CS-CSO info created: %s", csoInfoCredentialRequest.getCredentialRequestJson()));

            Log.w(this.getClass().toString(), "Creating credential request for CS DID certification...");
            AnoncredsResults.ProverCreateCredentialRequestResult csCertifiedDIDCredentialRequest = CredentialUtils.createCSCertifiedDIDCredentialRequest(csWallet, csDID.getDid(), csCertifiedDIDCredentialOffer, mCsDIDCertificationCredentialDefFromLedger.getJSONObject("object"), csMasterSecretID);

            // 10. Credentials creation

            Log.i(this.getClass().toString(), "Creating credential for CS-CSO info...");
            AnoncredsResults.IssuerCreateCredentialResult csoInfoCredential = CredentialUtils.createCSOInfoCredential(csoWallet, csoInfoCredentialOffer, new JSONObject(csoInfoCredentialRequest.getCredentialRequestJson()), csoDID.getDid());
            Log.i(this.getClass().toString(), String.format("Credential for CS-CSO info created: %s", csoInfoCredential.getCredentialJson()));

            Log.i(this.getClass().toString(), "Saving credential for CS-CSO info into CS wallet...");
            WalletUtils.saveCredential(csWallet, new JSONObject(csoInfoCredentialRequest.getCredentialRequestMetadataJson()), new JSONObject(csoInfoCredential.getCredentialJson()), mCsoInfoCredentialDefFromLedger.getJSONObject("object"), csoInfoCredential.getRevocRegDeltaJson() != null ? new JSONObject(csoInfoCredential.getRevocRegDeltaJson()) : null);
            Log.i(this.getClass().toString(), "Credential for CS-CSO info saved into CS wallet");

            Log.w(this.getClass().toString(), "Creating credential for CS certified DID...");
            AnoncredsResults.IssuerCreateCredentialResult csCertifiedDIDCredential = CredentialUtils.createCSCertifiedDIDCredential(csoWallet, csCertifiedDIDCredentialOffer, new JSONObject(csCertifiedDIDCredentialRequest.getCredentialRequestJson()), csDID.getDid());
            Log.w(this.getClass().toString(), "Saving credential for CS certified DID into CS wallet...");
            WalletUtils.saveCredential(csWallet, new JSONObject(csCertifiedDIDCredentialRequest.getCredentialRequestMetadataJson()), new JSONObject(csCertifiedDIDCredential.getCredentialJson()), mCsDIDCertificationCredentialDefFromLedger.getJSONObject("object"), csCertifiedDIDCredential.getRevocRegDeltaJson() != null ? new JSONObject(csCertifiedDIDCredential.getRevocRegDeltaJson()) : null);

            // 15. Pool disconnection


            Log.i(this.getClass().toString(), "Closing test pool...");
            mSofiePool.close();
            Log.i(this.getClass().toString(), "Test pool closed.");



            // 16. Wallets de-initialisation

            Log.w(this.getClass().toString(), "Closing CS wallet...");
            csWallet.close();
            Log.i(this.getClass().toString(), "Closing CSO wallet...");
            csoWallet.close();
            Log.i(this.getClass().toString(), "Closing ER wallet...");
            erWallet.close();
            Log.i(this.getClass().toString(), "Closing steward wallet...");
            stewardWallet.close();


            // Saving ids to local cache.
            storage.edit()
                    .putString(CSO_DID, csoDID.getDid())
                    .putString(CSO_SCHEMA_ID, csoInfoCredentialSchema.getSchemaId())
                    .putString(CSO_DEF_ID, csoInfoCredentialDefinition.getCredDefId())
                    .putString(DID_SCHEMA_ID, didCertifiedCredentialSchema.getSchemaId())
                    .putString(DID_DEF_ID, csDIDCertificationCredentialDefinition.getCredDefId())
                    .apply();


        } catch (Exception e) {
            if (e instanceof IndyException) {
                Log.e(this.getClass().toString(), ((IndyException) e).getSdkBacktrace());
                Log.e(this.getClass().toString(), ((IndyException) e).getSdkMessage());
                Log.e(this.getClass().toString(), String.format("%d", ((IndyException) e).getSdkErrorCode()));
            }
            e.printStackTrace();
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

            // 3. Wallets opening

            Log.i(this.getClass().toString(), "Opening CS wallet...");
            csWallet = WalletUtils.openCSWallet();

            // 4. Pool configuration + connection

            Log.i(this.getClass().toString(), "Creating test pool configuration...");
            PoolUtils.createSOFIEPoolConfig();
            Log.i(this.getClass().toString(), "Test pool configuration created.");

            Log.i(this.getClass().toString(), "Connecting to SOFIE pool...");
            mSofiePool = PoolUtils.connectToSOFIEPool();
            Log.i(this.getClass().toString(), "Connected to SOFIE pool.");

            Log.i(this.getClass().toString(), "Calculating CS DID...");
            csDID = DIDUtils.createCSDID(csWallet);
            Log.i(this.getClass().toString(), String.format("CS DID calculated: %s - %s", csDID.getDid(), csDID.getVerkey()));


            // Creating wallet master secret
            Log.i(this.getClass().toString(), "Creating master secret for CS wallet...");
            csMasterSecretID = CredentialUtils.createAndSaveCSMasterSecret(csWallet);
            Log.i(this.getClass().toString(), String.format("Master secret for CS wallet created: %s", csMasterSecretID));

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

}


