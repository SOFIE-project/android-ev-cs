package fi.aalto.evchargingprotocol.framework;

import org.bitcoinj.core.Base58;
import org.json.JSONArray;

import java.util.ArrayList;

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

    public static JSONArray getBulkCSDID(int numDids) {
        JSONArray pbKeyList = new JSONArray();
        PeerDID peerDID = new PeerDID(PeerDID.PeerEntity.CS.getSeed());
        pbKeyList.put(peerDID.getDID());
        DIDUtils.didsStorage.saveDID(peerDID);

        for( int i=1; i < numDids; i++) {
            String didSeed = (new String(PeerDID.PeerEntity.CS.getSeed())).substring(0, 28) + String.format("%04d", i);
            peerDID = new PeerDID(didSeed.getBytes());
            pbKeyList.put(peerDID.getDID());
        }
        return  pbKeyList;
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
