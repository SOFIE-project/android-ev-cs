package fi.aalto.evchargingprotocol.framework;

import com.goterl.lazycode.lazysodium.interfaces.Box;
import com.goterl.lazycode.lazysodium.interfaces.Sign;

import org.bitcoinj.core.Base58;

import java.util.Arrays;

public class PeerDID implements DID {

    private static String peerDIDMethod0Prefix = "0";
    private static String base58MultiEncodePrefix = "z";
    private static byte ed25519MulticodecPrefix = (byte) 0xed;

    private static Sign.Native nativeSign = (Sign.Native) Initialiser.androidSodium;
    private static Box.Native nativeBox = (Box.Native) Initialiser.androidSodium;

    private String did;
    private byte[] signingKey;
    private byte[] verificationKey;
    private byte[] decryptionKey;
    private byte[] encryptionKey;


    public enum PeerEntity {
        EV, CS;

        private byte[] seed;

        static {
            EV.seed = "EV-DID-SECRET-SEED-0000000000000".getBytes();
            CS.seed = "CS-DID-SECRET-SEED-0000000000000".getBytes();
        }

        public byte[] getSeed() {
            return this.seed;
        }
    }

    public PeerDID(PeerEntity entity) {
        this.signingKey = new byte[Sign.ED25519_SECRETKEYBYTES];
        this.verificationKey = new byte[Sign.ED25519_PUBLICKEYBYTES];
        boolean keyCreationResult = PeerDID.nativeSign.cryptoSignSeedKeypair(this.verificationKey, this.signingKey, entity.getSeed());
        this.decryptionKey = new byte[Box.SECRETKEYBYTES];
        this.encryptionKey = new byte[Box.PUBLICKEYBYTES];
        PeerDID.nativeSign.convertSecretKeyEd25519ToCurve25519(this.decryptionKey, this.signingKey);
        PeerDID.nativeSign.convertPublicKeyEd25519ToCurve25519(this.encryptionKey, this.verificationKey);
        if (!keyCreationResult) {
            throw new RuntimeException("Keys not created correctly.");
        }
        this.did = PeerDID.getDIDFromVerkey(this.verificationKey);
    }

    public PeerDID(byte[] seed) {
        this.signingKey = new byte[Sign.ED25519_SECRETKEYBYTES];
        this.verificationKey = new byte[Sign.ED25519_PUBLICKEYBYTES];
        boolean keyCreationResult = PeerDID.nativeSign.cryptoSignSeedKeypair(this.verificationKey, this.signingKey, seed);
        this.decryptionKey = new byte[Box.SECRETKEYBYTES];
        this.encryptionKey = new byte[Box.PUBLICKEYBYTES];
        PeerDID.nativeSign.convertSecretKeyEd25519ToCurve25519(this.decryptionKey, this.signingKey);
        PeerDID.nativeSign.convertPublicKeyEd25519ToCurve25519(this.encryptionKey, this.verificationKey);
        if (!keyCreationResult) {
            throw new RuntimeException("Keys not created correctly.");
        }
        this.did = PeerDID.getDIDFromVerkey(this.verificationKey);
    }


    public static byte[] getEncKeyFromVerKey (byte[] verKey) {
        byte[] encKey = new byte[Box.PUBLICKEYBYTES];
        PeerDID.nativeSign.convertPublicKeyEd25519ToCurve25519(encKey, verKey);
        return encKey;
    }

    // Only ed25519 key supported
    private static String getDIDFromVerkey(byte[] verkey) {
        return String.format("did:peer:%s", PeerDID.getMultiBaseKey(verkey));
    }

    // Only ed25519 key supported
    static String getMultiBaseKey(byte[] key) {
        byte[] multicodecEncodedKey = PeerDID.getMulticodecForKey(key);
        return String.format("%s%s%s", PeerDID.peerDIDMethod0Prefix, PeerDID.base58MultiEncodePrefix, Base58.encode(multicodecEncodedKey));
    }

    // Only ed25519 key supported
    private static byte[] getMulticodecForKey(byte[] key) {
        byte[] multiCodecPayload = new byte[key.length+1];
        multiCodecPayload[0] = PeerDID.ed25519MulticodecPrefix;
        System.arraycopy(key, 0, multiCodecPayload, 1, key.length);
        return multiCodecPayload;
    }

    // Only ed25519 key supported
    public static byte[] getVerkeyFromDID(String did) {
        if (!did.startsWith("did:peer:")) {
            throw new RuntimeException("DID is not a peer DID.");
        }

        String encodedKey = did.split(":")[2].substring(2);     // Discard the '0' and 'z' chars
        byte[] decoded = Base58.decode(encodedKey);
        return Arrays.copyOfRange(decoded, 1, decoded.length);      // Discard the 0xed initial byte
    }

    public static byte[] getPublicKeyFromMultiBaseKey (String multiBaseKey ) {
        String encodedKey = multiBaseKey.substring(2);     // Discard the ?
        byte[] decoded = Base58.decode(encodedKey);
        return Arrays.copyOfRange(decoded, 1, decoded.length);      // Discard the 0xed initial byte
    }

    @Override
    public byte[] sign(byte[] data) {
        byte[] signature = new byte[Sign.BYTES];
        boolean signatureResult = PeerDID.nativeSign.cryptoSignDetached(signature, data, data.length, this.signingKey);
        if (!signatureResult) {
            throw new RuntimeException("Signing failed.");
        }
        return signature;
    }

    public static boolean verifyFrom(String signerDID, byte[] data, byte[] signature) {
        if (!signerDID.startsWith("did:peer:")) {
            throw new RuntimeException("DID is not a peer DID.");
        }
        try {
            byte[] signerVerkey = DIDUtils.getVerkeyForPeerDID(signerDID);
            if (signerVerkey == null) {
                signerVerkey = PeerDID.getVerkeyFromDID(signerDID);
                DIDUtils.savePeerDIDVerkey(signerDID, signerVerkey);
            }
            return PeerDID.nativeSign.cryptoSignVerifyDetached(signature, data, data.length, signerVerkey);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public byte[] decrypt(byte[] data) {
        byte[] message = new byte[data.length - Box.SEALBYTES];
        boolean decryptionResult = PeerDID.nativeBox.cryptoBoxSealOpen(message, data, data.length, this.encryptionKey, this.decryptionKey);
        if (!decryptionResult) {
            throw new RuntimeException("Decryption failed.");
        }
        return message;
    }

    @Override
    public String getDID() {
        return this.did;
    }

    public byte[] getEncryptionKey() {
        return this.encryptionKey;
    }
}
