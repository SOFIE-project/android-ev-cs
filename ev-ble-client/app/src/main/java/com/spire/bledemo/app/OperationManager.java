package com.spire.bledemo.app;

import java.util.LinkedList;
import java.util.Queue;

public class OperationManager {
    private Queue<Runnable> operations = new LinkedList<Runnable>();

    private Runnable currentOp;


    public synchronized void request(Runnable operation) {
        operations.add(operation);
        if( currentOp == null ) {
            currentOp = operations.poll();
        }

        currentOp.run();
    }

    public synchronized void operationCompleted() {
        currentOp = null;
        if( operations.peek() != null ) {
            currentOp = operations.poll();
            currentOp.run();
        }

    }

    public synchronized void nudge() {
        if(currentOp != null) {
            currentOp.run();
        }
    }

}
