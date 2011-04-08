package cc.osint.graphd.client.handlers;

import java.lang.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.json.*;

public abstract class ProtographClientResultHandler {
    Throwable throwable = null;
    private AtomicBoolean hasExecuted;

    public abstract void onResult(String result);
    
    public void onError(String err) {
        this.throwable = new Exception(err);
    }
    
    public ProtographClientResultHandler() {
        hasExecuted = new AtomicBoolean(false);
    }
    
    public void onComplete() {
        hasExecuted.set(true);
    }

    public boolean isComplete() {
        return hasExecuted.get();
    }
    
    public void onException(Throwable throwable) {
        this.throwable = throwable;
        throwable.printStackTrace();
    }
    
    public Throwable getException() {
        return this.throwable;
    }
}