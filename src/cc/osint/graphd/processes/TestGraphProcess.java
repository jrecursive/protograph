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
        log("new TestGraphProcess!");
    }
    
    public void onMessage(JSONObject msg) {
        try {
            JSONObject visited;
            if (!msg.has("visited")) {
                visited = new JSONObject();
            } else {
                visited = msg.getJSONObject("visited");
                if (visited.has(getContext().getString(Graph.KEY_FIELD))) {
                    log("i've already seen this message!");
                    return;
                }
            }
            log(getContext().getString(Graph.KEY_FIELD) + ": msg = " + msg);
            
            // spread like wildfire!
            Set<JSONVertex> neighbors = getGraph().getOutgoingNeighborsOf(getContext());
            for(JSONVertex neighbor: neighbors) {
                String neighborKey = neighbor.getString(Graph.KEY_FIELD);
                if (visited.has(neighborKey)) {
                    log(">> already visited " + neighborKey + " at " + visited.getString(neighborKey));
                } else {
                    visited.put(neighborKey, System.nanoTime());
                    msg.put("visited", visited);
                    emit(neighborKey, "testGraphProcess", new JSONObject(msg.toString()));
                }
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