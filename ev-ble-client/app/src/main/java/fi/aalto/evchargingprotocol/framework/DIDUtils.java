package fi.aalto.evchargingprotocol.framework;

public class DIDUtils {

    private static Storage didsStorage = new Storage();

    public static PeerDID getEVDID() {
        PeerDID evDID = new PeerDID(PeerDID.PeerEntity.EV);
        DIDUtils.didsStorage.saveDID(evDID);
        return evDID;
    }

    public static PeerDID getCSDID() {
        PeerDID csDID = new PeerDID(PeerDID.PeerEntity.CS);
        DIDUtils.didsStorage.saveDID(csDID);
        return csDID;
    }

    public static IndyDID getERDID() {
        IndyDID erDID = new IndyDID(IndyDID.IndyEntity.ER);
        DIDUtils.didsStorage.saveDID(erDID);
        return erDID;
    }

    public static IndyDID getCSODID() {
        IndyDID csoDID = new IndyDID(IndyDID.IndyEntity.CSO);
        DIDUtils.didsStorage.saveDID(csoDID);
        return csoDID;
    }

    static DID getDIDByName(String did) {
        return DIDUtils.didsStorage.retrieveDID(did);
    }

    static void savePeerDIDVerkey(String did, byte[] verkey) {
        DIDUtils.didsStorage.savePeerDIDVerificationKey(did, verkey);
    }

    static byte[] getVerkeyForPeerDID(String did) {
        return DIDUtils.didsStorage.getVerificationKeyForPeerDID(did);
    }

    static void savePeerDIDEncryptionKey(String did, byte[] encryptionKey) {
        DIDUtils.didsStorage.savePeerDIDEncryptionKey(did, encryptionKey);
    }

    static byte[] getEncryptionKeyForPeerDID(String did) {
        return DIDUtils.didsStorage.getEncryptionKeyForPeerDID(did);
    }
}
