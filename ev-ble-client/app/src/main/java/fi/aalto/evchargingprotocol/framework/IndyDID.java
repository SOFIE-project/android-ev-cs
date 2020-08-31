package fi.aalto.evchargingprotocol.framework;

import org.hyperledger.indy.sdk.IndyException;
import org.hyperledger.indy.sdk.crypto.Crypto;
import org.hyperledger.indy.sdk.did.Did;
import org.hyperledger.indy.sdk.did.DidJSONParameters;
import org.hyperledger.indy.sdk.did.DidResults;
import org.hyperledger.indy.sdk.wallet.Wallet;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.concurrent.ExecutionException;

public class IndyDID implements DID {

    public enum IndyEntity {
        ER, CSO;

        private String seed;
        private JSONObject walletConfig;

        static {
            try {
                ER.seed = "00000000000000000000000000ER-DID";
                ER.walletConfig = new JSONObject().put("id", ER.name()).put("storage_config", IndyDID.DEFAULT_WALLETS_CONFIG);

                CSO.seed = "0000000000000000000000000CSO-DID";
                CSO.walletConfig = new JSONObject().put("id", CSO.name()).put("storage_config", IndyDID.DEFAULT_WALLETS_CONFIG);
            } catch (JSONException e) {
                throw new RuntimeException(e.getMessage());
            }
        }
    }

    private static JSONObject DEFAULT_WALLETS_CREDENTIALS;
    private static JSONObject DEFAULT_WALLETS_CONFIG;

    static {
        try {
            DEFAULT_WALLETS_CREDENTIALS = new JSONObject().put("key", "password");
            DEFAULT_WALLETS_CONFIG = new JSONObject().put("path", IndyInitialiser.getWalletsPath());
        } catch (JSONException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    private Wallet wallet;
    private String did;
    private String verkey;

    IndyDID(IndyEntity entity) {
        try {
            if (!new File(String.format("%s/%s", IndyInitialiser.getWalletsPath(), entity.name())).exists()) {
                Wallet.createWallet(entity.walletConfig.toString(), IndyDID.DEFAULT_WALLETS_CREDENTIALS.toString()).get();
            }
            this.wallet = Wallet.openWallet(entity.walletConfig.toString(), IndyDID.DEFAULT_WALLETS_CREDENTIALS.toString()).get();
            DidResults.CreateAndStoreMyDidResult creationResult = Did.createAndStoreMyDid(this.wallet, new DidJSONParameters.CreateAndStoreMyDidJSONParameter(null, entity.seed, null, null).toString()).get();
            this.did = String.format("did:sov:%s", creationResult.getDid());
            this.verkey = creationResult.getVerkey();
        } catch (InterruptedException | ExecutionException | IndyException e) {
            String message = e.getMessage();
            if (e instanceof IndyException) {
                message = ((IndyException) e).getSdkMessage();
            }
            throw new RuntimeException(message);
        }
    }

    @Override
    public byte[] sign(byte[] data) {
        try {
            return Crypto.cryptoSign(this.wallet, this.verkey, data).get();
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public static boolean verifyFrom(String signerDID, byte[] data, byte[] signature) {
        IndyDID savedInstance = (IndyDID) DIDUtils.getDIDByName(signerDID);
        try {
            return Crypto.cryptoVerify(savedInstance.getVerkey(), data, signature).get();
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    @Override
    public String getDID() {
        return this.did;
    }

    public String getVerkey() {
        return this.verkey;
    }
}