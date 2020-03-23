package io.github.webbluetoothcg.bletestperipheral;

import java.util.LinkedList;
import java.util.Queue;

public class MessageBuffer {
    private Queue<String> messages = new LinkedList<>();
    private int chunkSize;


    public MessageBuffer(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public void add(String message) {
        messages.add(message);
    }

    public String poll() {
        return messages.poll();
    }

    public String extract() {
        StringBuilder queueBuffer = new StringBuilder();
        while(!messages.isEmpty()){
            queueBuffer.append((messages.poll()));
        }
        return queueBuffer.toString();
    }

}
