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
import org.jboss.netty.channel.Channel;
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
    final private static String CMD_NAMECON = "namecon";    // "name" this connection
    final private static String CMD_CLSTATE = "clstate";    // dump client status (debug)
    
    final private static String CMD_EXISTS = "exists";      // key exists?
    final private static String CMD_CVERT = "cvert";        // create vertex
    final private static String CMD_CEDGE = "cedge";        // create edge
    final private static String CMD_SET = "set";            // set a property on an edge or vertex
    final private static String CMD_DEL = "del";            // delete object (vertex or edge)
    final private static String CMD_GET = "get";            // get object (vertex or edge)
    final private static String CMD_INCW = "incw";          // increment edge weight
    final private static String CMD_Q = "q";                // query objects by property
    final private static String CMD_SPY = "spy";            // dump JSONVertex or JSONEdge explicitly
    final private static String CMD_SPATH = "spath";        // shortest path between two vertices
    final private static String CMD_KSPATH = "kspath";      // k-shortest paths between two vertices (w/ opt. maxHops)
    final private static String CMD_HC = "hc";              // hamiltonian cycle "traveling salesman problem"
    final private static String CMD_EC = "ec";              // eulerian circuit
    final private static String CMD_EKMF = "ekmf";          // edmonds karp maximum flow
    final private static String CMD_CN = "cn";              // chromatic number "graph coloring"
    final private static String CMD_KMST = "kmst";          // compute (kruskal's) minimum spanning tree
    final private static String CMD_VCG = "vcg";            // vertex cover (greedy)
    final private static String CMD_VC2A = "vc2a";          // vertex cover (2 approximation)
    final private static String CMD_CSETV = "csetv";        // maximally connected set of V
    final private static String CMD_CSETS = "csets";        // all maximally connected sets
    final private static String CMD_ISCON = "iscon";        // is graph connected?
    final private static String CMD_UPATHEX = "upathex";    // does _any_ *UNDIRECTED* path exist from v0 -> v1?
    
    final private static String R_OK = "ok";                // standard reply
    final private static String R_DONE = "done";            // object stream done
    final private static String R_ERR = "err";              // error processing request
    final private static String R_UNK = "unk";              // unknown request
    final private static String R_NOT_IMPL = "not_impl";    // cmd not implemented
    final private static String R_NOT_FOUND = "not_found";  // object not found
    final private static String R_NOT_EXIST = "not_exist";  // requested resource does not exist
    
    final private static String ST_DB = "cur_db";           // clientState: current db (via CMD_USE)
    final private static String ST_NAMECON = "name_con";    // clientState: current db (via CMD_USE)
    
    /* test/experimental */
    final private static ConcurrentHashMap<String, Channel> clientIdChannelMap;
    
    static {
        clientIdChannelMap = new ConcurrentHashMap<String, Channel>();
    }
    
    /* netty handlers */
    
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
        log.info("* connect: " + InetAddress.getLocalHost().getHostName());
        String clientId = "" + e.getChannel().getId();
        if (null == clientStateMap.get(clientId)) {
            clientStateMap.put(clientId,
                new ConcurrentHashMap<String, String>());
        }
        e.getChannel().write(
                "graphd " + InetAddress.getLocalHost().getHostName() + " " + clientId + "\n");
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
    
    /* cmd processors */
    
    @Override
    public void messageReceived(
        ChannelHandlerContext ctx, MessageEvent e) {
        String clientId = "" + e.getChannel().getId();
        String request = (String) e.getMessage();
        String response;
        boolean close = false;
        ConcurrentHashMap<String, String> clientState = 
            clientStateMap.get(clientId);
        
        log.info(clientId + ": " + request);
        
        if (request.length() == 0) {
            response = R_OK;
        } else if (request.toLowerCase().equals(CMD_GOODBYE)) {
            response = R_OK;
            close = true;
        } else {
            try {
                response = executeRequest(clientId, clientState, request);
            } catch (Exception ex) {
                ex.printStackTrace();
                response = R_ERR + " " + ex.getMessage();
            }
        }
        
        ChannelFuture future = e.getChannel().write(response.trim() + "\n");
        if (close) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
    }
    
    public String executeRequest(String clientId, 
                                 ConcurrentHashMap<String, String> clientState, 
                                 String request) throws Exception {
        StringBuffer rsb = new StringBuffer();
        String cmd;
        String[] args;
        
        if (request.indexOf(" ") != -1) {
            cmd  = request.substring(0, request.indexOf(" ")).trim().toLowerCase();
            args = request.substring(request.indexOf(" ")).trim().split(" ");
        } else {
            cmd = request;
            args = new String[0];
        }
        
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
            
        // NAME THIS CONNECTION: namecon <name>
        } else if (cmd.equals(CMD_NAMECON)) {
            clientState.put(ST_NAMECON, args[0]);
            rsb.append(R_OK);
            
        // DUMP CLIENT STATE: clstate
        } else if (cmd.equals(CMD_CLSTATE)) {
            JSONObject result = new JSONObject(clientState);
            rsb.append(result.toString(4));
            rsb.append("\n");
            rsb.append(R_DONE);
            
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
                    String json = request.substring(request.indexOf(" " + key)+
                        (key.length()+1)).trim(); // remainder of line
                    
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
                // OR:          cedge <key> <vFromKey> <vToKey> <rel> <weight> <json>
                } else if (cmd.equals(CMD_CEDGE)) {
                    String key = args[0];
                    String vFromKey = args[1];
                    String vToKey = args[2];
                    String rel = args[3];
                    double weight = 1.0;
                    String json;
                    
                    if (args[4].charAt(0) == '{') {
                        json = request.substring(request.indexOf(" " + rel) +
                            (rel.length()+1)).trim(); // remainder of line
                    } else {
                        weight = Double.parseDouble(args[4]);
                        json = request.substring(request.indexOf(" " + args[4]) +
                            (args[4].length()+1)).trim(); // remainder of line
                    }
                    
                    log.info("CMD_CEDGE: " + key + ": " +
                        vFromKey + " -> " + vToKey + 
                        " [" + rel + "] " + weight);
                    
                    JSONObject jo = null;
                    try {
                        jo = new JSONObject(json);
                        jo.put("_fromVertex", vFromKey);
                        jo.put("_toVertex", vToKey);
                        jo.put("_weight", weight);
                        jo.put("_rel", rel);
                        gr.addEdge(key, jo, vFromKey, vToKey, rel, weight);
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
                    
                    JSONObject obj = gr.get(key);
                    if (null == obj) {
                        rsb.append(R_NOT_FOUND);
                    } else {
                        String _type = obj.getString("_type");
                        if (_type.equals("vertex")) {
                            JSONVertex jv = gr.getVertex(key);
                            gr.removeVertex(jv);
                            rsb.append(R_DONE);
                            
                        } else if (_type.equals("edge")) {
                            JSONEdge je = gr.getEdge(key);
                            gr.removeEdge(je);
                            rsb.append(R_DONE);
                        
                        } else {
                            rsb.append(R_ERR);
                            rsb.append(" ");
                            rsb.append(" UNKNOWN_OBJECT_TYPE");
                        }
                    }

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
                    String q = request.substring(request.indexOf(" ")).trim(); // remainder of line past "q "
                    log.info("CMD_Q: " + q);
                    List<JSONObject> results = gr.query(q);
                    for(JSONObject jo: results) {
                        rsb.append(prepareResult(jo));
                        rsb.append("\n");
                    }
                    rsb.append(R_DONE);
                
                // DIJKSTRA SHORTEST PATH: spath <from> <to> <radius>
                } else if (cmd.equals(CMD_SPATH)) {
                    String vFromKey = args[0];
                    String vToKey = args[1];
                    double radius = Double.POSITIVE_INFINITY;
                    if (args.length == 3) {
                        radius = Double.parseDouble(args[2]);
                    }
                    log.info("SPATH: " + vFromKey + " -> " + vToKey + " radius = " + radius);
                    JSONObject result = gr.getShortestPath(vFromKey, vToKey, radius);

                    if (null == result) {
                        rsb.append(R_NOT_EXIST);
                    } else {
                        rsb.append(prepareResult(result));
                        rsb.append("\n");
                        rsb.append(R_DONE);
                    }
                    
                // SET VERTEX/EDGE ATTRIBUTE: set <key> <attr> <value>
                } else if (cmd.equals(CMD_SET)) {
                    String key = args[0];
                    String attr = args[1];
                    String val;
                    
                    if (args.length == 2) {
                        // clear value
                        val = null;
                    } else {
                        val = request.substring(request.indexOf(" " + args[1]) +
                            (args[1].length()+1)).trim(); // remainder of line
                    }
                    
                    log.info("SET: " + key + "." + attr + " -> " + val);
                    
                    if (attr.startsWith("_") &&
                        !attr.equals("_weight")) {
                        rsb.append(R_ERR);
                        rsb.append(" CANNOT_SET_RESERVED_PROPERTY");
                    } else {                    
                        JSONObject obj = gr.get(key);
                        if (null == obj) {
                            rsb.append(R_NOT_FOUND);
                        } else {
                            String _type = obj.getString("_type");
                            if (_type.equals("vertex")) {
                                
                                JSONVertex jv = gr.getVertex(key);
                                if (null != val) {
                                    jv.put(attr, val);
                                } else {
                                    jv.remove(attr);
                                }
                                gr.indexObject(key, _type, jv);
                                rsb.append(R_DONE);
                            } else if (_type.equals("edge")) {
                                
                                JSONEdge je = gr.getEdge(key);
                                
                                if (null != val) {
                                    je.put(attr, val);
                                } else {
                                    je.remove(attr);
                                }
                                if (attr.equals("_weight")) {
                                    gr.setEdgeWeight(je, Double.parseDouble(val));
                                }
                                
                                gr.indexObject(key, _type, je.asJSONObject().getJSONObject("data"));
                                rsb.append(R_DONE);
                            } else {
                                rsb.append(R_ERR);
                                rsb.append(" UNKNOWN_OBJECT_TYPE");
                            }
                        }
                    }
                    
                // INCREASE EDGE WEIGHT: incw <edge_key> <amount>
                } else if (cmd.equals(CMD_INCW)) {
                    String key = args[0];
                    double w_amt = Double.parseDouble(args[1]);
                    JSONEdge je = gr.getEdge(key);
                    if (null == je) {
                        rsb.append(R_NOT_FOUND);
                    } else {
                        double weight = gr.getEdgeWeight(je);
                        weight += w_amt;
                        gr.setEdgeWeight(je, weight);
                        je.put("_weight", "" + weight);
                        gr.indexObject(key, "edge", je.asJSONObject().getJSONObject("data"));
                        rsb.append(R_DONE);
                    }

                // DUMP VERTEX/EDGE: spy <key>
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
                
                // K-SHORTEST-PATHS: kspath <from> <to> <k> <optional:maxHops>
                } else if (cmd.equals(CMD_KSPATH)) {
                    String vFromKey = args[0];
                    String vToKey = args[1];
                    int k = Integer.parseInt(args[2]);
                    int maxHops = 0;
                    if (args.length > 3) {
                        maxHops = Integer.parseInt(args[3]);
                    }
                    log.info("getKShortestPaths: " + vFromKey + " -> " + vToKey);
                    List<JSONObject> results = gr.getKShortestPaths(vFromKey, vToKey, k, maxHops);
                    JSONObject result = new JSONObject();
                    JSONArray paths = new JSONArray();
                    for(JSONObject jo: results) {
                        paths.put(jo);
                    }
                    result.put("paths", paths);
                    rsb.append(prepareResult(result));
                    rsb.append("\n");
                    rsb.append(R_DONE);
                
                // HAMILTONIAN CYCLE: 
                } else if (cmd.equals(CMD_HC)) {
                    List<JSONVertex> results = gr.getHamiltonianCycle();
                    if (null == results) {
                        rsb.append(R_NOT_EXIST);
                    } else {
                        for(JSONVertex jo: results) {
                            rsb.append(prepareResult(jo));
                            rsb.append("\n");
                        }
                        rsb.append(R_DONE);
                    }
                
                // EULERIAN CIRCUIT: 
                } else if (cmd.equals(CMD_EC)) {
                    List<JSONVertex> results = gr.getEulerianCircuit();
                    if (null == results) {
                        rsb.append(R_NOT_EXIST);
                    } else {
                        for(JSONVertex jo: results) {
                            rsb.append(prepareResult(jo));
                            rsb.append("\n");
                        }
                        rsb.append(R_DONE);
                    }
                
                // EDWARDS KARP MAXIMUM FLOW: 
                } else if (cmd.equals(CMD_EKMF)) {
                    String vSourceKey = args[0];
                    String vSinkKey = args[1];
                    JSONObject flowResult = gr.getEKMF(vSourceKey, vSinkKey);
                    if (null == flowResult) {
                        rsb.append(R_NOT_EXIST);
                    } else {
                        rsb.append(flowResult.toString(4));
                        rsb.append("\n");
                        rsb.append(R_DONE);
                    }
                
                // CHROMATIC NUMBER: 
                } else if (cmd.equals(CMD_CN)) {
                    JSONObject result = new JSONObject();
                    result.put("chromatic_number", gr.getChromaticNumber());
                    rsb.append(result.toString(4));
                    rsb.append("\n");
                    rsb.append(R_DONE);
                
                // KRUSKAL'S MINIMUM SPANNING TREE
                } else if (cmd.equals(CMD_KMST)) {
                    JSONObject result = gr.getKMST();
                    if (null == result) {
                        rsb.append(R_NOT_EXIST);
                    } else {
                        rsb.append(result.toString(4));
                        rsb.append("\n");
                        rsb.append(R_DONE);
                    }
                
                // VERTEX COVER: GREEDY
                } else if (cmd.equals(CMD_VCG)) {
                    JSONObject result = gr.getGreedyVertexCover();
                    if (null == result) {
                        rsb.append(R_NOT_EXIST);
                    } else {
                        rsb.append(result.toString(4));
                        rsb.append("\n");
                        rsb.append(R_DONE);
                    }
                
                // VERTEX COVER: 2-APPROXIMATION
                } else if (cmd.equals(CMD_VC2A)) {
                    JSONObject result = gr.get2ApproximationVertexCover();
                    if (null == result) {
                        rsb.append(R_NOT_EXIST);
                    } else {
                        rsb.append(result.toString(4));
                        rsb.append("\n");
                        rsb.append(R_DONE);
                    }
                
                } else if (cmd.equals(CMD_CSETV)) {
                    String key = args[0];
                    JSONVertex v = gr.getVertex(key);
                    if (null == v) {
                        rsb.append(R_NOT_FOUND);
                    } else {
                        JSONObject result = gr.getConnectedSetByVertex(v);
                        rsb.append(result.toString(4));
                        rsb.append("\n");
                        rsb.append(R_DONE);
                    }
                
                } else if (cmd.equals(CMD_CSETS)) {
                    JSONObject result = gr.getConnectedSets();
                    if (null == result) {
                        rsb.append(R_NOT_EXIST);
                    } else {
                        rsb.append(result.toString(4));
                        rsb.append("\n");
                        rsb.append(R_DONE);
                    }
                
                } else if (cmd.equals(CMD_ISCON)) {
                    rsb.append("" + gr.isConnected());
                    rsb.append("\n");
                    rsb.append(R_DONE);
                
                } else if (cmd.equals(CMD_UPATHEX)) {
                    JSONVertex vFrom = gr.getVertex(args[0]);
                    JSONVertex vTo = gr.getVertex(args[1]);
                    if (null == vFrom ||
                        null == vTo) {
                        rsb.append(R_NOT_FOUND);
                    } else {
                        rsb.append("" + gr.pathExists(vFrom, vTo));
                        rsb.append("\n");
                        rsb.append(R_DONE);
                    }
                
                }
                
                // BASIC PERSISTENCE
                
                // EVENT-SUBSCRIPTION MANAGEMENT
                
                // JAVASCRIPT "POINT-OF-VIEW" TRAVERSAL
                
                // BF SHORTEST PATH
                
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
        
        //log.info("executeRequest: rsb = " + rsb.toString());
        return rsb.toString();
    }
    
    /* utility methods */
    
    private String prepareResult(JSONObject jo) throws Exception {
        String s = jo.toString();
        s = s.replaceAll("\n", " ");
        return s;
    }
    
}

