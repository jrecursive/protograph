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
            long time = msg1.getLong("time");
            JSONObject msg = msg1.getJSONObject("msg");
            nettyChannel.write("-message " +
                               from + " " +
                               time + " " + msg.toString() + "\n");
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