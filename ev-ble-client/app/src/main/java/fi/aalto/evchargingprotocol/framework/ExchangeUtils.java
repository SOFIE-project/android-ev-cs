package fi.aalto.evchargingprotocol.framework;

import com.goterl.lazycode.lazysodium.exceptions.SodiumException;
import com.goterl.lazycode.lazysodium.interfaces.Box;
import com.goterl.lazycode.lazysodium.utils.KeyPair;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Base64;

public class ExchangeUtils {

    private static Box.Native nativeBox = (Box.Native) Initialiser.androidSodium;

    private ExchangeUtils() {}

    public static KeyPair getCSInvitationKeyPair() {
        try {
            Box.Lazy lazyBox = (Box.Lazy) Initialiser.androidSodium;
            byte[] seed = "CSKEY".getBytes();
            byte[] completeSeed = new byte[Box.SEEDBYTES];
            System.arraycopy(seed, 0, completeSeed, completeSeed.length - seed.length, seed.length);
            return lazyBox.cryptoBoxSeedKeypair(completeSeed);
        } catch (SodiumException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public static byte[] encryptFor(byte[] publicKey, byte[] data) {
        try {
            byte[] cipherText = new byte[data.length + Box.SEALBYTES];
            ExchangeUtils.nativeBox.cryptoBoxSeal(cipherText, data, data.length, publicKey);
            return cipherText;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public static byte[] decrypt(byte[] data, byte[] receiverDecryptionKey, byte[] receiverEncryptionKey) {
        byte[] message = new byte[data.length - Box.SEALBYTES];
        boolean decryptionResult = ExchangeUtils.nativeBox.cryptoBoxSealOpen(message, data, data.length, receiverEncryptionKey, receiverDecryptionKey);
        if (!decryptionResult) {
            throw new RuntimeException("Decryption failed.");
        }
        return message;
    }

    public static JSONObject createExchangeInvitation(byte[] csEncryptionKey, String csoName) {
        JSONObject exchangeInvitation = null;

        try {
            exchangeInvitation = new JSONObject()
                    .put("invitation", new JSONObject()
                            .put("@type", "https://didcomm.org/didexchange/1.0/invitation")
                            .put("@id", "urn:uuid:D23CAC23-7AD9-4FEF-A46D-1A543C1BD36F")
                            .put("label", String.format("CSO %s - CS 1412/2", csoName))
                            .put("recipientKeys", new JSONArray(new String[]{PeerDID.getMultiBaseKey(csEncryptionKey)}))
                    )
                    .put("https://w3c.org/2020/credentials/cso-info/v1/CSO", csoName);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }

        return exchangeInvitation;
    }

    public static JSONObject createExchangeRequest(JSONObject exchangeInvitation, PeerDID evDID) {
        try {
            String exchangeInvitationID = exchangeInvitation.getString("@id");

            return new JSONObject()
                    .put("request", new JSONObject()
                            .put("@id", "urn:uuid:7B3EA71C-0149-4011-93A4-D8F94AD48EE7")
                            .put("@type", "https://didcomm.org/didexchange/1.0/request")
                            .put("~thread", new JSONObject()
                                    .put("pthid", exchangeInvitationID)
                            )
                            .put("label", "EV 721893")
                            .put("connection", new JSONObject()
                                    .put("did", evDID.getDID())
                            )
                    );
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public static JSONObject createExchangeResponse(JSONObject exchangeRequest, JSONObject csInfocredential, JSONObject csPresentation, PeerDID csDID) {
        try {
            String exchangeInvitationID = exchangeRequest.getJSONObject("~thread").getString("pthid");

            return new JSONObject()
                    .put("response", new JSONObject()
                            .put("@type", "https://didcomm.org/didexchange/1.0/response")
                            .put("@id", "uri:uuid:32F7436E-CFE2-4176-B158-7C1B4F16312C")
                            .put("~thread", new JSONObject()
                                    .put("thid", exchangeInvitationID)
                            )
                            .put("connection", new JSONObject()
                                    .put("did", csDID.getDID())
                            )
                    )
                    .put("verifiableCredential", new JSONArray(new String[] {Base64.getEncoder().encodeToString(csInfocredential.toString().getBytes())}))
                    .put("presentation", csPresentation);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public static JSONObject createExchangeComplete(JSONObject exchangeResponse, JSONObject evChargingCredential, JSONObject evPresentation) {
        try {
            String exchangeInvitationID = exchangeResponse.getJSONObject("~thread").getString("thid");

            return new JSONObject()
                    .put("complete", new JSONObject()
                            .put("@type", "https://didcomm.org/didexchange/1.0/complete")
                            .put("@id", "uri:uuid:D8408EA8-6687-4C30-8EDC-D074E28C657B")
                            .put("~thread", new JSONObject()
                                    .put("pthid", exchangeInvitationID)
                                    .put("thid", exchangeInvitationID)
                            )
                    )
                    .put("verifiableCredential", new JSONArray(new String[] {Base64.getEncoder().encodeToString(evChargingCredential.toString().getBytes())}))
                    .put("presentation", evPresentation);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }
}