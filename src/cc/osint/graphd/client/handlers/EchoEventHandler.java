package cc.osint.graphd.client.handlers;

import java.lang.*;
import java.util.*;
import java.util.concurrent.*;
import org.apache.log4j.Logger;
import org.json.*;

public class EchoEventHandler extends ProtographClientEventHandler {
    static Logger log = Logger.getLogger(EchoEventHandler.class);
    
    public void onStatusEvent(String status) {
        log.info("onGenericEvent: " + status);
    }
    
    public void onMessageEvent(String channel, String message) {
        try {
            JSONObject obj = new JSONObject(message);
            log.info("onMessageEvent: <" + channel + "> " + obj.toString(4));
            //log.info("onMessageEvent: <" + channel + "> " + message);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    @Override
    public void onProtographEventException(Throwable throwable) {
        throwable.printStackTrace();
    }
    
}