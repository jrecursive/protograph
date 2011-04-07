package cc.osint.graphd.client;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.lang.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;

import cc.osint.graphd.client.handlers.*;
import cc.osint.graphd.server.GraphServerProtocol;

public class ProtographClientHandler extends SimpleChannelUpstreamHandler {
    private static final Logger log = Logger.getLogger(
            ProtographClientHandler.class.getName());
    
    /*
     * TODO:
     * 
     * - deal with disconnect / event / optional reconnect
     *
    */
    
    ProtographClient client = null;
    ProtographClientResultHandler currentResultHandler =
        null;
    ProtographClientEventHandler defaultEventHandler =
        new EchoEventHandler();
    
    public ProtographClientHandler(ProtographClient client) {
        super();
        this.client = client;
    }
    
    protected void setProtographClient(ProtographClient client) {
        this.client = client;
    }
    
    protected void setResultHandler(ProtographClientResultHandler
        currentResultHandler) {
        this.currentResultHandler = currentResultHandler;
    }
    
    protected ProtographClientResultHandler getResultHandler() {
        return this.currentResultHandler;
    }
    
    @Override
    public void handleUpstream(
            ChannelHandlerContext ctx, ChannelEvent e) throws Exception {
        if (e instanceof ChannelStateEvent) {
            log.info(e.toString());
        }
        super.handleUpstream(ctx, e);
    }

    /*
     *
     *
     *
    */
    @Override
    public void messageReceived(
            ChannelHandlerContext ctx, MessageEvent e) {
        String channelId = "" + e.getChannel().getId();
        String request = (String) e.getMessage();
        
        log.info(channelId + ": " + request);
        
        /* charAt(0):
         *  '!' -> event message
         *  '#' -> status/generic message
         *  '-' -> server response (-ok, -err, etc.)
         *
         * everything else counts as a result response (except
         *  for the initial line on connect)
         *
        */
        
        if (request.charAt(0) == '!' ||
            request.charAt(0) == '#') {
            
            log.info("event response: " + request);
            
            ProtographClientEventHandler handler = 
                client.getEventHandler();
            String[] eventParts = request.split(" ");
            boolean isMessageEvent = 
                (eventParts[0] == "!"?true:false);
            String channelName = eventParts[1];
            String message = 
                request.substring(request.indexOf(" " + channelName) +
                (channelName.length()+1)).trim(); // remainder of line
            if (null != handler) {
                if (isMessageEvent) {
                    handler.onMessageEvent(channelName, message);
                } else {
                    handler.onStatusEvent(request.substring(2).trim());
                }
            } else {
                if (isMessageEvent) {
                    defaultEventHandler.onMessageEvent(channelName, message);
                } else {
                    defaultEventHandler.onStatusEvent(request.substring(2).trim());
                }
            }
        } else if (request.charAt(0) == '-') {
            // server response
            log.info("server response: " + request);
            
            if (request.startsWith("-graphd")) {
                log.info("connected: " + request);
            } else {
                /**
                final protected static String R_OK = "-ok";                         // standard reply
                final protected static String R_ERR = "-err";                       // error processing request
                final protected static String R_UNK = "-unk";                       // unknown request
                final protected static String R_NOT_IMPL = "-not_impl";             // cmd not implemented
                final protected static String R_NOT_FOUND = "-not_found";           // object not found
                final protected static String R_NOT_EXIST = "-not_exist";           // requested resource does not exist
                final protected static String R_ALREADY_EXIST = "-already_exist";   // requested resource does not exist
                final protected static String R_UNKNOWN_OBJECT_TYPE = 
                                                " unknown_object_type";             // should theoretically never happen; if a get(key)
                                                                                    //   returns anything other than edge or vertex
                final protected static String R_BATCH_OK = "-batch-ok";             // response at the end of a selector-driven batch of responses
                **/

                if (request.startsWith(GraphServerProtocol.R_OK) ||
                    request.startsWith(GraphServerProtocol.R_BATCH_OK) ) {
                    currentResultHandler.onComplete();
                    this.currentResultHandler = null;
                } else {
                    currentResultHandler.onError(request);
                    currentResultHandler.onComplete();
                    this.currentResultHandler = null;
                }
            }
                        
        } else {
            // result response
            log.info("result response: " + request);
            currentResultHandler.onResult(request);
        }
    }

    @Override
    public void exceptionCaught(
            ChannelHandlerContext ctx, ExceptionEvent e) {
        log.log(Level.WARNING,
                "Unexpected exception from downstream.",
                e.getCause());
        e.getChannel().close();
    }
}
