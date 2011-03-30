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
import org.json.*;
import cc.osint.graphd.graph.*;

@ChannelPipelineCoverage("all")
public class GraphServerHandler extends SimpleChannelUpstreamHandler {

    final private static ConcurrentHashMap<String, Graph> dbs;
    final private static ConcurrentHashMap<String, 
        ConcurrentHashMap<String, String>> clientStateMap;
    
    static {
        dbs = new ConcurrentHashMap<String, Graph>();
        clientStateMap = new ConcurrentHashMap<String, 
            ConcurrentHashMap<String, String>>();
    }
    
    private static final Logger log = Logger.getLogger(
        GraphServerHandler.class.getName());
    
    final private static String CMD_GOODBYE = "bye";        // disconnect
    final private static String CMD_USE = "use";            // select graph to use
    final private static String CMD_CREATE = "create";      // create graph
    final private static String CMD_DROP = "drop";          // delete graph
    final private static String CMD_EXISTS = "exists";      // key exists?
    final private static String CMD_CVERT = "cvert";        // create vertex
    final private static String CMD_CEDGE = "cedge";        // create edge
    final private static String CMD_SET = "set";            // set a property on an edge or vertex
    final private static String CMD_DEL = "del";            // delete object (vertex or edge)
    final private static String CMD_GET = "get";            // get object (vertex or edge)
    final private static String CMD_Q = "q";                // query objects by property
    final private static String CMD_SPATH = "spath";        // shortest path between two vertices
    final private static String CMD_SPY = "spy";            // dump JSONVertex or JSONEdge explicitly
    
    final private static String R_OK = "ok";                // standard reply
    final private static String R_DONE = "done";            // object stream done
    final private static String R_ERR = "err";              // error processing request
    final private static String R_UNK = "unk";              // unknown request
    final private static String R_NOT_IMPL = "not_impl";    // cmd not implemented
    final private static String R_NOT_FOUND = "not_found";  // object not found
    
    final private static String ST_DB = "cur_db";           // clientState: current db (via CMD_USE)
    
    @Override
    public void handleUpstream(
            ChannelHandlerContext ctx, ChannelEvent e) throws Exception {
        if (e instanceof ChannelStateEvent) {
            ChannelState state = ( (ChannelStateEvent) e).getState();
            if (state == state.CONNECTED &&
                ( (ChannelStateEvent)e).getValue() == null) {
                log.info("> DISCONNECTED");
            } else {
                log.info(e.toString());
            }
        }
        super.handleUpstream(ctx, e);
    }

    @Override
    public void channelConnected(
            ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        // Send greeting for a new connection.
        e.getChannel().write(
                "graphd " + InetAddress.getLocalHost().getHostName() + "\n");
    }

    @Override
    public void messageReceived(
        ChannelHandlerContext ctx, MessageEvent e) {
        String clientId = "" + e.getChannel().getId();
        String request = (String) e.getMessage();
        String response;
        boolean close = false;
        
        log.info(clientId + ": " + request);
        if (null == clientStateMap.get(clientId)) {
            clientStateMap.put(clientId,
                new ConcurrentHashMap<String, String>());
        }
        
        if (request.length() == 0) {
            response = R_OK;
        } else if (request.toLowerCase().equals(CMD_GOODBYE)) {
            response = R_OK;
            close = true;
        } else {
            try {
                response = executeRequest(clientId, request);
            } catch (Exception ex) {
                ex.printStackTrace();
                response = R_ERR + " CANNOT_PARSE_REQUEST";
            }
        }
        
        ChannelFuture future = e.getChannel().write(response.trim() + "\n");
        if (close) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
    }
    
    public String executeRequest(String clientId, String request) throws Exception {
        ConcurrentHashMap<String, String> clientState = 
            clientStateMap.get(clientId);
        StringBuffer rsb = new StringBuffer();
        String cmd = request.substring(0, request.indexOf(" "))
            .trim().toLowerCase();
        String[] args = request.substring(request.indexOf(" "))
            .trim().split(" ");
        
        // USE GRAPH: use <db>
        if (cmd.equals(CMD_USE)) {
            if (null == dbs.get(args[0])) {
                rsb.append(R_ERR);
                rsb.append(" DB_NOT_EXIST");
            } else {
                if (null != clientState.get(ST_DB)) clientState.remove(ST_DB);
                clientState.put(ST_DB, args[0]);
                rsb.append(R_OK);
            }
        
        // CREATE GRAPH: create <db> [type]
        } else if (cmd.equals(CMD_CREATE)) {
            if (null != dbs.get(args[0])) {
                rsb.append(R_ERR);
                rsb.append(" DB_ALREADY_EXISTS");
            } else {
                dbs.put(args[0], new Graph());
                rsb.append(R_OK);
            }
        
        // DROP GRAPH: drop <db>
        } else if (cmd.equals(CMD_DROP)) {
            if (null == dbs.get(args[0])) {
                rsb.append(R_ERR);
                rsb.append(" DB_NOT_EXIST");
            } else {
                dbs.remove(args[0]);
                rsb.append(R_OK);
            }
        } else {
            
            // all remaining operations require a db to be selected (via CMD_USE)
            
            if (null == clientState.get(ST_DB)) {
                rsb.append(R_ERR);
                rsb.append(" REQUIRE_USE_DB");
            } else {
                String cldb = clientState.get(ST_DB);
                Graph gr = dbs.get(cldb);
                
                // OBJECT EXISTS: exists <key>
                if (cmd.equals(CMD_EXISTS)) {
                    String key = args[0];
                    log.info("CMD_EXISTS: " + key);
                    
                    rsb.append("" + gr.exists(key));
                    
                // CREATE VERTEX: cvert <key> <json>
                } else if (cmd.equals(CMD_CVERT)) {
                    String key = args[0];
                    String json = request.substring(
                        request.indexOf(" " + key)+(key.length()+1)).trim();
                    log.info("CMD_CVERT: " + key + ": " + json);
                    
                    JSONObject jo = null;
                    try {
                        jo = new JSONObject(json);
                        gr.addVertex(key, jo);
                        rsb.append(R_OK);
                        rsb.append(" CMD_CVERT ");
                        rsb.append(key);
                    } catch (org.json.JSONException jsonEx) {
                        jsonEx.printStackTrace();
                        log.info("------");
                        log.info(json);
                        log.info("------");
                        rsb.append(R_ERR);
                        rsb.append(" BAD_JSON");
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        rsb.append(R_ERR);
                        rsb.append(" ");
                        rsb.append(ex.toString());
                    }
                                        
                // CREATE EDGE: cedge <key> <vFromKey> <vToKey> <rel> <json>
                } else if (cmd.equals(CMD_CEDGE)) {
                    String key = args[0];
                    String vFromKey = args[1];
                    String vToKey = args[2];
                    String rel = args[3];
                    String json = request.substring(
                        request.indexOf(" " + rel)+(rel.length()+1)).trim();
                    log.info("CMD_CEDGE: " + key + ": " +
                        vFromKey + " -> " + vToKey + 
                        " [" + rel + "]");
                    
                    JSONObject jo = null;
                    try {
                        jo = new JSONObject(json);
                        jo.put("_fromVertex", vFromKey);
                        jo.put("_toVertex", vToKey);
                        jo.put("_weight", 1.0);
                        jo.put("_rel", rel);
                        gr.addEdge(key, jo, vFromKey, vToKey, rel);
                        rsb.append(R_OK);
                        rsb.append(" CMD_CEDGE ");
                        rsb.append(key);
                    } catch (org.json.JSONException jsonEx) {
                        jsonEx.printStackTrace();
                        log.info("------");
                        log.info(json);
                        log.info("------");
                        rsb.append(R_ERR);
                        rsb.append(" BAD_JSON");
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        rsb.append(R_ERR);
                        rsb.append(" ");
                        rsb.append(ex.toString());
                    }
                    
                // DELETE OBJECT: del <key>
                } else if (cmd.equals(CMD_DEL)) {
                    String key = args[0];
                    log.info("CMD_DEL: " + key);
                    
                    rsb.append(R_NOT_IMPL);
                    
                // GET OBJECT: get <key>
                } else if (cmd.equals(CMD_GET)) {
                    String key = args[0];
                    log.info("CMD_GET: " + key);
                    JSONObject jo = gr.get(key);
                    if (jo == null) {
                        rsb.append(R_NOT_FOUND);
                    } else {
                        rsb.append(prepareResult(jo));
                        rsb.append("\n");
                        rsb.append(R_DONE);
                    }
                    
                // QUERY OBJECTS: q <query>
                } else if (cmd.equals(CMD_Q)) {
                    String q = request.substring(request.indexOf(" "))
                        .trim();
                    log.info("CMD_Q: " + q);
                    List<JSONObject> results = gr.query(q);
                    for(JSONObject jo: results) {
                        rsb.append(prepareResult(jo));
                        rsb.append("\n");
                    }
                    rsb.append(R_DONE);
                
                // [DJ] SHORTEST PATH: spath <from> <to>
                } else if (cmd.equals(CMD_SPATH)) {
                    String vFromKey = args[0];
                    String vToKey = args[1];
                    log.info("SPATH: " + vFromKey + " -> " + vToKey);
                    List<JSONObject> results = gr.getShortestPath(vFromKey, vToKey);
                    for(JSONObject jo: results) {
                        rsb.append(prepareResult(jo));
                        rsb.append("\n");
                    }
                    rsb.append(R_DONE);
                    
                // set <key> <attr> <value>
                } else if (cmd.equals(CMD_SET)) {
                    String key = args[0];
                    String attr = args[1];
                    String val = args[2];
                    log.info("SET: " + key + "." + attr + " -> " + val);
                    
                    JSONObject obj = gr.get(key);
                    if (null == obj) {
                        rsb.append(R_NOT_FOUND);
                    } else {
                        String _type = obj.getString("_type");
                        if (_type.equals("vertex")) {
                            
                            JSONVertex jv = gr.getVertex(key);
                            jv.put(attr, val);
                            gr.indexObject(key, _type, jv.getJSONObject("data"));
                            rsb.append(R_DONE);
                        } else if (_type.equals("edge")) {
                            
                            JSONEdge je = gr.getEdge(key);
                            je.put(attr, val);
                            gr.indexObject(key, _type, je.asJSONObject().getJSONObject("data"));
                            rsb.append(R_DONE);
                        } else {
                            rsb.append(R_ERR);
                            rsb.append(" UNKNOWN_OBJECT_TYPE");
                        }
                    }                
                } else if (cmd.equals(CMD_SPY)) {
                    String key = args[0];
                    
                    JSONObject obj = gr.get(key);
                    if (null == obj) {
                        rsb.append(R_NOT_FOUND);
                    } else {
                        String _type = obj.getString("_type");
                        if (_type.startsWith("e")) {
                            JSONEdge je = gr.getEdge(key);
                            if (null == je) {
                                rsb.append(R_NOT_FOUND);
                            } else {
                                rsb.append(je.asJSONObject().toString(4) + "\n");
                                rsb.append(R_DONE);
                            }
                        } else if (_type.startsWith("v")) {
                            JSONVertex jv = gr.getVertex(key);
                            if (null == jv) {
                                rsb.append(R_NOT_FOUND);
                            } else {
                                rsb.append(jv.toString(4) + "\n");
                                rsb.append(R_DONE);
                            }
                        } else {
                            rsb.append(R_ERR);
                            rsb.append(" UNKNOWN_OBJECT_TYPE");
                        }
                    }                    
                }
                
                // BASIC PERSISTENCE
                
                // EVENT-SUBSCRIPTION MANAGEMENT
                
                // JAVASCRIPT "POINT-OF-VIEW" PREDICATE
                
                // BF SHORTEST PATH
                
                // CHROMATIC NUMBER
                
                // EK MAX FLOW
                
                // HAM CYCLE
                
                // MINIMAL VERTEX COVER SET
                
                // BK CLIQUE
                
                
            }
        }
                
        // unknown request
        if (rsb.toString().equals("")) {
            log.info("R_UNK: " + cmd);
            rsb.append(R_UNK);
            rsb.append(" ");
            rsb.append(cmd);
        }
                
        rsb.append("\n");
        
        log.info("executeRequest: rsb = " + rsb.toString());
        return rsb.toString();
    }
    
    private String prepareResult(JSONObject jo) throws Exception {
        String s = jo.toString();
        s = s.replaceAll("\n", " ");
        return s;
    }

    @Override
    public void exceptionCaught(
            ChannelHandlerContext ctx, ExceptionEvent e) {
        log.log(
                Level.WARNING,
                "Unexpected exception from downstream.",
                e.getCause());
        e.getChannel().close();
    }
}

