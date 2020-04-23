package fi.aalto.indy_utils;

import androidx.annotation.Nullable;

import org.hyperledger.indy.sdk.IndyException;
import org.hyperledger.indy.sdk.anoncreds.Anoncreds;
import org.hyperledger.indy.sdk.wallet.Wallet;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.ExecutionException;

public final class WalletUtils {

    private static JSONObject EV_WALLET_CONFIG;
    private static JSONObject CSO_WALLET_CONFIG;
    private static JSONObject DSO_WALLET_CONFIG;
    private static JSONObject CS_WALLET_CONFIG;
    private static JSONObject ER_WALLET_CONFIG;
    private static JSONObject CSO_STEWARD_WALLET_CONFIG;

    private static JSONObject DEFAULT_WALLETS_CREDENTIALS;

    static {
        try {
            WalletUtils.EV_WALLET_CONFIG = new JSONObject().put("id", "evWallet");
            WalletUtils.CSO_WALLET_CONFIG = new JSONObject().put("id", "csoWallet");
            WalletUtils.DSO_WALLET_CONFIG = new JSONObject().put("id", "dsoWallet");
            WalletUtils.CS_WALLET_CONFIG = new JSONObject().put("id", "csWallet");
            WalletUtils.ER_WALLET_CONFIG = new JSONObject().put("id", "erWallet");
            WalletUtils.CSO_STEWARD_WALLET_CONFIG = new JSONObject().put("id", "csoStewardWallet");

            WalletUtils.DEFAULT_WALLETS_CREDENTIALS = new JSONObject().put("key", "password");
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private WalletUtils() {}

    // Wallet creation

    /**
     * @throws IndyException
     */
    public static void createEVWallet() throws IndyException {
        try {
            Wallet.createWallet(WalletUtils.EV_WALLET_CONFIG.toString(), WalletUtils.DEFAULT_WALLETS_CREDENTIALS.toString()).get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException ignored) {}        // Wallet already exists
    }

    /**
     * @throws IndyException
     */
    public static void createCSWallet() throws IndyException {
        try {
            Wallet.createWallet(WalletUtils.CS_WALLET_CONFIG.toString(), WalletUtils.DEFAULT_WALLETS_CREDENTIALS.toString()).get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException ignored) {}        // Wallet already exists
    }

    /**
     * @throws IndyException
     */
    public static void createCSOWallet() throws IndyException {
        try {
            Wallet.createWallet(WalletUtils.CSO_WALLET_CONFIG.toString(), WalletUtils.DEFAULT_WALLETS_CREDENTIALS.toString()).get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException ignored) {}        // Wallet already exists
    }

    /**
     * @throws IndyException
     */
    public static void createDSOWallet() throws IndyException {
        try {
            Wallet.createWallet(WalletUtils.DSO_WALLET_CONFIG.toString(), WalletUtils.DEFAULT_WALLETS_CREDENTIALS.toString()).get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException ignored) {}        // Wallet already exists
    }

    /**
     * @throws IndyException
     */
    public static void createERWallet() throws IndyException {
        try {
            Wallet.createWallet(WalletUtils.ER_WALLET_CONFIG.toString(), WalletUtils.DEFAULT_WALLETS_CREDENTIALS.toString()).get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException ignored) {}        // Wallet already exists
    }

    /**
     * @throws IndyException
     */
    public static void createStewardWallet() throws IndyException {
        try {
            Wallet.createWallet(WalletUtils.CSO_STEWARD_WALLET_CONFIG.toString(), WalletUtils.DEFAULT_WALLETS_CREDENTIALS.toString()).get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException ignored) {}        // Wallet already exists
    }

    // Wallet opening

    /**
     * @return the handle to the EV wallet.
     * @throws IndyException
     */
    public static Wallet openEVWallet() throws IndyException {
        Wallet wallet = null;
        try {
            wallet = Wallet.openWallet(WalletUtils.EV_WALLET_CONFIG.toString(), WalletUtils.DEFAULT_WALLETS_CREDENTIALS.toString()).get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException ignored) {}
        return wallet;
    }

    /**
     * @return the handle to the CS wallet.
     * @throws IndyException
     */
    public static Wallet openCSWallet() throws IndyException {
        Wallet wallet = null;
        try {
            wallet = Wallet.openWallet(WalletUtils.CS_WALLET_CONFIG.toString(), WalletUtils.DEFAULT_WALLETS_CREDENTIALS.toString()).get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException ignored) {}
        return wallet;
    }

    /**
     * @return the handle to the CSO wallet.
     * @throws IndyException
     */
    public static Wallet openCSOWallet() throws IndyException {
        Wallet wallet = null;
        try {
            wallet = Wallet.openWallet(WalletUtils.CSO_WALLET_CONFIG.toString(), WalletUtils.DEFAULT_WALLETS_CREDENTIALS.toString()).get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException ignored) {}
        return wallet;
    }

    /**
     * @return the handle to the DSO wallet.
     * @throws IndyException
     */
    public static Wallet openDSOWallet() throws IndyException {
        Wallet wallet = null;
        try {
            wallet = Wallet.openWallet(WalletUtils.DSO_WALLET_CONFIG.toString(), WalletUtils.DEFAULT_WALLETS_CREDENTIALS.toString()).get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException ignored) {}
        return wallet;
    }

    /**
     * @return the handle to the ER wallet.
     * @throws IndyException
     */
    public static Wallet openERWallet() throws IndyException {
        Wallet wallet = null;
        try {
            wallet = Wallet.openWallet(WalletUtils.ER_WALLET_CONFIG.toString(), WalletUtils.DEFAULT_WALLETS_CREDENTIALS.toString()).get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException ignored) {}
        return wallet;
    }

    /**
     * @return the handle to the steward wallet.
     * @throws IndyException
     */
    public static Wallet openStewardWallet() throws IndyException {
        Wallet wallet = null;
        try {
            wallet = Wallet.openWallet(WalletUtils.CSO_STEWARD_WALLET_CONFIG.toString(), WalletUtils.DEFAULT_WALLETS_CREDENTIALS.toString()).get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException ignored) {}
        return wallet;
    }

    // Credential saving

    /**
     * @param wallet = the handle of the wallet to save the credential
     * @param credentialRequestMetadata = result of getCredentialJson called on an instance of AnoncredsResults.IssuerCreateCredentialResult
     * @param credential = result of getCredentialJson called on an instance of AnoncredsResults.IssuerCreateCredentialResult
     * @param credentialDefinition = result of calling getJSONObject("object") on the JSON returned by CredentialDefinitionUtils.readCredentialDefinitionFromLedger for the given credential
     * @param revocationDelta = result of getRevocRegDeltaJson() called on an instance of AnoncredsResults.IssuerCreateCredentialResult (cannot be null)
     * @throws IndyException
     */
    public static void saveCredential(Wallet wallet, JSONObject credentialRequestMetadata, JSONObject credential, JSONObject credentialDefinition, @Nullable JSONObject revocationDelta) throws IndyException {
        try {
            Anoncreds.proverStoreCredential(wallet, null, credentialRequestMetadata.toString(), credential.toString(), credentialDefinition.toString(), revocationDelta != null ? revocationDelta.toString() : null).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }
}