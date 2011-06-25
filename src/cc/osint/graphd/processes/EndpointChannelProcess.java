/*
 * Copyright 2011 John Muellerleile
 *
 * This file is licensed to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

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

public class EndpointChannelProcess extends GraphProcess<String, JSONObject> {
    final private Set<Channel<JSONObject>> subscribers;
    final private String name;
    
    public EndpointChannelProcess(String name) {
        this.name = name;
        subscribers = new HashSet<Channel<JSONObject>>();
    }
    
    public void addSubscriber(Channel<JSONObject> channel) throws Exception {
        subscribers.add(channel);
    }
    
    public void removeSubscriber(Channel<JSONObject> channel) throws Exception {
        subscribers.remove(channel);
    }
    
    public boolean isSubscribed(Channel<JSONObject> channel) throws Exception {
        return subscribers.contains(channel);
    }
    
    public Set<Channel<JSONObject>> getSubscribers() throws Exception {
        return subscribers;
    }
    
    public void publish(JSONObject msg1) throws Exception {
        JSONObject msg = new JSONObject();
        msg.put("from", name);
        msg.put("msg", msg1);
        for(Channel<JSONObject> channel: subscribers) {
            try {
                channel.publish(msg);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }
    
    public void message(JSONObject msg) {
        try {
            publish(msg);
        } catch (Exception ex) {
            ex.printStackTrace();
            try {
                log(getPid() + ": " + getName() + ": EXCEPTION: " +
                    ex.getMessage());
            } catch (Exception ex1) {
                ex1.printStackTrace();
            }
        }
    }
    
    protected void beforeKill() {
        log(getPid() + ": " + getName() + ": beforeKill");
    }
    
    protected void beforeRemoveVertex(JSONVertex vertex) { }
    protected void beforeRemoveEdge(JSONEdge edge) { }
    protected void afterRemoveEdge(JSONEdge edge) { }

}