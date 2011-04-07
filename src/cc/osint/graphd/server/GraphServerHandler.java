/*
 * Copyright 2010 John Muellerleile
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
package cc.osint.graphd.server;

import java.lang.ref.*;
import java.net.InetAddress;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.*;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.ChannelState;
import org.jboss.netty.channel.Channel;
import org.jetlang.channels.MemoryChannel;
import org.jetlang.fibers.Fiber;
import org.jetlang.fibers.PoolFiberFactory;
import org.json.*;
import cc.osint.graphd.graph.*;
import cc.osint.graphd.processes.*;

@ChannelPipelineCoverage("all")
public class GraphServerHandler extends SimpleChannelUpstreamHandler {
    private static final Logger log = Logger.getLogger(
        GraphServerHandler.class.getName());

    /* name -> graph registry */
    final private static ConcurrentHashMap<String, Graph> nameGraphMap;
    static {
        nameGraphMap = new ConcurrentHashMap<String, Graph>();
    }

    /* name -> GraphCommandExecutor registry */
    final private static 
        ConcurrentHashMap<String, GraphCommandExecutor> graphCommandExecutorMap;
    static {
        graphCommandExecutorMap = 
            new ConcurrentHashMap<String, GraphCommandExecutor>();
    }
    
    /* executors */
    final private static ExecutorService graphCommandExecutorService;
    final private static ExecutorService inboundChannelExecutorService;
    static {
        graphCommandExecutorService = Executors.newCachedThreadPool();
        inboundChannelExecutorService = Executors.newCachedThreadPool();
    }
    
    /* inboundChannel fiberFactory */
    final private static PoolFiberFactory fiberFactory;
    static {
        fiberFactory = new PoolFiberFactory(inboundChannelExecutorService);
    }
    
    /* clientId -> inboundChannel registry */
    final private static ConcurrentHashMap<String, 
        WeakReference<InboundChannelProcess>> inboundChannelMap;
    static {
        inboundChannelMap = new ConcurrentHashMap<String, 
            WeakReference<InboundChannelProcess>>();
    }
    
    /* clientId -> client state registry */
    final private static ConcurrentHashMap<String, 
        ConcurrentHashMap<String, String>> clientStateMap;
    static {
        clientStateMap = new ConcurrentHashMap<String, 
            ConcurrentHashMap<String, String>>();
    }

    /* client id -> netty channel registry */
    final private static ConcurrentHashMap<String, Channel> clientIdChannelMap;
    static {
        clientIdChannelMap = new ConcurrentHashMap<String, Channel>();
    }
        
    /* clientStateMap keys */
    final protected static String ST_DB = "cur_db";           // clientState: current db (via CMD_USE)
    final protected static String ST_NAMECON = "name_con";    // clientState: connection name (via CMD_USE)
    
    /* defaults */
    
    final protected static String DEFAULT_CONNECTION_NAME = "client";   // default connection name
    
    /* netty handlers */
    
    @Override
    public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e) 
        throws Exception {
        if (e instanceof ChannelStateEvent) {
            String clientId = "" + e.getChannel().getId();
            ChannelState state = ((ChannelStateEvent)e).getState();
            if (state == state.CONNECTED &&
                ((ChannelStateEvent)e).getValue() == null) {
                
                // TODO: send client disconnection messages to any related
                //       or interested parties (processes, watched objects, 
                //       watching objects, etc.)
                
                clientStateMap.remove(clientId);
                clientIdChannelMap.remove(clientId);
                InboundChannelProcess inboundChannelProcess = 
                    inboundChannelMap.get(clientId).get();
                if (null != inboundChannelProcess) {
                    inboundChannelProcess.kill();
                    inboundChannelProcess = null;
                }
                inboundChannelMap.remove(clientId);
                
                log.info("DISCONNECTED: " + clientId);
            } else {
                log.info("NETTY: handleUpstream: " + e.toString());
            }
        }
        super.handleUpstream(ctx, e);
    }
    
    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) 
        throws Exception {
        String clientId = "" + e.getChannel().getId();
        if (null == clientStateMap.get(clientId)) {
            ConcurrentHashMap<String, String> clientState = 
                new ConcurrentHashMap<String, String>();
            clientState.put(ST_NAMECON, 
                DEFAULT_CONNECTION_NAME + "-" + clientId);
            clientStateMap.put(clientId, clientState);
            clientIdChannelMap.put(clientId, e.getChannel());
            
            /*
             * start "client fiber & channel" & connect them
            */
            Fiber fiber = fiberFactory.create();
            fiber.start();
            org.jetlang.channels.Channel<JSONObject> inboundChannel = 
                new MemoryChannel<JSONObject>();
            InboundChannelProcess inboundChannelProcess = 
                new InboundChannelProcess(clientId,
                                          fiber,
                                          inboundChannel,       // jetlang channel
                                          e.getChannel());      // netty channel
            inboundChannelMap.put(clientId, 
                new WeakReference<InboundChannelProcess>(inboundChannelProcess));
            inboundChannel.subscribe(fiber, inboundChannelProcess);
        }
        e.getChannel().write(
                "graphd " + 
                    InetAddress.getLocalHost().getHostName() + 
                    GraphServerProtocol.SPACE + 
                    clientId + 
                    GraphServerProtocol.NL);
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
        if (e.getCause() instanceof java.nio.channels.ClosedChannelException) {
            // client disconnected before response to command has been sent back
        } else {
            log.log(Level.WARNING, "Unexpected exception from downstream.", e.getCause());
            e.getChannel().close();
        }
    }
    
    /* message/command processors */
    
    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {
        String clientId = "" + e.getChannel().getId();
        String request = (String) e.getMessage();
        String response;
        boolean close = false;
        ConcurrentHashMap<String, String> clientState = 
            clientStateMap.get(clientId);
        
        //log.info(clientId + ": " + request);
        
        if (request.length() == 0) {
            response = GraphServerProtocol.R_OK;
        } else if (request.toLowerCase().equals(GraphServerProtocol.CMD_GOODBYE)) {
            response = GraphServerProtocol.R_OK;
            close = true;
        } else {
            try {
                response = executeRequest(e.getChannel(), clientId, clientState, request);
                if (null == response) return;
            } catch (Exception ex) {
                ex.printStackTrace();
                response = GraphServerProtocol.R_ERR + GraphServerProtocol.SPACE + ex.getMessage();
            }
        }
        
        ChannelFuture future = e.getChannel().write(response.trim() + GraphServerProtocol.NL);
        if (close) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
    }
    
    public String executeRequest(Channel responseChannel,
                                 String clientId, 
                                 ConcurrentHashMap<String, String> clientState, 
                                 String request) throws Exception {
        StringBuffer rsb = new StringBuffer();
        String cmd;
        String[] args;
        
        if (request.indexOf(GraphServerProtocol.SPACE) != -1) {
            cmd  = request.substring(0, request.indexOf(GraphServerProtocol.SPACE)).trim().toLowerCase();
            args = request.substring(request.indexOf(GraphServerProtocol.SPACE)).trim().split(GraphServerProtocol.SPACE);
        } else {
            cmd = request.trim().toLowerCase();
            args = new String[0];
        }
        
        // USE GRAPH: use <graphName>
        if (cmd.equals(GraphServerProtocol.CMD_USE)) {
            if (null == nameGraphMap.get(args[0])) {
                rsb.append(GraphServerProtocol.R_ERR);
                rsb.append(" DB_NOT_EXIST");
            } else {
                if (null != clientState.get(ST_DB)) clientState.remove(ST_DB);
                clientState.put(ST_DB, args[0]);
                rsb.append(GraphServerProtocol.R_OK);
            }
        
        // CREATE GRAPH: create <graphName>
        } else if (cmd.equals(GraphServerProtocol.CMD_CREATE)) {
            if (null != nameGraphMap.get(args[0])) {
                rsb.append(GraphServerProtocol.R_ERR);
                rsb.append(" DB_ALREADY_EXISTS");
            } else {
                Graph newGraph = new Graph(args[0]);
                nameGraphMap.put(args[0], newGraph);
                WeakReference<Graph> graphRef = 
                    new WeakReference<Graph>(newGraph);
                GraphCommandExecutor graphCommandExecutor = 
                    new GraphCommandExecutor(args[0], 
                                             graphRef, 
                                             inboundChannelMap);
                graphCommandExecutorService.execute(graphCommandExecutor);
                graphCommandExecutorMap.put(args[0], graphCommandExecutor);
                
                rsb.append(GraphServerProtocol.R_OK);
            }
        
        // DROP GRAPH: drop <graphName>
        } else if (cmd.equals(GraphServerProtocol.CMD_DROP)) {
            if (null == nameGraphMap.get(args[0])) {
                rsb.append(GraphServerProtocol.R_ERR);
                rsb.append(" DB_NOT_EXIST");
            } else {
                nameGraphMap.remove(args[0]);
                graphCommandExecutorMap.remove(args[0]);
                // TODO: DROP <KEY>
                // TODO: KILL graphCommandExecutor (via poisonPill message)
                rsb.append(GraphServerProtocol.R_OK);
            }
            
        // NAME THIS CONNECTION: namecon <name>
        } else if (cmd.equals(GraphServerProtocol.CMD_NAMECON)) {
            clientState.put(ST_NAMECON, args[0] + "-" + clientId);
            rsb.append(clientState.get(ST_NAMECON));
            rsb.append(GraphServerProtocol.NL);
            rsb.append(GraphServerProtocol.R_OK);
            
        // DUMP CLIENT STATE: clstate
        } else if (cmd.equals(GraphServerProtocol.CMD_CLSTATE)) {
            JSONObject result = new JSONObject(clientState);
            rsb.append(result.toString(4));
            rsb.append(GraphServerProtocol.NL);
            rsb.append(GraphServerProtocol.R_OK);
            
        // SERVER STATUS: sstat
        } else if (cmd.equals(GraphServerProtocol.CMD_SSTAT)) {
            JSONObject result = new JSONObject();
            JSONObject names = new JSONObject();
            names.put("TYPE_FIELD", Graph.TYPE_FIELD);
            names.put("KEY_FIELD", Graph.KEY_FIELD);
            names.put("WEIGHT_FIELD", Graph.WEIGHT_FIELD);
            names.put("EDGE_SOURCE_FIELD", Graph.EDGE_SOURCE_FIELD);
            names.put("EDGE_TARGET_FIELD", Graph.EDGE_TARGET_FIELD);
            names.put("RELATION_FIELD", Graph.RELATION_FIELD);
            names.put("VERTEX_TYPE", Graph.VERTEX_TYPE);
            names.put("EDGE_TYPE", Graph.EDGE_TYPE);
            result.put("names", names);
            
            rsb.append(result.toString(4));
            rsb.append(GraphServerProtocol.NL);
            rsb.append(GraphServerProtocol.R_OK);
        
        // LIST NAMED GRAPHS
        } else if (cmd.equals(GraphServerProtocol.CMD_LISTG)) {
            for(String name: nameGraphMap.keySet()) {
                rsb.append(name);
                rsb.append(GraphServerProtocol.NL);
            }
            rsb.append(GraphServerProtocol.R_OK);
        
        // GRAPH STATUS: gstat <name>
        } else if (cmd.equals(GraphServerProtocol.CMD_GSTAT)) {
            Graph gr0 = nameGraphMap.get(args[0]);
            if (null == gr0) {
                rsb.append(GraphServerProtocol.R_NOT_EXIST);
            } else {
                JSONObject result = new JSONObject();
                result.put("vertex_count", gr0.numVertices());
                result.put("edge_count", gr0.numEdges());
                rsb.append(result.toString(4));
                rsb.append(GraphServerProtocol.NL);
                rsb.append(GraphServerProtocol.R_OK);
            }
        
        } else {
            
            //
            // graph-specific, queue-driven ordered operations
            //
            
            GraphCommand graphCommand = new GraphCommand();
            graphCommand.responseChannel = responseChannel;
            graphCommand.clientId = clientId;
            graphCommand.clientState = clientState;
            graphCommand.request = request;
            graphCommand.cmd = cmd;
            graphCommand.args = args;
            graphCommand.poisonPill = false;
            
            graphCommandExecutorMap.get(
                clientState.get(GraphServerHandler.ST_DB))
                    .queue(graphCommand);
            
            // a null return value indicates it's been queued for execution
            // by the appropriate GraphCommandExecutor
            return null;
        }
        
        // unknown request
        if (rsb.toString().equals("")) {
            log.info("GraphServerProtocol.R_UNK: " + cmd);
            rsb.append(GraphServerProtocol.R_UNK);
            rsb.append(GraphServerProtocol.SPACE);
            rsb.append(cmd);
        }
        rsb.append(GraphServerProtocol.NL);
        return rsb.toString();
    }
    
}
