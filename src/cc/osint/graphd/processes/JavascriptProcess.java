package cc.osint.graphd.processes;

import java.util.*;
import java.util.concurrent.*;
import java.lang.ref.*;
import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.jetlang.channels.*;
import org.jetlang.core.Callback;
import org.jetlang.core.Disposable;
import org.jetlang.core.Filter;
import org.jetlang.fibers.Fiber;
import org.jetlang.fibers.PoolFiberFactory;
import org.jetlang.fibers.ThreadFiber;
import cc.osint.graphd.graph.*;
import cc.osint.graphd.sim.*;
import cc.osint.graphd.script.*;

public class JavascriptProcess<T> extends GraphProcess<T, JSONObject> {
    
    private String key;
    private GScriptEngine scriptEngine;
    private ConcurrentHashMap<String, Object> stateMap;
    
    public JavascriptProcess(String key,
                             GScriptEngine scriptEngine) {
        this.key = key;
        this.scriptEngine = scriptEngine;
        stateMap = new ConcurrentHashMap<String, Object>();
    }
    
    protected void beforeKill() {
        try {
            log("beforeKill");
            scriptEngine.invoke("_udf_call", getPid(), "beforeKill");
        } catch (Exception ex) { ex.printStackTrace(); }
    }
    
    protected void beforeRemoveVertex(JSONVertex vertex) {
        try {
            log("beforeRemoveVertex");
            scriptEngine.invoke("_udf_call", getPid(), "beforeRemoveVertex", vertex);
        } catch (Exception ex) { ex.printStackTrace(); }
    }
    
    protected void beforeRemoveEdge(JSONEdge edge) {
        try {
            log("beforeRemoveEdge");
            scriptEngine.invoke("_udf_call", getPid(), "beforeRemoveEdge", edge);
        } catch (Exception ex) { ex.printStackTrace(); }
    }
    
    protected void afterRemoveEdge(JSONEdge edge) {
        try {
            log("afterRemoveEdge");
            scriptEngine.invoke("_udf_call", getPid(), "afterRemoveEdge", edge);
        } catch (Exception ex) { ex.printStackTrace(); }
    }
    
    public void message(JSONObject msg) {
        try {
            //log("JavascriptProcess: " + pid + ": " + msg.toString());
            // execute function
            Object msgObj = scriptEngine.invoke("_JSONstring_to_js", msg.toString());
            scriptEngine.invoke("_udf_call", getPid(), "message", msgObj);
            //log("stateMap = " + stateMap.toString());
        } catch (Exception ex) {
            ex.printStackTrace();
            try {
                if (getContext() instanceof JSONVertex) {
                    log(((JSONVertex)getContext()).getKey() + ": suicide :(");
                } else if (getContext() instanceof JSONEdge) {
                    log(((JSONEdge)getContext()).getKey() + ": suicide :(");
                } else if (getContext() instanceof EventObject) {
                    log(((EventObject)getContext()).toString() + ": suicide :(");
                } else {
                    log(getContext().toString() + ": suicide :(");
                }
                this.kill();
            } catch (Exception ex1) {
                ex1.printStackTrace();
            }
        }
    }
    
    public String getPid() {
        return super.getPid();
    }
    
    public ConcurrentHashMap<String, Object> getState() throws Exception {
        return stateMap;
    }
    
    @Override
    public void emit(String key, String processName, JSONObject msg) throws Exception {
        getGraph().emit(key, processName, msg);
    }
    
    public long nanoTime() {
        return System.nanoTime();
    }
}