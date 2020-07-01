package fi.aalto.indy_utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.bitcoinj.core.Base58;
import org.hyperledger.indy.sdk.IndyException;
import org.hyperledger.indy.sdk.did.Did;
import org.hyperledger.indy.sdk.did.DidJSONParameters;
import org.hyperledger.indy.sdk.did.DidResults;
import org.hyperledger.indy.sdk.ledger.Ledger;
import org.hyperledger.indy.sdk.pool.Pool;
import org.hyperledger.indy.sdk.wallet.Wallet;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;

public final class DIDUtils {

    private static final String EV_DID_SEED = "00000000000000000000000000EV-DID";
    private static final String CS_DID_SEED = "00000000000000000000000000CS-DID";
    private static final String CSO_DID_SEED = "0000000000000000000000000CSO-DID";
    private static final String DSO_DID_SEED = "0000000000000000000000000DSO-DID";
    private static final String ER_DID_SEED = "00000000000000000000000000ER-DID";
    private static final String STEWARD_DID_SEED = "F37DeEe0ba861dFdca5bBF466DAcaB11";                                  //The DID generated from this seed has already been added (externally) to the pool as a STEWARD.

    private static final DidJSONParameters.CreateAndStoreMyDidJSONParameter EV_DID_INFO = new DidJSONParameters.CreateAndStoreMyDidJSONParameter(null, DIDUtils.EV_DID_SEED, null, null);
    private static final DidJSONParameters.CreateAndStoreMyDidJSONParameter CS_DID_INFO = new DidJSONParameters.CreateAndStoreMyDidJSONParameter(null, DIDUtils.CS_DID_SEED, null, null);
    private static final DidJSONParameters.CreateAndStoreMyDidJSONParameter CSO_DID_INFO = new DidJSONParameters.CreateAndStoreMyDidJSONParameter(null, DIDUtils.CSO_DID_SEED, null, null);
    private static final DidJSONParameters.CreateAndStoreMyDidJSONParameter DSO_DID_INFO = new DidJSONParameters.CreateAndStoreMyDidJSONParameter(null, DIDUtils.DSO_DID_SEED, null, null);
    private static final DidJSONParameters.CreateAndStoreMyDidJSONParameter ER_DID_INFO = new DidJSONParameters.CreateAndStoreMyDidJSONParameter(null, DIDUtils.ER_DID_SEED, null, null);
    private static final DidJSONParameters.CreateAndStoreMyDidJSONParameter CSO_STEWARD_DID_INFO = new DidJSONParameters.CreateAndStoreMyDidJSONParameter(null, DIDUtils.STEWARD_DID_SEED, null, null);

    private static SharedPreferences storage;
    private static boolean force;

    private DIDUtils() {}

    static void initWithAppContext(Context context, boolean force) {
        DIDUtils.storage = context.getSharedPreferences("dids", Context.MODE_PRIVATE);
        DIDUtils.force = force;
    }

    /**
     * @param evWallet = result of WalletUtils.openEVWallet
     * @return the DID and verkey for the EV.
     * @throws IndyException
     */
    public static DidResults.CreateAndStoreMyDidResult createEVDID(Wallet evWallet) throws IndyException {
        DidResults.CreateAndStoreMyDidResult did = null;
        try {
            did = Did.createAndStoreMyDid(evWallet, DIDUtils.EV_DID_INFO.toString()).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return did;
    }

    /**
     * @param csWallet = result of WalletUtils.openCSWallet
     * @return the DID and verkey for the CS.
     * @throws IndyException
     */
    public static DidResults.CreateAndStoreMyDidResult createCSDID(Wallet csWallet) throws IndyException {
        DidResults.CreateAndStoreMyDidResult did = null;
        try {
            did = Did.createAndStoreMyDid(csWallet, DIDUtils.CS_DID_INFO.toString()).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return did;
    }

    /**
     * @param csoWallet = result of WalletUtils.openCSOWallet
     * @param csoStewardWallet = result of WalletUtils.openStewardWallet
     * @param csoStewardDID = result of DIDUtils.createStewardDID
     * @param targetPool = result of PoolUtils.connectToSOFIEPool
     * @return the DID and verkey for the DSO.
     * @throws IndyException
     */
    public static DidResults.CreateAndStoreMyDidResult createAndWriteCSODID(Wallet csoWallet, Wallet csoStewardWallet, String csoStewardDID, Pool targetPool) throws IndyException, LedgerWriteException {
        DidResults.CreateAndStoreMyDidResult did = null;
        try {
            did = Did.createAndStoreMyDid(csoWallet, DIDUtils.CSO_DID_INFO.toString()).get();
            if (DIDUtils.isDIDStored(did.getDid())) {
                Log.w(DIDUtils.class.toString(), "DID previously generated.");
                return did;
            }
            DIDUtils.saveDID(did.getDid());
            JSONObject csoRegistrationNymRequest = new JSONObject(Ledger.buildNymRequest(csoStewardDID, did.getDid(), did.getVerkey(), "CSO", "ENDORSER").get());
            JSONObject requestResult = new JSONObject(Ledger.signAndSubmitRequest(targetPool, csoStewardWallet, csoStewardDID, csoRegistrationNymRequest.toString()).get());
            Log.d(DIDUtils.class.toString(), requestResult.toString());

            if (!LedgerUtils.isLedgerResponseValid(requestResult)) {
                String ledgerError = LedgerUtils.getErrorMessage(requestResult);
                if (!ledgerError.contains("only the owner can modify it")) {
                    throw new LedgerWriteException(ledgerError);
                } else {
                    Log.w(DIDUtils.class.toString(), "DID previously written on the ledger.");
                }
            }
            return did;
        } catch (InterruptedException | ExecutionException | JSONException e) {
            e.printStackTrace();
        }
        return did;
    }

    /**
     * @param dsoWallet = result of WalletUtils.openCSOWallet
     * @param dsoStewardWallet = result of WalletUtils.openStewardWallet
     * @param dsoStewardDID = result of DIDUtils.createStewardDID
     * @param targetPool = result of PoolUtils.connectToSOFIEPool
     * @return the DID and verkey for the DSO.
     * @throws IndyException
     */
    public static DidResults.CreateAndStoreMyDidResult createAndWriteDSODID(Wallet dsoWallet, Wallet dsoStewardWallet, String dsoStewardDID, Pool targetPool) throws IndyException, LedgerWriteException {
        DidResults.CreateAndStoreMyDidResult did = null;
        try {
            did = Did.createAndStoreMyDid(dsoWallet, DIDUtils.DSO_DID_INFO.toString()).get();
            if (DIDUtils.isDIDStored(did.getDid())) {
                Log.w(DIDUtils.class.toString(), "DID previously generated.");
                return did;
            }
            DIDUtils.saveDID(did.getDid());
            JSONObject dsoRegistrationNymRequest = new JSONObject(Ledger.buildNymRequest(dsoStewardDID, did.getDid(), did.getVerkey(), "DSO", "ENDORSER").get());
            JSONObject requestResult = new JSONObject(Ledger.signAndSubmitRequest(targetPool, dsoStewardWallet, dsoStewardDID, dsoRegistrationNymRequest.toString()).get());
            Log.d(DIDUtils.class.toString(), requestResult.toString());

            if (!LedgerUtils.isLedgerResponseValid(requestResult)) {
                String ledgerError = LedgerUtils.getErrorMessage(requestResult);
                if (!ledgerError.contains("only the owner can modify it")) {
                    throw new LedgerWriteException(ledgerError);
                } else {
                    Log.w(DIDUtils.class.toString(), "DID previously written on the ledger.");
                }
            }
            return did;
        } catch (InterruptedException | ExecutionException | JSONException e) {
            e.printStackTrace();
        }
        return did;
    }

    /**
     * @param erWallet = result of WalletUtils.openCSOWallet
     * @param erStewardWallet = result of WalletUtils.openStewardWallet
     * @param erStewardDID = result of DIDUtils.createStewardDID
     * @param targetPool = result of PoolUtils.connectToSOFIEPool
     * @return the DID and verkey for the ER.
     * @throws IndyException
     */
    public static DidResults.CreateAndStoreMyDidResult createAndWriteERDID(Wallet erWallet, Wallet erStewardWallet, String erStewardDID, Pool targetPool) throws IndyException, LedgerWriteException {
        DidResults.CreateAndStoreMyDidResult did = null;
        try {
            did = Did.createAndStoreMyDid(erWallet, DIDUtils.ER_DID_INFO.toString()).get();
            if (DIDUtils.isDIDStored(did.getDid())) {
                Log.w(DIDUtils.class.toString(), "DID previously generated.");
                return did;
            }
            DIDUtils.saveDID(did.getDid());

            // LOCAL ER DID HACK

            /*JSONObject erRegistrationNymRequest = new JSONObject(Ledger.buildNymRequest(erStewardDID, did.getDid(), did.getVerkey(), "ER", "ENDORSER").get());
            JSONObject requestResult = new JSONObject(Ledger.signAndSubmitRequest(targetPool, erStewardWallet, erStewardDID, erRegistrationNymRequest.toString()).get());
            Log.d(DIDUtils.class.toString(), requestResult.toString());

            if (!LedgerUtils.isLedgerResponseValid(requestResult)) {
                String ledgerError = LedgerUtils.getErrorMessage(requestResult);
                if (!ledgerError.contains("only the owner can modify it")) {
                    throw new LedgerWriteException(ledgerError);
                } else {
                    Log.w(DIDUtils.class.toString(), "DID previously written on the ledger.");
                }
            }
            return did; */
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return did;
    }

    /**
     * @param stewardWallet = result of WalletUtils.openStewardWallet
     * @return the DID and verkey for the steward.
     * @throws IndyException
     */
    public static DidResults.CreateAndStoreMyDidResult createStewardDID(Wallet stewardWallet) throws IndyException {
        DidResults.CreateAndStoreMyDidResult did = null;
        try {
            did = Did.createAndStoreMyDid(stewardWallet, DIDUtils.CSO_STEWARD_DID_INFO.toString()).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return did;
    }

    private static void saveDID(String did) {
        DIDUtils.storage.edit().putBoolean(DIDUtils.getStorageKeyForDID(did), true).apply();
    }

    private static String getStorageKeyForDID(String DID) {
        return new StringBuilder("did:").append(DID).toString();
    }

    private static boolean isDIDStored(String did) {
        return DIDUtils.storage.getBoolean(DIDUtils.getStorageKeyForDID(did), false);
    }

    public static String getDIDForVerkey(String verkey) {
        byte[] didValue = Arrays.copyOfRange(Base58.decode(verkey), 0, 16);
        return Base58.encode(didValue);
    }
}
