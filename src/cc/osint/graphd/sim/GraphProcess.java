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
    private String pid = null;
    
    protected void setup(String pid,
                         WeakReference<Graph> graphRef,
                         T context,
                         String name,
                         Fiber fiber, 
                         Channel channel) {
        this.pid = pid;
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
        fiber = null;
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
    
    public Channel<M> getChannel() {
        return channel;
    }
    
    public void log(String s) {
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
    
    public void emitByQuery(String query, JSONObject msg) throws Exception {
        getGraph().emitByQuery(query, msg);
    }
    
    public void onMessage(M msg) {
        message(msg);
    }
    
    public String getPid() {
        return pid;
    }
    
    //
    
    protected abstract void beforeKill();
    protected abstract void beforeRemoveVertex(JSONVertex vertex);
    protected abstract void beforeRemoveEdge(JSONEdge edge);
    protected abstract void afterRemoveEdge(JSONEdge edge);
    protected abstract void message(M msg);
    
}