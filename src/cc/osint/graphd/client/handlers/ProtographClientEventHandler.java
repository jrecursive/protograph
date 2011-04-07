package cc.osint.graphd.client.handlers;

import java.lang.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

public abstract class ProtographClientEventHandler {
    
    public abstract void onStatusEvent(String status);
    public abstract void onMessageEvent(String channel, String message);
    public void onProtographEventException(Throwable throwable) {
        throwable.printStackTrace();
    }
    
}