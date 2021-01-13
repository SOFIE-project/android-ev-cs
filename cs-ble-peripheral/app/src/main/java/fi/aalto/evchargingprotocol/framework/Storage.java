package fi.aalto.evchargingprotocol.framework;

import java.util.HashMap;

class Storage {

    private HashMap<String, DID> dids = new HashMap<>();
    private HashMap<String, byte[]> peerDIDsVerificationKeys = new HashMap<>();
    private HashMap<String, byte[]> peerDIDsEncryptionKeys = new HashMap<>();

    Storage() {}

    void saveDID(DID did) {
        this.dids.put(did.getDID(), did);
    }

    DID retrieveDID(String did) {
        return this.dids.get(did);
    }

    void savePeerDIDVerificationKey(String did, byte[] verKey) {
        this.peerDIDsVerificationKeys.put(did, verKey);
    }

    void savePeerDIDEncryptionKey(String did, byte[] encryptionKey) {
        this.peerDIDsEncryptionKeys.put(did, encryptionKey);
    }

    byte[] getVerificationKeyForPeerDID(String did) {
        return this.peerDIDsVerificationKeys.get(did);
    }

    byte[] getEncryptionKeyForPeerDID(String did) {
        return this.peerDIDsEncryptionKeys.get(did);
    }
}
