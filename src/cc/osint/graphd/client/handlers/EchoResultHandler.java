package cc.osint.graphd.client.handlers;

import java.lang.*;
import java.util.*;
import java.util.concurrent.*;
import org.apache.log4j.Logger;
import org.json.*;

public class EchoResultHandler extends ProtographClientResultHandler {
    static Logger log = Logger.getLogger(EchoResultHandler.class);
        
    public void onResult(String result) {
        log.info("onResult: " + result);
    }
    
    public void onError(String err) {
        log.info("onError: " + err);
    }
}