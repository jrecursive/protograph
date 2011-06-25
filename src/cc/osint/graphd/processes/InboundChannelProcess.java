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

/*
 * NOTE: This is not a GraphProcess<T, M> -- this is strictly for
 *       routing client-inbound messages to the netty channel for
 *       output
*/
public class InboundChannelProcess implements Callback<JSONObject> {
    final private Fiber fiber;
    final private String clientId;
    final private org.jboss.netty.channel.Channel nettyChannel;
    final private Channel<JSONObject> channel;
    
    public InboundChannelProcess(String clientId,
                                 Fiber fiber, 
                                 Channel channel,
                                 org.jboss.netty.channel.Channel nettyChannel) {
        this.clientId = clientId;
        this.fiber = fiber;
        this.channel = channel;
        this.nettyChannel = nettyChannel;
        log("setup: " + clientId + " fiber = " + 
            fiber + ", channel = " + channel);
    }
    
    public void kill() throws Exception {
        fiber.dispose();
        log("kill");
    }
    
    public void publish(JSONObject msg) throws Exception {
        channel.publish(msg);
    }
    
    public Channel getChannel() {
        return channel;
    }
    
    public org.jboss.netty.channel.Channel getNettyChannel() {
        return nettyChannel;
    }
    
    public void log(String s) {
        try {
            Thread t = Thread.currentThread();
            System.out.println(t.getId() + ": " +
                               clientId + ": InboundChannelProcess: " + s);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    public void onMessage(JSONObject msg1) {
        try {
            String from = msg1.getString("from");
            JSONObject msg = msg1.getJSONObject("msg");
            nettyChannel.write("! " +
                               from + " " +
                               msg.toString() + "\n");
        } catch (Exception ex) {
            ex.printStackTrace();
            try {
                kill();
            } catch (Exception ex1) {
                ex1.printStackTrace();
            }
        }
    }
    
    public String getClientId() {
        return clientId;
    }    
}