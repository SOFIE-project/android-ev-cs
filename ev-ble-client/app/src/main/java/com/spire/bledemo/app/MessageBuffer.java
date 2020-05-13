package com.spire.bledemo.app;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;

public class MessageBuffer {
    private Queue<byte[]> messages = new LinkedList<>();
    private int chunkSize;


    public MessageBuffer(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public void add(byte[] message) {
        messages.add(message);
    }

    public byte[] poll() {
        return messages.poll();
    }

//    public String extract() {
//        StringBuilder queueBuffer = new StringBuilder();
//        while (!messages.isEmpty()) {
//            queueBuffer.append(messages.poll());
//        }
//        return queueBuffer.toString();
//    }

    public byte[] extract() {
        ByteArrayOutputStream delimitedStream = new ByteArrayOutputStream();
        try {
            while (!messages.isEmpty()) {
                delimitedStream.write(messages.poll());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return delimitedStream.toByteArray();
    }
}
