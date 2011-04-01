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
    private static final Logger log = Logger.getLogger(
        GraphServerHandler.class.getName());

    /* name -> graph registry */
    final private static ConcurrentHashMap<String, Graph> dbs;

    /* clientId -> client state registry */
    final private static ConcurrentHashMap<String, 
        ConcurrentHashMap<String, String>> clientStateMap;
    
    static {
        dbs = new ConcurrentHashMap<String, Graph>();
        clientStateMap = new ConcurrentHashMap<String, 
            ConcurrentHashMap<String, String>>();
    }
    
    /* protocol commands */
    
    final private static String CMD_GOODBYE = "bye";        // disconnect
    final private static String CMD_USE = "use";            // select graph to use
    final private static String CMD_CREATE = "create";      // create graph
    final private static String CMD_DROP = "drop";          // delete graph
    final private static String CMD_NAMECON = "namecon";    // "name" this connection
    final private static String CMD_CLSTATE = "clstate";    // dump client state (debug)
    final private static String CMD_SSTAT = "sstat";        // dump server status (debug)
    final private static String CMD_GSTAT = "gstat";        // dump graph status (debug)
    final private static String CMD_LISTG = "listg";        // list names of graphs
    
    final private static String CMD_Q = "q";                // query graph objects by property
    final private static String CMD_QP = "qp";              // query processes by property
    final private static String CMD_EXISTS = "exists";      // key exists?
    final private static String CMD_CVERT = "cvert";        // create vertex
    final private static String CMD_CEDGE = "cedge";        // create edge
    final private static String CMD_SET = "set";            // set a property on an edge or vertex
    final private static String CMD_DEL = "del";            // delete object (vertex or edge)
    final private static String CMD_GET = "get";            // get object (vertex or edge)
    final private static String CMD_SPY = "spy";            // dump JSONVertex or JSONEdge explicitly
    final private static String CMD_INCW = "incw";          // increment edge weight
    
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
    final private static String CMD_UPATHEX = "upathex";    // does any UNDIRECTED path exist from v0 -> v1?
    final private static String CMD_FAMC = "famc";          // Bron Kerosch Clique Finder: find all maximal cliques
    final private static String CMD_FBMC = "fbmc";          // Bron Kerosch Clique Finder: find biggest maximal cliques
    
    /* protocol responses */
    
    final private static String R_OK = "-ok";                // standard reply
    final private static String R_DONE = "-done";            // object stream done
    final private static String R_ERR = "-err";              // error processing request
    final private static String R_UNK = "-unk";              // unknown request
    final private static String R_NOT_IMPL = "-not_impl";    // cmd not implemented
    final private static String R_NOT_FOUND = "-not_found";  // object not found
    final private static String R_NOT_EXIST = "-not_exist";  // requested resource does not exist
    final private static String R_UNKNOWN_OBJECT_TYPE = 
                                    " unknown_object_type"; // should theoretically never happen; if a get(key)
                                                            //   returns anything other than edge or vertex
    
    final private static String ST_DB = "cur_db";           // clientState: current db (via CMD_USE)
    final private static String ST_NAMECON = "name_con";    // clientState: connection name (via CMD_USE)
    
    final private static String DEFAULT_CONNECTION_NAME =
                                    "client";               // default connection name
    
    /* protocol misc */
    
    final private static String NL = "\n";
    final private static String SPACE = " ";
    
    /* client id -> netty channel registry */
    
    final private static ConcurrentHashMap<String, Channel> clientIdChannelMap;
    static {
        clientIdChannelMap = new ConcurrentHashMap<String, Channel>();
    }
    
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
        }
        e.getChannel().write(
                "graphd " + InetAddress.getLocalHost().getHostName() + SPACE + clientId + NL);
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
        log.log(Level.WARNING, "Unexpected exception from downstream.", e.getCause());
        e.getChannel().close();
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
                response = R_ERR + SPACE + ex.getMessage();
            }
        }
        
        ChannelFuture future = e.getChannel().write(response.trim() + NL);
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
        
        if (request.indexOf(SPACE) != -1) {
            cmd  = request.substring(0, request.indexOf(SPACE)).trim().toLowerCase();
            args = request.substring(request.indexOf(SPACE)).trim().split(SPACE);
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
            clientState.put(ST_NAMECON, args[0] + "-" + clientId);
            rsb.append(clientState.get(ST_NAMECON));
            rsb.append(NL);
            rsb.append(R_OK);
            
        // DUMP CLIENT STATE: clstate
        } else if (cmd.equals(CMD_CLSTATE)) {
            JSONObject result = new JSONObject(clientState);
            rsb.append(result.toString(4));
            rsb.append(NL);
            rsb.append(R_DONE);
            
        // SERVER STATUS: sstat
        } else if (cmd.equals(CMD_SSTAT)) {
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
            rsb.append(NL);
            rsb.append(R_DONE);
        
        // LIST NAMED GRAPHS
        } else if (cmd.equals(CMD_LISTG)) {
            for(String name: dbs.keySet()) {
                rsb.append(name);
                rsb.append(NL);
            }
            rsb.append(R_DONE);
        
        // GRAPH STATUS: gstat <name>
        } else if (cmd.equals(CMD_GSTAT)) {
            Graph gr0 = dbs.get(args[0]);
            if (null == gr0) {
                rsb.append(R_NOT_EXIST);
            } else {
                JSONObject result = new JSONObject();
                result.put("vertex_count", gr0.numVertices());
                result.put("edge_count", gr0.numEdges());
                rsb.append(result.toString(4));
                rsb.append(NL);
                rsb.append(R_DONE);
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
                    rsb.append("" + gr.exists(key));
                    rsb.append(R_DONE);
                
                // CREATE VERTEX: cvert <key> <json>
                } else if (cmd.equals(CMD_CVERT)) {
                    String key = args[0];
                    String json = request.substring(request.indexOf(SPACE + key)+
                        (key.length()+1)).trim(); // remainder of line
                    JSONObject jo = null;
                    try {
                        jo = new JSONObject(json);
                        gr.addVertex(key, jo);
                        rsb.append(R_DONE);
                    } catch (org.json.JSONException jsonEx) {
                        jsonEx.printStackTrace();
                        rsb.append(R_ERR);
                        rsb.append(" BAD_JSON ");
                        rsb.append(jsonEx.getMessage());
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        rsb.append(R_ERR);
                        rsb.append(SPACE);
                        rsb.append(ex.getMessage());
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
                        json = request.substring(request.indexOf(SPACE + rel) +
                            (rel.length()+1)).trim(); // remainder of line
                    } else {
                        weight = Double.parseDouble(args[4]);
                        json = request.substring(request.indexOf(SPACE + args[4]) +
                            (args[4].length()+1)).trim(); // remainder of line
                    }
                    JSONObject jo = null;
                    try {
                        jo = new JSONObject(json);
                        jo.put(Graph.EDGE_SOURCE_FIELD, vFromKey);
                        jo.put(Graph.EDGE_TARGET_FIELD, vToKey);
                        jo.put(Graph.WEIGHT_FIELD, weight);
                        jo.put(Graph.RELATION_FIELD, rel);
                        gr.addEdge(key, jo, vFromKey, vToKey, rel, weight);
                        rsb.append(R_DONE);
                    } catch (org.json.JSONException jsonEx) {
                        jsonEx.printStackTrace();
                        rsb.append(R_ERR);
                        rsb.append(" BAD_JSON ");
                        rsb.append(jsonEx.getMessage());
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        rsb.append(R_ERR);
                        rsb.append(SPACE);
                        rsb.append(ex.getMessage());
                    }
                    
                // DELETE OBJECT: del <key>
                } else if (cmd.equals(CMD_DEL)) {
                    String key = args[0];
                    JSONObject obj = gr.get(key);
                    if (null == obj) {
                        rsb.append(R_NOT_FOUND);
                    } else {
                        String _type = obj.getString(Graph.TYPE_FIELD);
                        if (_type.equals(Graph.VERTEX_TYPE)) {
                            JSONVertex jv = gr.getVertex(key);
                            gr.removeVertex(jv);
                            rsb.append(R_DONE);
                            
                        } else if (_type.equals(Graph.EDGE_TYPE)) {
                            JSONEdge je = gr.getEdge(key);
                            gr.removeEdge(je);
                            rsb.append(R_DONE);
                        
                        } else {
                            rsb.append(R_ERR);
                            rsb.append(SPACE);
                            rsb.append(R_UNKNOWN_OBJECT_TYPE);
                        }
                    }

                // GET OBJECT: get <key>
                } else if (cmd.equals(CMD_GET)) {
                    String key = args[0];
                    JSONObject jo = gr.get(key);
                    if (jo == null) {
                        rsb.append(R_NOT_FOUND);
                    } else {
                        rsb.append(prepareResult(jo));
                        rsb.append(NL);
                        rsb.append(R_DONE);
                    }
                    
                // QUERY GRAPH OBJECTS: q <query>
                } else if (cmd.equals(CMD_Q)) {
                    String q = request.substring(request.indexOf(SPACE)).trim(); // remainder of line past "q "
                    List<JSONObject> results = gr.queryGraphIndex(q);
                    JSONArray ja = new JSONArray();
                    for(JSONObject jo: results) {
                        ja.put(jo);
                    }
                    JSONObject res = new JSONObject();
                    res.put("results", ja);
                    
                    rsb.append(prepareResult(res));
                    rsb.append(NL);
                    rsb.append(R_DONE);
                    
                // QUERY PROCESSES: qp <query>
                } else if (cmd.equals(CMD_QP)) {
                    String q = request.substring(request.indexOf(SPACE)).trim(); // remainder of line past "qp "
                    List<JSONObject> results = gr.queryProcessIndex(q);
                    JSONArray ja = new JSONArray();
                    for(JSONObject jo: results) {
                        ja.put(jo);
                    }
                    JSONObject res = new JSONObject();
                    res.put("results", ja);
                    
                    rsb.append(prepareResult(res));
                    rsb.append(NL);
                    rsb.append(R_DONE);
                
                // DIJKSTRA SHORTEST PATH: spath <from> <to> <radius>
                } else if (cmd.equals(CMD_SPATH)) {
                    String vFromKey = args[0];
                    String vToKey = args[1];
                    double radius = Double.POSITIVE_INFINITY;
                    if (args.length == 3) {
                        radius = Double.parseDouble(args[2]);
                    }
                    JSONObject result = gr.getShortestPath(vFromKey, vToKey, radius);
                    if (null == result) {
                        rsb.append(R_NOT_EXIST);
                    } else {
                        rsb.append(prepareResult(result));
                        rsb.append(NL);
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
                        val = request.substring(request.indexOf(SPACE + args[1]) +
                            (args[1].length()+1)).trim(); // remainder of line
                    }
                    
                    if (attr.startsWith("_") &&
                        !attr.equals(Graph.WEIGHT_FIELD)) {
                        rsb.append(R_ERR);
                        rsb.append(" CANNOT_SET_RESERVED_PROPERTY");
                    } else {                    
                        JSONObject obj = gr.get(key);
                        if (null == obj) {
                            rsb.append(R_NOT_FOUND);
                        } else {
                            String _type = obj.getString(Graph.TYPE_FIELD);
                            if (_type.equals(Graph.VERTEX_TYPE)) {
                                
                                JSONVertex jv = gr.getVertex(key);
                                if (null != val) {
                                    jv.put(attr, val);
                                } else {
                                    jv.remove(attr);
                                }
                                gr.indexObject(key, _type, jv);
                                rsb.append(R_DONE);
                            } else if (_type.equals(Graph.EDGE_TYPE)) {
                                
                                JSONEdge je = gr.getEdge(key);
                                
                                if (null != val) {
                                    je.put(attr, val);
                                } else {
                                    je.remove(attr);
                                }
                                if (attr.equals(Graph.WEIGHT_FIELD)) {
                                    gr.setEdgeWeight(je, Double.parseDouble(val));
                                }
                                
                                gr.indexObject(key, _type, je.asJSONObject().getJSONObject(Graph.DATA_FIELD));
                                rsb.append(R_DONE);
                            } else {
                                rsb.append(R_ERR);
                                rsb.append(R_UNKNOWN_OBJECT_TYPE);
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
                        je.put(Graph.WEIGHT_FIELD, "" + weight);
                        gr.indexObject(key, Graph.EDGE_TYPE, je.asJSONObject().getJSONObject(Graph.DATA_FIELD));
                        rsb.append(R_DONE);
                    }

                // DUMP INTERNAL REPRESENTATION OF VERTEX/EDGE: spy <key>
                } else if (cmd.equals(CMD_SPY)) {
                    String key = args[0];
                    
                    JSONObject obj = gr.get(key);
                    if (null == obj) {
                        rsb.append(R_NOT_FOUND);
                    } else {
                        String _type = obj.getString(Graph.TYPE_FIELD);
                        if (_type.equals(Graph.EDGE_TYPE)) {
                            JSONEdge je = gr.getEdge(key);
                            if (null == je) {
                                rsb.append(R_NOT_FOUND);
                            } else {
                                rsb.append(je.asClientJSONObject().toString(4) + NL);
                                rsb.append(R_DONE);
                            }
                        } else if (_type.equals(Graph.VERTEX_TYPE)) {
                            JSONVertex jv = gr.getVertex(key);
                            if (null == jv) {
                                rsb.append(R_NOT_FOUND);
                            } else {
                                rsb.append(jv.toString(4) + NL);
                                rsb.append(R_DONE);
                            }
                        } else {
                            rsb.append(R_ERR);
                            rsb.append(R_UNKNOWN_OBJECT_TYPE);
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
                    JSONObject result = gr.getKShortestPaths(vFromKey, vToKey, k, maxHops);
                    rsb.append(prepareResult(result));
                    rsb.append(NL);
                    rsb.append(R_DONE);
                
                // HAMILTONIAN CYCLE: hc
                } else if (cmd.equals(CMD_HC)) {
                    List<JSONVertex> results = gr.getHamiltonianCycle();
                    if (null == results) {
                        rsb.append(R_NOT_EXIST);
                    } else {
                        JSONObject res = new JSONObject();
                        JSONArray cycle = new JSONArray();
                        for(JSONVertex jo: results) {
                            cycle.put(jo);
                        }
                        res.put("cycle", cycle);
                        rsb.append(prepareResult(res));
                        rsb.append(NL);
                        rsb.append(R_DONE);
                    }
                
                // EULERIAN CIRCUIT: ec
                } else if (cmd.equals(CMD_EC)) {
                    List<JSONVertex> results = gr.getEulerianCircuit();
                    if (null == results) {
                        rsb.append(R_NOT_EXIST);
                    } else {
                        JSONObject res = new JSONObject();
                        JSONArray circuit = new JSONArray();
                        for(JSONVertex jo: results) {
                            circuit.put(jo);
                        }
                        res.put("circuit", circuit);
                        rsb.append(prepareResult(res));
                        rsb.append(NL);
                        rsb.append(R_DONE);
                    }
                
                // EDWARDS KARP MAXIMUM FLOW: ekmf <from> <to>
                } else if (cmd.equals(CMD_EKMF)) {
                    String vSourceKey = args[0];
                    String vSinkKey = args[1];
                    JSONObject flowResult = gr.getEKMF(vSourceKey, vSinkKey);
                    if (null == flowResult) {
                        rsb.append(R_NOT_EXIST);
                    } else {
                        rsb.append(flowResult.toString(4));
                        rsb.append(NL);
                        rsb.append(R_DONE);
                    }
                
                // CHROMATIC NUMBER: cn
                } else if (cmd.equals(CMD_CN)) {
                    JSONObject result = new JSONObject();
                    result.put("chromatic_number", gr.getChromaticNumber());
                    rsb.append(result.toString(4));
                    rsb.append(NL);
                    rsb.append(R_DONE);
                
                // KRUSKAL'S MINIMUM SPANNING TREE: kmst
                } else if (cmd.equals(CMD_KMST)) {
                    JSONObject result = gr.getKMST();
                    if (null == result) {
                        rsb.append(R_NOT_EXIST);
                    } else {
                        rsb.append(result.toString(4));
                        rsb.append(NL);
                        rsb.append(R_DONE);
                    }
                
                // VERTEX COVER: GREEDY: vcg
                } else if (cmd.equals(CMD_VCG)) {
                    JSONObject result = gr.getGreedyVertexCover();
                    if (null == result) {
                        rsb.append(R_NOT_EXIST);
                    } else {
                        rsb.append(result.toString(4));
                        rsb.append(NL);
                        rsb.append(R_DONE);
                    }
                
                // VERTEX COVER: 2-APPROXIMATION: vc2a
                } else if (cmd.equals(CMD_VC2A)) {
                    JSONObject result = gr.get2ApproximationVertexCover();
                    if (null == result) {
                        rsb.append(R_NOT_EXIST);
                    } else {
                        rsb.append(result.toString(4));
                        rsb.append(NL);
                        rsb.append(R_DONE);
                    }
                
                // MAXIMALLY CONNECTED SET BY VERTEX: csetv <key>
                } else if (cmd.equals(CMD_CSETV)) {
                    String key = args[0];
                    JSONVertex v = gr.getVertex(key);
                    if (null == v) {
                        rsb.append(R_NOT_FOUND);
                    } else {
                        JSONObject result = gr.getConnectedSetByVertex(v);
                        rsb.append(result.toString(4));
                        rsb.append(NL);
                        rsb.append(R_DONE);
                    }
                
                // ALL MAXIMALLY CONNECTED SETS: csets
                } else if (cmd.equals(CMD_CSETS)) {
                    JSONObject result = gr.getConnectedSets();
                    if (null == result) {
                        rsb.append(R_NOT_EXIST);
                    } else {
                        rsb.append(result.toString(4));
                        rsb.append(NL);
                        rsb.append(R_DONE);
                    }
                
                // CONNECTEDNESS TEST: iscon
                } else if (cmd.equals(CMD_ISCON)) {
                    rsb.append("" + gr.isConnected());
                    rsb.append(NL);
                    rsb.append(R_DONE);
                
                // UNDIRECTED PATH EXISTENCE TEST: upathex <from> <to>
                } else if (cmd.equals(CMD_UPATHEX)) {
                    JSONVertex vFrom = gr.getVertex(args[0]);
                    JSONVertex vTo = gr.getVertex(args[1]);
                    if (null == vFrom ||
                        null == vTo) {
                        rsb.append(R_NOT_FOUND);
                    } else {
                        rsb.append("" + gr.pathExists(vFrom, vTo));
                        rsb.append(NL);
                        rsb.append(R_DONE);
                    }
                
                // FIND ALL MAXIMAL CLIQUES: Bron Kerosch Clique Finder
                } else if (cmd.equals(CMD_FAMC)) {
                    JSONObject result = gr.getAllMaximalCliques();
                    if (null == result) {
                        rsb.append(R_NOT_EXIST);
                    } else {
                        rsb.append(result.toString(4));
                        rsb.append(NL);
                        rsb.append(R_DONE);
                    }
                    
                // FIND BIGGEST MAXIMAL CLIQUES: Bron Kerosch Clique Finder
                } else if (cmd.equals(CMD_FBMC)) {
                    JSONObject result = gr.getBiggestMaximalCliques();
                    if (null == result) {
                        rsb.append(R_NOT_EXIST);
                    } else {
                        rsb.append(result.toString(4));
                        rsb.append(NL);
                        rsb.append(R_DONE);
                    }
                    
                }
                
                // EVENT-SUBSCRIPTION MANAGEMENT
                
                // JAVASCRIPT "POINT-OF-VIEW" TRAVERSAL
                
                // BF SHORTEST PATH
                
                // BK CLIQUE
                
                // ALL PAIRS SHORTEST PATH
            }
        }
                
        // unknown request
        if (rsb.toString().equals("")) {
            log.info("R_UNK: " + cmd);
            rsb.append(R_UNK);
            rsb.append(SPACE);
            rsb.append(cmd);
        }
        rsb.append(NL);
        return rsb.toString();
    }
    
    /* utility methods */
    
    private String prepareResult(JSONObject jo) throws Exception {
        //String s = jo.toString();
        //s = s.replaceAll(NL, SPACE);
        //return s;
        return jo.toString(4);
    }
}
