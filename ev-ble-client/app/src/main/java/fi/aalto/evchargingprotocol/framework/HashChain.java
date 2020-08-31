package fi.aalto.evchargingprotocol.framework;

import androidx.annotation.NonNull;

import org.bitcoinj.core.Base58;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class HashChain {

    public enum HashingAlgorithm {
        SHA256;

        @NonNull
        @Override
        public String toString() {
            String result = null;
            switch (this) {
                case SHA256: result = "SHA-256";
            }
            return result;
        }
    }

    private static class HashChainElement<T> {
        T data;
        HashChainElement<T> previous;
        HashChainElement<T> next;

        HashChainElement(T data) {
            this.data = data;
        }
    }

    private HashChainElement<byte[]> hashChainPointer;
    private String root;
    private HashingAlgorithm hashingFunction;

    // Assumed length to be at least 1.
    public HashChain(String seed, int length, HashingAlgorithm algorithm) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance(algorithm.toString());
        this.hashingFunction = algorithm;
        this.hashChainPointer = new HashChainElement<>(md.digest(seed.getBytes()));

        for (int i = 1; i < length + 1; i++) {
            HashChainElement<byte[]> newElement = new HashChainElement<>(md.digest(this.hashChainPointer.data));
            this.hashChainPointer.next = newElement;
            newElement.previous = this.hashChainPointer;
            this.hashChainPointer = newElement;
        }
        this.root = Base58.encode(this.hashChainPointer.data);
    }

    // Can return null if the chain length is passed.
    public String revealNextChainStep() {
        if (this.hashChainPointer == null) {
            return null;
        }
        this.hashChainPointer = this.hashChainPointer.previous;
        byte[] nextStepValue = this.hashChainPointer.data;

        return Base58.encode(nextStepValue);
    }

    public static boolean isNextStepValid(String hashFunctionName, String nextStep, String previousStep) throws NoSuchAlgorithmException {
        byte[] nextStepDecoded = Base58.decode(nextStep);
        byte[] previousStepDecoded = Base58.decode(previousStep);
        byte[] nextStepHashedDecoded = MessageDigest.getInstance(hashFunctionName).digest(nextStepDecoded);
        return Arrays.equals(nextStepHashedDecoded, previousStepDecoded);
    }

    public String getHashingFunction() {
        return this.hashingFunction.toString();
    }

    public String getRoot() {
        return this.root;
    }
}
