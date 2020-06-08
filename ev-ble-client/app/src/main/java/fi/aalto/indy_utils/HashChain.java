package fi.aalto.indy_utils;

import org.bitcoinj.core.Base58;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class HashChain {
    private class HashChainElement<T> {
        T data;
        HashChainElement<T> previous;
        HashChainElement<T> next;

        HashChainElement(T data) {
            this.data = data;
        }
    }

    private HashChainElement<byte[]> hashChainPointer;
    private String hashingFunction;

    // Assumed length to be at least 1.
    public HashChain(String seed, int length, String hashFunctionName) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance(hashFunctionName);
        this.hashingFunction = hashFunctionName;
        this.hashChainPointer = new HashChainElement<>(md.digest(seed.getBytes()));

        for (int i = 1; i <= length; i++) {
            HashChainElement<byte[]> newElement = new HashChainElement<>(md.digest(this.hashChainPointer.data));
            this.hashChainPointer.next = newElement;
            newElement.previous = this.hashChainPointer;
            this.hashChainPointer = newElement;
        }
    }

    // Can return null if the chain length is passed.
    public String revealNextChainStep() {
        if (this.hashChainPointer == null) {
            return null;
        }
        byte[] nextStepValue = this.hashChainPointer.data;
        this.hashChainPointer = this.hashChainPointer.previous;

        return Base58.encode(nextStepValue);
    }

    /**
     * @param hashFunctionName the function to hash the next element and check for equality with the previous one
     * @param nextStep = the next step in the hashchain so that H(nextStep) = previousStep
     * @param previousStep = the previous (revealed at the previous iteration) step in the hashchain
     * @return true if the next step revealed is the previous step in the hashchain, i.e., if H(nextStep) == previousStep. False otherwise.
     * @throws NoSuchAlgorithmException
     */
    public static boolean isNextStepValid(String hashFunctionName, String nextStep, String previousStep) throws NoSuchAlgorithmException {
        byte[] nextStepDecoded = Base58.decode(nextStep);
        byte[] previousStepDecoded = Base58.decode(previousStep);
        byte[] nextStepHashedDecoded = MessageDigest.getInstance(hashFunctionName).digest(nextStepDecoded);
        return Arrays.equals(nextStepHashedDecoded, previousStepDecoded);
    }

    public String getHashingFunction() {
        return this.hashingFunction;
    }
}
