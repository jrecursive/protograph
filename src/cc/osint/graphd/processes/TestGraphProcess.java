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

public class TestGraphProcess extends GraphProcess<JSONVertex, JSONObject> {
    
    public TestGraphProcess() {
        //log("new TestGraphProcess!");
    }
    
    protected void beforeKill() {
        log("beforeKill");
    }
    
    protected void beforeRemoveVertex(JSONVertex vertex) {
        log("beforeRemoveVertex");
    }
    
    protected void beforeRemoveEdge(JSONEdge edge) {
        log("beforeRemoveEdge");
    }
    
    protected void afterRemoveEdge(JSONEdge edge) {
        log("afterRemoveEdge");
    }
    
    public void message(JSONObject msg) {
        try {
            JSONObject visited;
            if (!msg.has("visited")) {
                visited = new JSONObject();
            } else {
                visited = msg.getJSONObject("visited");
                if (visited.has(getContext().getString(Graph.KEY_FIELD))) {
                    return;
                }
            }
            visited.put(getContext().getString(Graph.KEY_FIELD), System.nanoTime());
            msg.put("visited", visited);
            
            //log(getContext().getString(Graph.KEY_FIELD) + ": msg = " + msg);
            int maxLen = 9999999;
            if (msg.has("maxlen")) {
                maxLen = msg.getInt("maxlen");
            }
            
            int msgsSent = 0;
            if (JSONObject.getNames(visited).length < maxLen) {
                // spread like wildfire!
                Set<String> rels = null;
                if (msg.has("via")) {
                    rels = new HashSet<String>();
                    rels.add(msg.getString("via"));
                }
                Set<JSONVertex> neighbors = getGraph().getOutgoingNeighborsOf(getContext(), rels);
                for(JSONVertex neighbor: neighbors) {
                    String neighborKey = neighbor.getString(Graph.KEY_FIELD);
                    if (visited.has(neighborKey)) {
                        //log(">> already visited " + neighborKey + " at " + visited.getString(neighborKey));
                    } else {
                        emit(neighborKey, "testGraphProcess", new JSONObject(msg.toString()));
                        msgsSent++;
                    }
                }
            }
            
            if (msgsSent == 0) {
                JSONObject visitObj = msg.getJSONObject("visited");
                String[] pathKeys = JSONObject.getNames(visitObj);;
                String[] pathKeys1 = new String[pathKeys.length];
                for(int i=0; i<pathKeys.length; i++) {
                    pathKeys1[i] = visitObj.getString(pathKeys[i]) + "|" + pathKeys[i];
                }
                Arrays.sort(pathKeys1);
                String p = "";
                for(String pk: pathKeys1) {
                    String pk1 = pk.substring(pk.indexOf("|")+1);
                    p += " -> " + pk1;
                }
                log("ENDPOINT: " + p);
            } else {
                //log(getContext().getKey() + " -> " + msgsSent);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            try {
                log(getContext().getString(Graph.KEY_FIELD) + ": suicide :(");
                this.kill();
            } catch (Exception ex1) {
                ex1.printStackTrace();
            }
        }
    }
}