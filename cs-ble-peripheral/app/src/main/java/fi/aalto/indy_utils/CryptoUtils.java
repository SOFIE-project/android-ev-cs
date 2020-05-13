package fi.aalto.indy_utils;

import org.hyperledger.indy.sdk.IndyException;
import org.hyperledger.indy.sdk.crypto.Crypto;
import org.hyperledger.indy.sdk.crypto.CryptoResults;
import org.hyperledger.indy.sdk.did.Did;
import org.hyperledger.indy.sdk.wallet.Wallet;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.ExecutionException;

public class CryptoUtils {

    private CryptoUtils() {}

    /**
     * @param storageWallet = result of WalletUtils.create<Entity>Wallet, where Entity is the party that will make use of the generated key
     * @return the generated key, after it has been saved in the given wallet. The key can be used to sign messages from and/or encrypt messages to the wallet owner.
     * @throws IndyException
     */
    public static String generateAndStoreKey(Wallet storageWallet) throws IndyException {
        String key = null;

        try {
            key = Crypto.createKey(storageWallet, new JSONObject().toString()).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

        return key;
    }

    /**
     * @param storageWallet = result of WalletUtils.create<Entity>Wallet, where Entity is the party that will make use of the generated key
     * @param seed = the seed to generate the key. Can be UTF-8, base64 or hex string
     * @return the generated key, after it has been saved in the given wallet. The key can be used to sign messages from and/or encrypt messages to the wallet owner.
     * @throws IndyException
     */
    public static String generateAndStoreKey(Wallet storageWallet, String seed) throws IndyException, KeyExistingException {
        if (seed == null) {
            return CryptoUtils.generateAndStoreKey(storageWallet);
        }

        String key = null;

        try {
            JSONObject configJSON = new JSONObject().put("seed", seed);
            key = Crypto.createKey(storageWallet, configJSON.toString()).get();
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (InterruptedException | ExecutionException e) {
            // Seed already used, return the result of generating the verkey from the given seed.
            try {
                key = Did.createAndStoreMyDid(storageWallet, new JSONObject().put("seed", seed).toString()).get().getVerkey();
            } catch (Exception ignored) {
            }
        }

        return key;
    }

    /**
     * @param receiverVerkey = the key to use to encrypt the message. The content is only accessible by the owner of the key. The result of either CryptoUtils.generateKey() or DIDUtils.createDID
     * @param message = the message to encrypt
     * @return the encrypted message.
     * @throws IndyException
     */
    public static byte[] encryptMessage(String receiverVerkey, byte[] message) throws IndyException {
        byte[] encryptedMessage = null;

        try {
            encryptedMessage = Crypto.anonCrypt(receiverVerkey, message).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

        return encryptedMessage;
    }

    /**
     * @param receiverWallet = result of WalletUtils.create<Entity>Wallet, where Entity is the party that owns the decryption key
     * @param receiverVerkey = the key to use to decrypt the message. The result of either CryptoUtils.generateKey() or DIDUtils.createDID
     * @param encryptedMessage = the encrypted message payload
     * @return the decrypted message (or some other encrypted payload if the original message had not been encrypted for the receiver).
     * @throws IndyException
     */
    public static byte[] decryptMessage(Wallet receiverWallet, String receiverVerkey, byte[] encryptedMessage) throws IndyException {
        byte[] decryptedMessage = null;

        try {
            decryptedMessage = Crypto.anonDecrypt(receiverWallet, receiverVerkey, encryptedMessage).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

        return decryptedMessage;
    }

    /**
     * @param senderWallet = result of WalletUtils.create<Entity>Wallet, where Entity is the party that owns the signing key
     * @param senderVerkey = the signing key to use. The result of either CryptoUtils.generateKey() or DIDUtils.createDID
     * @param message = the message over which the signature is generated
     * @return the signature over the given message using the given signing key.
     * @throws IndyException
     */
    public static byte[] generateMessageSignature(Wallet senderWallet, String senderVerkey, byte[] message) throws IndyException {
        byte[] signature = null;

        try {
            signature = Crypto.cryptoSign(senderWallet, senderVerkey, message).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

        return signature;
    }

    /**
     * @param senderVerkey = the verification key to use. The result of either CryptoUtils.generateKey() or DIDUtils.createDID
     * @param message = the message (not encrypted in this function)
     * @param signature = the signature over the given message
     * @return true if the signed over the message matches the given key. False otherwise.
     * @throws IndyException
     */
    public static boolean verifyMessageSignature(String senderVerkey, byte[] message, byte[] signature) throws IndyException {
        boolean validationResult = false;

        try {
            validationResult = Crypto.cryptoVerify(senderVerkey, message, signature).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

        return validationResult;
    }

    /**
     * @param senderWallet = result of WalletUtils.create<Entity>Wallet, where Entity is the party that owns the signing key
     * @param senderVerkey = the signing key to use. The result of either CryptoUtils.generateKey() or DIDUtils.createDID
     * @param receiverVerkey = the key to use to encrypt the message. The content is only accessible by the owner of the key. The result of either CryptoUtils.generateKey() or DIDUtils.createDID
     * @param message = the message to sign and encrypt
     * @return the signed and encrypted message.
     * @throws IndyException
     */
    public static byte[] signAndEncryptMessage(Wallet senderWallet, String senderVerkey, String receiverVerkey, byte[] message) throws IndyException {
        byte[] encryptedAndSignedMessage = null;

        try {
            encryptedAndSignedMessage = Crypto.authCrypt(senderWallet, senderVerkey, receiverVerkey, message).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

        return encryptedAndSignedMessage;
    }

    /**
     * @param receiverWallet = result of WalletUtils.create<Entity>Wallet, where Entity is the party that owns the decryption key
     * @param receiverVerkey = the key to use to decrypt the message. The result of either CryptoUtils.generateKey() or DIDUtils.createDID
     * @param senderVerkey = the signing key to use. The result of either CryptoUtils.generateKey() or DIDUtils.createDID
     * @param encryptedAndSignedMessage = the encrypt and signed payload to decrypt and verify the signature
     * @return the decrypted message if the signature is succesfully verified.
     * @throws IndyException
     * @throws MessageSignatureException
     */
    public static byte[] decryptAndVerifyMessage(Wallet receiverWallet, String receiverVerkey, String senderVerkey, byte[] encryptedAndSignedMessage) throws IndyException, MessageSignatureException {
        byte[] decryptedAndVerifiedMessage = null;

        try {
            CryptoResults.AuthDecryptResult authDecryptionResult = Crypto.authDecrypt(receiverWallet, receiverVerkey, encryptedAndSignedMessage).get();
            if (!authDecryptionResult.getVerkey().equals(senderVerkey)) {
                throw new MessageSignatureException(String.format("The message received does not match expected signature by %s.", senderVerkey));
            }
            decryptedAndVerifiedMessage = authDecryptionResult.getDecryptedMessage();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

        return decryptedAndVerifiedMessage;
    }
}
