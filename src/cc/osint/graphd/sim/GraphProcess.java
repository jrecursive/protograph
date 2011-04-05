package cc.osint.graphd.sim;

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

/*
 * T : Type of "context object" (e.g., JSONVertex, JSONEdge, Graph)
 * M : Messaging type (e.g., JSONObject)
 *
*/
public abstract class GraphProcess<T, M> implements Callback<M> {
    
    private String name = null;
    private Fiber fiber = null;
    private Channel<M> channel = null;
    private WeakReference<T> contextRef = null;
    private WeakReference<Graph> graphRef = null;
    
    protected void setup(WeakReference<Graph> graphRef,
                         T context,
                         String name,
                         Fiber fiber, 
                         Channel channel) {
        this.name = name;
        this.fiber = fiber;
        this.channel = channel;
        this.graphRef = graphRef;
        this.contextRef = new WeakReference<T>(context);
        log("setup: " + name + " fiber = " + 
            fiber + ", channel = " + channel);
    }
    
    public void kill() throws Exception {
        beforeKill();
        fiber.dispose();
        log("process: kill: " + name);
    }
    
    public void publish(M msg) throws Exception {
        channel.publish(msg);
        //log("process: send: " + name + ": " + msg);
    }
    
    public Graph getGraph() {
        return graphRef.get();
    }
    
    public T getContext() {
        return contextRef.get();
    }
    
    public String getName() {
        return name;
    }
    
    protected void log(String s) {
        try {
            Thread t = Thread.currentThread();
            System.out.println(t.getId() + ": " + name + ": " + s);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    protected void emit(String key, String processName, JSONObject msg) throws Exception {
        getGraph().emit(key, processName, msg);
    }
    
    public void onMessage(M msg) {
        message(msg);
    }
    
    //
    
    protected abstract void beforeKill();
    protected abstract void beforeRemoveVertex();
    protected abstract void beforeRemoveEdge();
    protected abstract void afterRemoveEdge();
    protected abstract void message(M msg);
    
}