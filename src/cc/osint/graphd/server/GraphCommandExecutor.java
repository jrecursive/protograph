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

package cc.osint.graphd.server;

import java.lang.*;
import java.lang.ref.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.*;
import org.jboss.netty.channel.Channel;
import org.jgrapht.traverse.*;
import cc.osint.graphd.graph.*;
import cc.osint.graphd.processes.InboundChannelProcess;

public class GraphCommandExecutor implements Runnable {
    private static final Logger log = Logger.getLogger(
        GraphCommandExecutor.class.getName());

    final private String graphName;
    final private WeakReference<Graph> graphRef;
    final LinkedBlockingQueue<GraphCommand> graphCommandQueue;
    
    public GraphCommandExecutor(String graphName,
                                WeakReference<Graph> graphRef) {
        this.graphName = graphName;
        this.graphRef = graphRef;
        graphCommandQueue = new LinkedBlockingQueue<GraphCommand>();
        log.info("start: GraphCommandExecutor(" + this.graphName + ")");
    }
    
    public void queue(GraphCommand cmd) throws Exception {
        graphCommandQueue.put(cmd);
    }
    
    public void run() {
        while (true) {
            GraphCommand graphCommand = null;
            String response;
            try {
                graphCommand = graphCommandQueue.take();
                if (graphCommand.poisonPill) {
                    log.info(graphName + ": detected poison pill: terminator processor");
                    return;
                }
                long requestTimeStart = System.currentTimeMillis();
                response = 
                    execute(graphCommand.responseChannel,
                            graphCommand.clientId,
                            graphCommand.clientState,
                            graphCommand.inboundChannelProcess,
                            graphCommand.request,
                            graphCommand.cmd,
                            graphCommand.args);
                long requestTimeElapsed = System.currentTimeMillis() - requestTimeStart;
                if (requestTimeElapsed > 100) {
                    log.info("[" + requestTimeElapsed + "ms]: " + graphCommand.request);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                response = GraphServerProtocol.R_ERR + GraphServerProtocol.SPACE + ex.getMessage();
            }
            (graphCommand.responseChannel).write(response.trim() + GraphServerProtocol.NL);
        }
    }
    
    protected String execute(Channel responseChannel,
                             String clientId, 
                             ConcurrentHashMap<String, String> clientState,
                             InboundChannelProcess inboundChannelProcess,
                             String request, 
                             String cmd, 
                             String[] args) throws Exception {
        
        StringBuffer rsb = new StringBuffer();
        
        // all operations require a db to be selected (via GraphServerProtocol.CMD_USE)
        
        if (null == clientState.get(GraphServerHandler.ST_DB)) {
            rsb.append(GraphServerProtocol.R_ERR);
            rsb.append(" REQUIRE_USE_DB");
            return rsb.toString();
        }
        
        Graph gr = graphRef.get();
        if (null == gr) {
            GraphCommand killCmd = new GraphCommand();
            killCmd.poisonPill = true;
            graphCommandQueue.clear();
            graphCommandQueue.put(killCmd);
            return GraphServerProtocol.R_ERR + GraphServerProtocol.SPACE + " GRAPH_NO_LONGER_EXISTS :queue cleared & poison pill sent";
        }
        
        /*
         * handle dynamic <<query>> expansion, replacement & execution, e.g.
         * 
         *   [...] <<_type:v _key:m*>> [...]
         *
         * TODO: take the responseChannel direct-send hack out of this and think about
         *  how to either queue the commands (& add an "echo-batch-ok" or equiv to the tail to
         *  signify end of the batched command execution) or move this up to the thread loop?
         *
        */
        if (request.indexOf("<<") != -1 &&
            request.indexOf(">>") != -1) {
            String query = request.substring(request.indexOf("<<")+2,
                                             request.indexOf(">>"));
            String prefix = request.substring(0, request.indexOf("<<")).trim();
            String suffix = request.substring(request.indexOf(">>")+2).trim();
            log.info("executing selector: [" + prefix + "] " + query + " [" + suffix + "]:");
            List<JSONObject> selectorResults = gr.queryGraphIndex(query);
            for(JSONObject selectorResult: selectorResults) {
                String batchRequestKey = selectorResult.getString(Graph.KEY_FIELD);
                String batchRequest = 
                    prefix + " " +
                    batchRequestKey + " " +
                    suffix;
                String batchCmd;
                String[] batchArgs;
                
                if (batchRequest.indexOf(GraphServerProtocol.SPACE) != -1) {
                    batchCmd = batchRequest.substring(0, 
                        batchRequest.indexOf(GraphServerProtocol.SPACE)).trim().toLowerCase();
                    batchArgs = batchRequest.substring(
                        batchRequest.indexOf(GraphServerProtocol.SPACE)).trim().split(GraphServerProtocol.SPACE);
                } else {
                    batchCmd = batchRequest.trim().toLowerCase();
                    batchArgs = new String[0];
                }
                    
                log.info("batchRequest: " + batchRequest);
                
                String batchCmdResponse =
                    execute(responseChannel,
                            clientId, 
                            clientState,
                            inboundChannelProcess,
                            batchRequest,
                            batchCmd,
                            batchArgs);
                String responseParts[] = batchCmdResponse.split(GraphServerProtocol.NL);
                for(String responsePart: responseParts) {
                    if (responsePart.charAt(0) != '-') {
                        responseChannel.write(responsePart.trim() + GraphServerProtocol.NL);
                    }
                }
            }
            return GraphServerProtocol.R_BATCH_OK;
        }
        
        /*
         * graph commands
         *
        */
        
        // CREATE VERTEX: cvert <key> <json>
        if (cmd.equals(GraphServerProtocol.CMD_CVERT)) {
            String key = args[0];
            String json = request.substring(request.indexOf(GraphServerProtocol.SPACE + key) +
                (key.length()+1)).trim(); // remainder of line
            JSONObject jo = null;
            try {
                jo = new JSONObject(json);
                gr.addVertex(key, jo);
                rsb.append(GraphServerProtocol.R_OK);
            } catch (org.json.JSONException jsonEx) {
                jsonEx.printStackTrace();
                rsb.append(GraphServerProtocol.R_ERR);
                rsb.append(" BAD_JSON ");
                rsb.append(jsonEx.getMessage());
            } catch (Exception ex) {
                ex.printStackTrace();
                rsb.append(GraphServerProtocol.R_ERR);
                rsb.append(GraphServerProtocol.SPACE);
                rsb.append(ex.getMessage());
            }
                                
        // CREATE EDGE: cedge <key> <vFromKey> <vToKey> <rel> <json>
        // OR:          cedge <key> <vFromKey> <vToKey> <rel> <weight> <json>
        } else if (cmd.equals(GraphServerProtocol.CMD_CEDGE)) {
            String key = args[0];
            String vFromKey = args[1];
            String vToKey = args[2];
            String rel = args[3];
            double weight = 1.0;
            String json;
            
            if (args[4].charAt(0) == '{') {
                json = request.substring(request.indexOf(GraphServerProtocol.SPACE + rel) +
                    (rel.length()+1)).trim(); // remainder of line
            } else {
                weight = Double.parseDouble(args[4]);
                json = request.substring(request.indexOf(GraphServerProtocol.SPACE + args[4]) +
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
                rsb.append(GraphServerProtocol.R_OK);
            } catch (org.json.JSONException jsonEx) {
                jsonEx.printStackTrace();
                rsb.append(GraphServerProtocol.R_ERR);
                rsb.append(" BAD_JSON ");
                rsb.append(jsonEx.getMessage());
            } catch (Exception ex) {
                ex.printStackTrace();
                rsb.append(GraphServerProtocol.R_ERR);
                rsb.append(GraphServerProtocol.SPACE);
                rsb.append(ex.getMessage());
            }
            
        // DELETE OBJECT: del <key>
        } else if (cmd.equals(GraphServerProtocol.CMD_DEL)) {
            String key = args[0];
            JSONObject obj = gr.getGraphObject(key);
            if (null == obj) {
                rsb.append(GraphServerProtocol.R_NOT_FOUND);
            } else {
                String _type = obj.getString(Graph.TYPE_FIELD);
                if (_type.equals(Graph.VERTEX_TYPE)) {
                    JSONVertex jv = gr.getVertex(key);
                    gr.removeVertex(jv);
                    rsb.append(GraphServerProtocol.R_OK);
                    
                } else if (_type.equals(Graph.EDGE_TYPE)) {
                    JSONEdge je = gr.getEdge(key);
                    gr.removeEdge(je);
                    rsb.append(GraphServerProtocol.R_OK);
                
                } else {
                    rsb.append(GraphServerProtocol.R_ERR);
                    rsb.append(GraphServerProtocol.SPACE);
                    rsb.append(GraphServerProtocol.R_UNKNOWN_OBJECT_TYPE);
                }
            }

        // OBJECT EXISTS: exists <key>
        } else if (cmd.equals(GraphServerProtocol.CMD_EXISTS)) {
            String key = args[0];
            rsb.append(gr.exists(key) + " " + key);
            rsb.append(GraphServerProtocol.R_OK);
        
        // GET OBJECT: get <key>
        } else if (cmd.equals(GraphServerProtocol.CMD_GET)) {
            String key = args[0];
            JSONObject jo = gr.getGraphObject(key);
            if (jo == null) {
                rsb.append(GraphServerProtocol.R_NOT_FOUND);
            } else {
                rsb.append(jo);
                rsb.append(GraphServerProtocol.NL);
                rsb.append(GraphServerProtocol.R_OK);
            }
            
        // QUERY GRAPH OBJECTS: q <query>
        } else if (cmd.equals(GraphServerProtocol.CMD_Q)) {
            String q = request.substring(request.indexOf(GraphServerProtocol.SPACE)).trim(); // remainder of line past "q "
            List<JSONObject> results = gr.queryGraphIndex(q);
            JSONArray ja = new JSONArray();
            for(JSONObject jo: results) {
                ja.put(jo);
            }
            JSONObject res = new JSONObject();
            res.put("results", ja);
            
            rsb.append(res);
            rsb.append(GraphServerProtocol.NL);
            rsb.append(GraphServerProtocol.R_OK);
            
        // QUERY PROCESSES: qp <query>
        } else if (cmd.equals(GraphServerProtocol.CMD_QP)) {
            String q = request.substring(request.indexOf(GraphServerProtocol.SPACE)).trim(); // remainder of line past "qp "
            
            /*
            List<JSONObject> results = gr.queryProcessIndex(q);
            JSONArray ja = new JSONArray();
            for(JSONObject jo: results) {
                ja.put(jo);
            }
            JSONObject res = new JSONObject();
            res.put("results", ja);
            
            rsb.append(res);
            rsb.append(GraphServerProtocol.NL);
            rsb.append(GraphServerProtocol.R_OK);
            */
        
        // DIJKSTRA SHORTEST PATH: spath <from> <to> <radius>
        } else if (cmd.equals(GraphServerProtocol.CMD_SPATH)) {
            String vFromKey = args[0];
            String vToKey = args[1];
            double radius = Double.POSITIVE_INFINITY;
            if (args.length == 3) {
                radius = Double.parseDouble(args[2]);
            }
            JSONObject result = gr.getShortestPath(vFromKey, vToKey, radius);
            if (null == result) {
                rsb.append(GraphServerProtocol.R_NOT_EXIST);
            } else {
                rsb.append(result);
                rsb.append(GraphServerProtocol.NL);
                rsb.append(GraphServerProtocol.R_OK);
            }
            
        // SET VERTEX/EDGE ATTRIBUTE: set <key> <attr> <value>
        } else if (cmd.equals(GraphServerProtocol.CMD_SET)) {
            String key = args[0];
            String attr = args[1];
            String val;
            
            if (args.length == 2) {
                // clear value
                val = null;
            } else {
                val = request.substring(request.indexOf(GraphServerProtocol.SPACE + args[1]) +
                    (args[1].length()+1)).trim(); // remainder of line
            }
            
            if (attr.startsWith("_") &&
                !attr.equals(Graph.WEIGHT_FIELD)) {
                rsb.append(GraphServerProtocol.R_ERR);
                rsb.append(" CANNOT_SET_RESERVED_PROPERTY");
            } else {                    
                JSONObject obj = gr.getGraphObject(key);
                if (null == obj) {
                    rsb.append(GraphServerProtocol.R_NOT_FOUND);
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
                        rsb.append(GraphServerProtocol.R_OK);
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
                        rsb.append(GraphServerProtocol.R_OK);
                    } else {
                        rsb.append(GraphServerProtocol.R_ERR);
                        rsb.append(GraphServerProtocol.R_UNKNOWN_OBJECT_TYPE);
                    }
                }
            }
            
        // INCREASE EDGE WEIGHT: incw <edge_key> <amount>
        } else if (cmd.equals(GraphServerProtocol.CMD_INCW)) {
            String key = args[0];
            double w_amt = Double.parseDouble(args[1]);
            JSONEdge je = gr.getEdge(key);
            if (null == je) {
                rsb.append(GraphServerProtocol.R_NOT_FOUND);
            } else {
                double weight = gr.getEdgeWeight(je);
                weight += w_amt;
                gr.setEdgeWeight(je, weight);
                je.put(Graph.WEIGHT_FIELD, "" + weight);
                gr.indexObject(key, Graph.EDGE_TYPE, je.asJSONObject().getJSONObject(Graph.DATA_FIELD));
                rsb.append(GraphServerProtocol.R_OK);
            }

        // DUMP INTERNAL REPRESENTATION OF VERTEX/EDGE: spy <key>
        } else if (cmd.equals(GraphServerProtocol.CMD_SPY)) {
            String key = args[0];
            
            JSONObject obj = gr.getGraphObject(key);
            if (null == obj) {
                rsb.append(GraphServerProtocol.R_NOT_FOUND);
            } else {
                String _type = obj.getString(Graph.TYPE_FIELD);
                if (_type.equals(Graph.EDGE_TYPE)) {
                    JSONEdge je = gr.getEdge(key);
                    if (null == je) {
                        rsb.append(GraphServerProtocol.R_NOT_FOUND);
                    } else {
                        rsb.append(je.asClientJSONObject().toString() + GraphServerProtocol.NL);
                        rsb.append(GraphServerProtocol.R_OK);
                    }
                } else if (_type.equals(Graph.VERTEX_TYPE)) {
                    JSONVertex jv = gr.getVertex(key);
                    if (null == jv) {
                        rsb.append(GraphServerProtocol.R_NOT_FOUND);
                    } else {
                        rsb.append(jv.toString() + GraphServerProtocol.NL);
                        rsb.append(GraphServerProtocol.R_OK);
                    }
                } else {
                    rsb.append(GraphServerProtocol.R_ERR);
                    rsb.append(GraphServerProtocol.R_UNKNOWN_OBJECT_TYPE);
                }
            }                    
        
        // K-SHORTEST-PATHS: kspath <from> <to> <k> <optional:maxHops>
        } else if (cmd.equals(GraphServerProtocol.CMD_KSPATH)) {
            String vFromKey = args[0];
            String vToKey = args[1];
            int k = Integer.parseInt(args[2]);
            int maxHops = 0;
            if (args.length > 3) {
                maxHops = Integer.parseInt(args[3]);
            }
            JSONObject result = gr.getKShortestPaths(vFromKey, vToKey, k, maxHops);
            rsb.append(result);
            rsb.append(GraphServerProtocol.NL);
            rsb.append(GraphServerProtocol.R_OK);
        
        // HAMILTONIAN CYCLE: hc
        } else if (cmd.equals(GraphServerProtocol.CMD_HC)) {
            List<JSONVertex> results = gr.getHamiltonianCycle();
            if (null == results) {
                rsb.append(GraphServerProtocol.R_NOT_EXIST);
            } else {
                JSONObject res = new JSONObject();
                JSONArray cycle = new JSONArray();
                for(JSONVertex jo: results) {
                    cycle.put(jo);
                }
                res.put("cycle", cycle);
                rsb.append(res);
                rsb.append(GraphServerProtocol.NL);
                rsb.append(GraphServerProtocol.R_OK);
            }
        
        // EULERIAN CIRCUIT: ec
        } else if (cmd.equals(GraphServerProtocol.CMD_EC)) {
            List<JSONVertex> results = gr.getEulerianCircuit();
            if (null == results) {
                rsb.append(GraphServerProtocol.R_NOT_EXIST);
            } else {
                JSONObject res = new JSONObject();
                JSONArray circuit = new JSONArray();
                for(JSONVertex jo: results) {
                    circuit.put(jo);
                }
                res.put("circuit", circuit);
                rsb.append(res);
                rsb.append(GraphServerProtocol.NL);
                rsb.append(GraphServerProtocol.R_OK);
            }
        
        // EDWARDS KARP MAXIMUM FLOW: ekmf <from> <to>
        } else if (cmd.equals(GraphServerProtocol.CMD_EKMF)) {
            String vSourceKey = args[0];
            String vSinkKey = args[1];
            JSONObject flowResult = gr.getEKMF(vSourceKey, vSinkKey);
            if (null == flowResult) {
                rsb.append(GraphServerProtocol.R_NOT_EXIST);
            } else {
                rsb.append(flowResult.toString());
                rsb.append(GraphServerProtocol.NL);
                rsb.append(GraphServerProtocol.R_OK);
            }
        
        // CHROMATIC NUMBER: cn
        } else if (cmd.equals(GraphServerProtocol.CMD_CN)) {
            JSONObject result = new JSONObject();
            result.put("chromatic_number", gr.getChromaticNumber());
            rsb.append(result.toString());
            rsb.append(GraphServerProtocol.NL);
            rsb.append(GraphServerProtocol.R_OK);
        
        // KRUSKAL'S MINIMUM SPANNING TREE: kmst
        } else if (cmd.equals(GraphServerProtocol.CMD_KMST)) {
            JSONObject result = gr.getKMST();
            if (null == result) {
                rsb.append(GraphServerProtocol.R_NOT_EXIST);
            } else {
                rsb.append(result.toString());
                rsb.append(GraphServerProtocol.NL);
                rsb.append(GraphServerProtocol.R_OK);
            }
        
        // VERTEX COVER: GREEDY: vcg
        } else if (cmd.equals(GraphServerProtocol.CMD_VCG)) {
            JSONObject result = gr.getGreedyVertexCover();
            if (null == result) {
                rsb.append(GraphServerProtocol.R_NOT_EXIST);
            } else {
                rsb.append(result.toString());
                rsb.append(GraphServerProtocol.NL);
                rsb.append(GraphServerProtocol.R_OK);
            }
        
        // VERTEX COVER: 2-APPROXIMATION: vc2a
        } else if (cmd.equals(GraphServerProtocol.CMD_VC2A)) {
            JSONObject result = gr.get2ApproximationVertexCover();
            if (null == result) {
                rsb.append(GraphServerProtocol.R_NOT_EXIST);
            } else {
                rsb.append(result.toString());
                rsb.append(GraphServerProtocol.NL);
                rsb.append(GraphServerProtocol.R_OK);
            }
        
        // MAXIMALLY CONNECTED SET BY VERTEX: csetv <key>
        } else if (cmd.equals(GraphServerProtocol.CMD_CSETV)) {
            String key = args[0];
            JSONVertex v = gr.getVertex(key);
            if (null == v) {
                rsb.append(GraphServerProtocol.R_NOT_FOUND);
            } else {
                JSONObject result = gr.getConnectedSetByVertex(v);
                rsb.append(result.toString());
                rsb.append(GraphServerProtocol.NL);
                rsb.append(GraphServerProtocol.R_OK);
            }
        
        // ALL MAXIMALLY CONNECTED SETS: csets
        } else if (cmd.equals(GraphServerProtocol.CMD_CSETS)) {
            JSONObject result = gr.getConnectedSets();
            if (null == result) {
                rsb.append(GraphServerProtocol.R_NOT_EXIST);
            } else {
                rsb.append(result.toString());
                rsb.append(GraphServerProtocol.NL);
                rsb.append(GraphServerProtocol.R_OK);
            }
        
        // CONNECTEDNESS TEST: iscon
        } else if (cmd.equals(GraphServerProtocol.CMD_ISCON)) {
            rsb.append("" + gr.isConnected());
            rsb.append(GraphServerProtocol.NL);
            rsb.append(GraphServerProtocol.R_OK);
        
        // UNDIRECTED PATH EXISTENCE TEST: upathex <from> <to>
        } else if (cmd.equals(GraphServerProtocol.CMD_UPATHEX)) {
            JSONVertex vFrom = gr.getVertex(args[0]);
            JSONVertex vTo = gr.getVertex(args[1]);
            if (null == vFrom ||
                null == vTo) {
                rsb.append(GraphServerProtocol.R_NOT_FOUND);
            } else {
                rsb.append("" + gr.pathExists(vFrom, vTo));
                rsb.append(GraphServerProtocol.NL);
                rsb.append(GraphServerProtocol.R_OK);
            }
        
        // FIND ALL MAXIMAL CLIQUES: Bron Kerosch Clique Finder
        } else if (cmd.equals(GraphServerProtocol.CMD_FAMC)) {
            JSONObject result = gr.getAllMaximalCliques();
            if (null == result) {
                rsb.append(GraphServerProtocol.R_NOT_EXIST);
            } else {
                rsb.append(result.toString());
                rsb.append(GraphServerProtocol.NL);
                rsb.append(GraphServerProtocol.R_OK);
            }
            
        // FIND BIGGEST MAXIMAL CLIQUES: Bron Kerosch Clique Finder
        } else if (cmd.equals(GraphServerProtocol.CMD_FBMC)) {
            JSONObject result = gr.getBiggestMaximalCliques();
            if (null == result) {
                rsb.append(GraphServerProtocol.R_NOT_EXIST);
            } else {
                rsb.append(result.toString());
                rsb.append(GraphServerProtocol.NL);
                rsb.append(GraphServerProtocol.R_OK);
            }
            
        } else if (cmd.equals(GraphServerProtocol.CMD_ASPV)) {
            JSONObject result = gr.getAllShortestPathsFrom(
                gr.getVertex(args[0]));
            if (null == result) {
                rsb.append(GraphServerProtocol.R_NOT_EXIST);
            } else {
                rsb.append(result.toString());
                rsb.append(GraphServerProtocol.NL);
                rsb.append(GraphServerProtocol.R_OK);
            }
        
        } else if (cmd.equals(GraphServerProtocol.CMD_GCYC)) {
            JSONObject result = gr.getGraphCycles();
            if (null == result) {
                rsb.append(GraphServerProtocol.R_NOT_EXIST);
            } else {
                rsb.append(result.toString());
                rsb.append(GraphServerProtocol.NL);
                rsb.append(GraphServerProtocol.R_OK);
            }

        } else if (cmd.equals(GraphServerProtocol.CMD_VCYC)) {
            JSONObject result = gr.getGraphCyclesContainingVertex(
                gr.getVertex(args[0]));
            if (null == result) {
                rsb.append(GraphServerProtocol.R_NOT_EXIST);
            } else {
                rsb.append(result.toString());
                rsb.append(GraphServerProtocol.NL);
                rsb.append(GraphServerProtocol.R_OK);
            }
        
        /*
         * TRAVERSAL
        */
        
        // custom traversal code
        // TRAV <start_vertex> <traversal_type> ( - | <udf_key> ) [channel]
        //
        // <traversal_type> values:
        //  breadth_first depth_first closest_first topological
        //
        // the following <traversal_type> may be specified with a suffix
        //  denoting a maximum radius from <start_vertex>:
        //  closest_first[:radius]
        //  
        // if parameter 3 is "-", events will be sent to argument 4 [channel]
        // if parameter 3 is not "-", it will be interpreted as a server-side udf
        //   that implements TraversalEvent functions
        // 
        // NOTE: topological <traversal_type> ignores the value of <start_vertex>
        //       but some value must be specified
        //
        } else if (cmd.equals(GraphServerProtocol.CMD_TRAV)) {
            String startVertex = args[0];
            String traversalType = args[1];
            String udfKey = args[2];
            String channel = null;
            JSONArray pipelineEventPassList = null;
            boolean isPipelined = false;
            if (udfKey.equals("-")) {
                isPipelined = true;
                channel = args[3];
                if (args.length == 5) {
                    String peplStr = 
                        request.substring(
                            request.lastIndexOf(GraphServerProtocol.SPACE + channel) +
                            (channel.length()+1)).trim(); // remainder of line
                    pipelineEventPassList = 
                        new JSONArray(peplStr);
                }
            }
            double radius = 0.0;
            if (traversalType.indexOf(":")!=-1) {
                radius = Double.parseDouble(
                    traversalType.substring(
                        traversalType.indexOf(":")+1));
                traversalType = traversalType.substring(
                    0, traversalType.indexOf(":"));
            }
            
            JSONVertex v = gr.getVertex(startVertex);
            if (null == v) {
                rsb.append(GraphServerProtocol.R_ERR);
                rsb.append(GraphServerProtocol.SPACE);
                rsb.append("startVertex does not exist");
            } else {
                if (isPipelined) {
                    gr.pipelinedTraversal(traversalType, 
                                          v, 
                                          channel, 
                                          radius, 
                                          pipelineEventPassList);
                    rsb.append(GraphServerProtocol.R_OK);
                } else {
                    gr.scriptedTraversal(traversalType, 
                                         v, 
                                         udfKey, 
                                         radius);
                    rsb.append(GraphServerProtocol.R_OK);
                }
            }
            
        /*
         * USER-DEFINED FUNCTIONS
        */
        
        // DEFINE A UDF
        } else if (cmd.equals(GraphServerProtocol.CMD_DEFINE_UDF)) {
            String udfKey = args[0];
            String udfType = args[1];
            String udfURL = args[2];
            gr.defineUDF(udfKey, udfType, udfURL);
            rsb.append(GraphServerProtocol.R_OK);
        
        /*
         * ANONYMOUS BLOCKS
        */
        
        // block_begin <block_key>
        } else if (cmd.equals(GraphServerProtocol.CMD_BLOCK_BEGIN)) {
            throw new Exception("not implemented");
        
        // block_end <block_key>
        } else if (cmd.equals(GraphServerProtocol.CMD_BLOCK_END)) {
            throw new Exception("not implemented");
        
        // block_get <block_key>
        } else if (cmd.equals(GraphServerProtocol.CMD_BLOCK_GET)) {
            throw new Exception("not implemented");

        // block_remove <block_key>
        } else if (cmd.equals(GraphServerProtocol.CMD_BLOCK_REMOVE)) {
            throw new Exception("not implemented");
        
        
        /*
         * SIMULATION
        */
        
        // query simulation index: qsim <query> e.g., _type:udf udf_name:test*
        } else if (cmd.equals(GraphServerProtocol.CMD_QSIM)) {
            String q = request.substring(request.indexOf(GraphServerProtocol.SPACE)).trim(); // remainder of line past "q "
            List<JSONObject> results = gr.querySimIndex(q);
            JSONArray ja = new JSONArray();
            for(JSONObject jo: results) {
                ja.put(jo);
            }
            JSONObject res = new JSONObject();
            res.put("results", ja);
            rsb.append(res);
            rsb.append(GraphServerProtocol.NL);
            rsb.append(GraphServerProtocol.R_OK);
        
        // START A UDF-BACKED PROCESS
        } else if (cmd.equals(GraphServerProtocol.CMD_SPROC)) {
            String objKey = args[0];
            String udfKey = args[1];
            String processName;
            if (args.length == 3) {
                processName = args[2];
            } else {
                processName = objKey + "-" + udfKey;
            }
            gr.startProcess(objKey, udfKey, processName);
            rsb.append(GraphServerProtocol.R_OK);
        
        // EMIT A MESSAGE TO A RUNNING SIMULATION PROCESS: emit <key> <process_name> <json_msg>
        } else if (cmd.equals(GraphServerProtocol.CMD_EMIT)) {
            String key = args[0];
            String processName = args[1];
            String json = request.substring(request.indexOf(GraphServerProtocol.SPACE + processName) +
                (processName.length()+1)).trim(); // remainder of line
            JSONObject jo = null;
            jo = new JSONObject(json);
            gr.emit(key, processName, jo);
            rsb.append(GraphServerProtocol.R_OK);
        
        /*
         * CHANNEL MESSAGING
        */
        
        // create a channel: cchan <channel_name>
        } else if (cmd.equals(GraphServerProtocol.CMD_CCHAN)) {
            String channelName = args[0];
            String pid = gr.createEndpointChannel(channelName);
            if (null == pid) {
                rsb.append(GraphServerProtocol.R_ALREADY_EXIST);
            } else {
                rsb.append(pid);
                rsb.append(GraphServerProtocol.NL);
                rsb.append(GraphServerProtocol.R_OK);
            }
            
        // publish a message to a channel: publish <channel_name> <json_msg>
        } else if (cmd.equals(GraphServerProtocol.CMD_PUBLISH)) {
            String channelName = args[0];
            String json = request.substring(request.indexOf(GraphServerProtocol.SPACE + channelName) +
                (channelName.length()+1)).trim(); // remainder of line
            gr.publishToEndpointByName(channelName, new JSONObject(json));
            rsb.append(GraphServerProtocol.R_OK);
            
        // subscribe to a channel: subscribe <channel_name>
        } else if (cmd.equals(GraphServerProtocol.CMD_SUBSCRIBE)) {
            String channelName = args[0];
            /*
            log.info("channelName = " + channelName);
            log.info("inboundChannelProcess = " + inboundChannelProcess);
            log.info("inboundChannelProcess = " + 
                inboundChannelProcess);
            log.info("inboundChannelProcess.getChannel() = " + 
                inboundChannelProcess.getChannel());
            */
            gr.subscribeToEndpointByName(channelName,
                inboundChannelProcess.getChannel());
            rsb.append(GraphServerProtocol.R_OK);
            
        // unsubscribe from a channel: unsubscribe <channel_name>
        } else if (cmd.equals(GraphServerProtocol.CMD_UNSUBSCRIBE)) {
            String channelName = args[0];
            gr.unsubscribeToEndpointByName(channelName,
                inboundChannelProcess.getChannel());
            rsb.append(GraphServerProtocol.R_OK);
        } else {
            log.info("GraphServerProtocol.R_UNK: " + cmd);
            rsb.append(GraphServerProtocol.R_UNK);
            rsb.append(GraphServerProtocol.SPACE);
            rsb.append(cmd);
        }

        
        return rsb.toString();
    }
}
