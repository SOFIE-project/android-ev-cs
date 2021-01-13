package fi.aalto.evchargingprotocol.framework;

public interface DID {
    byte[] sign(byte[] data);
    String getDID();
}
