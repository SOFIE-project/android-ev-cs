package fi.aalto.indy_utils;

import android.util.Log;

import org.hyperledger.indy.sdk.IndyException;
import org.hyperledger.indy.sdk.did.Did;
import org.hyperledger.indy.sdk.did.DidJSONParameters;
import org.hyperledger.indy.sdk.did.DidResults;
import org.hyperledger.indy.sdk.ledger.Ledger;
import org.hyperledger.indy.sdk.pool.Pool;
import org.hyperledger.indy.sdk.wallet.Wallet;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.ExecutionException;

public final class DIDUtils {

    private static final String EV_DID_SEED = "00000000000000000000000000EV-DID";
    private static final String CS_DID_SEED = "00000000000000000000000000CS-DID";
    private static final String CSO_DID_SEED = "0000000000000000000000000CSO-DID";
    private static final String DSO_DID_SEED = "0000000000000000000000000DSO-DID";
    private static final String CSO_STEWARD_DID_SEED = "F37DeEe0ba861dFdca5bBF466DAcaB11";                                  //The DID generated from this seed has already been added (externally) to the pool as a STEWARD.

    private static final DidJSONParameters.CreateAndStoreMyDidJSONParameter EV_DID_INFO = new DidJSONParameters.CreateAndStoreMyDidJSONParameter(null, DIDUtils.EV_DID_SEED, null, null);
    private static final DidJSONParameters.CreateAndStoreMyDidJSONParameter CS_DID_INFO = new DidJSONParameters.CreateAndStoreMyDidJSONParameter(null, DIDUtils.CS_DID_SEED, null, null);
    private static final DidJSONParameters.CreateAndStoreMyDidJSONParameter CSO_DID_INFO = new DidJSONParameters.CreateAndStoreMyDidJSONParameter(null, DIDUtils.CSO_DID_SEED, null, null);
    private static final DidJSONParameters.CreateAndStoreMyDidJSONParameter DSO_DID_INFO = new DidJSONParameters.CreateAndStoreMyDidJSONParameter(null, DIDUtils.DSO_DID_SEED, null, null);
    private static final DidJSONParameters.CreateAndStoreMyDidJSONParameter CSO_STEWARD_DID_INFO = new DidJSONParameters.CreateAndStoreMyDidJSONParameter(null, DIDUtils.CSO_STEWARD_DID_SEED, null, null);

    private DIDUtils() {}

    public static DidResults.CreateAndStoreMyDidResult createEVDID(Wallet evWallet) throws IndyException {
        DidResults.CreateAndStoreMyDidResult did = null;
        try {
            did = Did.createAndStoreMyDid(evWallet, DIDUtils.EV_DID_INFO.toString()).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return did;
    }

    public static DidResults.CreateAndStoreMyDidResult createCSDID(Wallet csWallet) throws IndyException {
        DidResults.CreateAndStoreMyDidResult did = null;
        try {
            did = Did.createAndStoreMyDid(csWallet, DIDUtils.CS_DID_INFO.toString()).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return did;
    }

    public static DidResults.CreateAndStoreMyDidResult createAndWriteCSODID(Wallet csoWallet, Wallet csoStewardWallet, String csoStewardDID, Pool targetPool) throws IndyException, LedgerWriteException {
        DidResults.CreateAndStoreMyDidResult did = null;
        try {
            did = Did.createAndStoreMyDid(csoWallet, DIDUtils.CSO_DID_INFO.toString()).get();
            JSONObject csoRegistrationNymRequest = new JSONObject(Ledger.buildNymRequest(csoStewardDID, did.getDid(), did.getVerkey(), "CSO", "ENDORSER").get());
            JSONObject requestResult = new JSONObject(Ledger.signAndSubmitRequest(targetPool, csoStewardWallet, csoStewardDID, csoRegistrationNymRequest.toString()).get());
            Log.d(DIDUtils.class.toString(), requestResult.toString());

            if (!LedgerUtils.isLedgerResponseValid(requestResult)) {
                String ledgerError = LedgerUtils.getErrorMessage(requestResult);
                if (!ledgerError.contains("only the owner can modify it")) {
                    throw new LedgerWriteException(ledgerError);
                } else {
                    Log.d(DIDUtils.class.toString(), "DID previously written on the ledger");
                }
            }
            return did;
        } catch (InterruptedException | ExecutionException | JSONException e) {
            e.printStackTrace();
        }
        return did;
    }

    public static DidResults.CreateAndStoreMyDidResult createAndWriteDSODID(Wallet dsoWallet, Wallet dsoStewardWallet, String dsoStewardDID, Pool targetPool) throws IndyException, LedgerWriteException {
        DidResults.CreateAndStoreMyDidResult did = null;
        try {
            did = Did.createAndStoreMyDid(dsoWallet, DIDUtils.DSO_DID_INFO.toString()).get();
            JSONObject dsoRegistrationNymRequest = new JSONObject(Ledger.buildNymRequest(dsoStewardDID, did.getDid(), did.getVerkey(), "DSO", "ENDORSER").get());
            JSONObject requestResult = new JSONObject(Ledger.signAndSubmitRequest(targetPool, dsoStewardWallet, dsoStewardDID, dsoRegistrationNymRequest.toString()).get());
            Log.d(DIDUtils.class.toString(), requestResult.toString());

            if (!LedgerUtils.isLedgerResponseValid(requestResult)) {
                String ledgerError = LedgerUtils.getErrorMessage(requestResult);
                if (!ledgerError.contains("only the owner can modify it")) {
                    throw new LedgerWriteException(ledgerError);
                } else {
                    Log.d(DIDUtils.class.toString(), "DID previously written on the ledger");
                }
            }
            return did;
        } catch (InterruptedException | ExecutionException | JSONException e) {
            e.printStackTrace();
        }
        return did;
    }

    public static DidResults.CreateAndStoreMyDidResult createStewardDID(Wallet csoStewardWallet) throws IndyException {
        DidResults.CreateAndStoreMyDidResult did = null;
        try {
            did = Did.createAndStoreMyDid(csoStewardWallet, DIDUtils.CSO_STEWARD_DID_INFO.toString()).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return did;
    }
}
