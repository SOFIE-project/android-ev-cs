package fi.aalto.evchargingprotocol.framework;

import org.bitcoinj.core.Base58;
import org.json.JSONObject;
import org.webpki.jcs.JsonCanonicalizer;

import java.security.MessageDigest;

@FunctionalInterface
interface DataSigner {
    byte[] sign(byte[] input) throws Exception;
}

@FunctionalInterface
interface DataVerifier {
    boolean verify(String verificationDID, byte[] message, byte[] signature);
}

public class SignatureUtils {

    private SignatureUtils() {}

    // From: https://identity.foundation/JcsEd25519Signature2020/#jcs-ed25519-signature-2020
    static void addJcsEd25519Signature2020LinkedProof(JSONObject claims, String presenterDID, DataSigner signingFunction) {
        SignatureUtils.addJcsEd25519Signature2020LinkedProof(claims, presenterDID, null, signingFunction);
    }

    static void addJcsEd25519Signature2020LinkedProof(JSONObject claims, String presenterDID, String nonce, DataSigner signingFunction) {
        try {
            // 1. Take the input document, embedded with a proof block containing all values except the signatureValue
            JSONObject proof = new JSONObject()
                    .put("created", "2018-03-12T07:10:31Z")
                    .put("type", "JcsEd25519Signature2020")
                    .put("proofPurpose", "assertionMethod")
                    .put("verificationMethod", presenterDID);
            if (nonce != null) {
                proof.put("nonce", nonce);
            }
            claims.put("proof", proof);

            // 2. Canonicalize the document using JCS
            JsonCanonicalizer canonicalizer = new JsonCanonicalizer(claims.toString());
            String canonicalizedJSON = canonicalizer.getEncodedString();

            // 3. Apply the SHA-256 Hash Algorithm
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonicalizedJSON.getBytes());

            // 4. Sign the result with the private key
            byte[] signature = signingFunction.sign(digest);

            // 5. Base58 encode the result and set it as the signatureValue of the document
            proof.put("signatureValue", Base58.encode(signature));
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    static boolean verifyJCSEd25519Signature2020LinkedDocument(JSONObject signedDocument, DataVerifier verifiyingFunction) {
        try {
            JSONObject proofBackup =  new JSONObject(signedDocument.getJSONObject("proof").toString());
            byte[] base58DecodedSignature = Base58.decode(signedDocument.getJSONObject("proof").getString("signatureValue"));
            signedDocument.getJSONObject("proof").remove("signatureValue");

            JsonCanonicalizer canonicalizer = new JsonCanonicalizer(signedDocument.toString());
            String canonicalizedJSON = canonicalizer.getEncodedString();

            byte[] signedDocumentHash = MessageDigest.getInstance("SHA-256").digest(canonicalizedJSON.getBytes());
            String verificationDID = signedDocument.getJSONObject("proof").getString("verificationMethod");
            signedDocument.put("proof", proofBackup);

            return verifiyingFunction.verify(verificationDID, signedDocumentHash, base58DecodedSignature);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }
}