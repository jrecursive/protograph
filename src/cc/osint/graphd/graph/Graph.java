package cc.osint.graphd.graph;

import java.lang.*;
import java.lang.ref.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jgrapht.*;
import org.jgrapht.ext.DOTExporter;
import org.jgrapht.ext.IntegerNameProvider;
import org.jgrapht.ext.StringEdgeNameProvider;
import org.jgrapht.ext.StringNameProvider;
import org.jgrapht.graph.ListenableDirectedGraph;
import org.jgrapht.alg.*;
import org.jgrapht.event.*;
import org.jgrapht.*;
import org.jgrapht.graph.*;
import org.apache.lucene.analysis.*;
import org.apache.lucene.analysis.standard.*;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryParser.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.*;
import org.apache.lucene.util.Version;
import org.json.*;
import org.jetlang.channels.*;
import org.jetlang.core.Callback;
import org.jetlang.core.Disposable;
import org.jetlang.core.Filter;
import org.jetlang.fibers.Fiber;
import org.jetlang.fibers.PoolFiberFactory;
import org.jetlang.fibers.ThreadFiber;
import cc.osint.graphd.sim.*;
import cc.osint.graphd.processes.*;
import cc.osint.graphd.db.*;
import cc.osint.graphd.script.*;

public class Graph 
    implements GraphListener<JSONVertex, JSONEdge>,
               VertexSetListener<JSONVertex>,
               TraversalListener<JSONVertex, JSONEdge> {

    private static final Logger log = Logger.getLogger(
        Graph.class.getName());

    /* graph */
    
    final private String graphName;
    private ListenableDirectedWeightedGraph<JSONVertex, JSONEdge> gr;
    private ConnectivityInspector<JSONVertex, JSONEdge> connectivityInspector;
    final private ConcurrentHashMap<String, JSONVertex> vertices;
    
    /* indexing */
    
    private IndexWriter indexWriter;
    private IndexReader indexReader;
    private Searcher searcher;
    final private RAMDirectory luceneDirectory;
    final private Analyzer analyzer = new KeywordAnalyzer();
    
    /* simulation: process management */

    final private ExecutorService executorService;
    final private PoolFiberFactory fiberFactory;
    final private ProcessGroup<JSONVertex, JSONObject> vertexProcesses;
    final private ProcessGroup<JSONEdge, JSONObject> edgeProcesses;
    final private ProcessGroup<EventObject, JSONObject> graphProcesses;
    
    /* endpoint channel management */
    final private ProcessGroup<String, JSONObject> endpointChannelProcesses;
    
    /* simulation: udf & process registry */
    
    private IndexWriter simIndexWriter;
    private IndexReader simIndexReader;
    private Searcher simSearcher;
    final private RAMDirectory simLuceneDirectory;
    
    /* graph object statics */
    
    final public static String TYPE_FIELD = "_type";
    final public static String KEY_FIELD = "_key";
    final public static String WEIGHT_FIELD = "_weight";
    final public static String EDGE_SOURCE_FIELD = "_source";
    final public static String EDGE_TARGET_FIELD = "_target";
    final public static String RELATION_FIELD = "_rel";
    final public static String DATA_FIELD = "_data";
    final public static String GRAPH_TYPE = "g";
    final public static String VERTEX_TYPE = "v";
    final public static String EDGE_TYPE = "e";
    final public static String PROCESS_TYPE = "p";
    final public static String CHANNEL_TYPE = "c";
    
    /* UDF/process statics */
    
    final public static String UDF_TYPE_JS = "js";
    
    /* graph management */

    public Graph(String graphName) throws Exception {
        this.graphName = graphName;
        gr = new ListenableDirectedWeightedGraph<JSONVertex, JSONEdge>(JSONEdge.class);
        connectivityInspector = new ConnectivityInspector<JSONVertex, JSONEdge>(gr);
        vertices = new ConcurrentHashMap<String, JSONVertex>();
        
        // event handlers
        gr.addVertexSetListener(this);
        gr.addGraphListener(this);
        gr.addVertexSetListener(connectivityInspector);
        gr.addGraphListener(connectivityInspector);
        
        // simulation components
        //executorService = 
        //    Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()-1);
        executorService = Executors.newCachedThreadPool();
        fiberFactory = new PoolFiberFactory(executorService);
        vertexProcesses = 
            new ProcessGroup<JSONVertex, JSONObject>(this, 
                                                     "vertex_processors", 
                                                     executorService,
                                                     fiberFactory);
        edgeProcesses = 
            new ProcessGroup<JSONEdge, JSONObject>(this, 
                                                   "edge_processors", 
                                                   executorService,
                                                   fiberFactory);
        graphProcesses = 
            new ProcessGroup<EventObject, JSONObject>(this, 
                                                      "graph_processors", 
                                                      executorService,
                                                      fiberFactory);
        endpointChannelProcesses = 
            new ProcessGroup<String, JSONObject>(this, 
                                                 "endpoint_channel_processors", 
                                                 executorService,
                                                 fiberFactory);
        
        // graph index
        luceneDirectory = new RAMDirectory();
        indexWriter = new IndexWriter(
            luceneDirectory,
            new StandardAnalyzer(Version.LUCENE_30),
            IndexWriter.MaxFieldLength.LIMITED);
        indexReader = indexWriter.getReader();
        searcher = new IndexSearcher(indexReader);
        
        // process registry
        simLuceneDirectory = new RAMDirectory();
        simIndexWriter = new IndexWriter(
            simLuceneDirectory,
            new StandardAnalyzer(Version.LUCENE_30),
            IndexWriter.MaxFieldLength.LIMITED);
        simIndexReader = simIndexWriter.getReader();
        simSearcher = new IndexSearcher(simIndexReader);

    }
    
    private static String generateKey() throws Exception {
        return UUID.randomUUID().toString();
    }
    
    //
    // UDF management
    //
    
    public void defineUDF(String udfKey,
                          String udfType,
                          String udfFn) throws Exception {
        JSONObject udfDef = new JSONObject();
        udfDef.put("udf_type", udfType);
        udfDef.put("udf_fn", udfFn);
        indexSimObject(udfKey, "udf", udfDef);
    }
    
    public JSONObject getUDFDef(String udfKey) throws Exception {
        return querySimIndex(TYPE_FIELD + ":udf " + 
                             KEY_FIELD + ":" + udfKey).get(0);
    }
    
    //
    // process management
    //
    
    public String startProcess(String key, 
                             String udfKey,
                             String processName) throws Exception {
        JSONObject obj = getGraphObject(key);
        if (obj == null || !obj.has(TYPE_FIELD)) {
            throw new Exception("startProcess: unknown or nonexistent graph object at " +
                                KEY_FIELD + ":" + key);
        }
        String pid = generateKey();
        String _type = obj.getString(TYPE_FIELD);
        JSONObject udfDef = getUDFDef(udfKey);
        String udfType = udfDef.getString("udf_type");
        String udfFn = udfDef.getString("udf_fn");
        
        // instanceName ties the object key (a vertex or edge) to
        //  a specific instance of this process
        String instanceName = key + "-" + processName;
        
        if (_type.equals(VERTEX_TYPE)) {
            JSONVertex jv = getVertex(key);
            
            // vertex-process, explicit (existing) java class
            if (udfType.equals("java")) {
                log.info("reflecting java class: " + udfFn);
                throw new Exception("java reflection unimplemented");
                
            // vertex-process, javascript engine
            } else if (udfType.equals("js") ||
                       udfType.equals("javascript")) {
                log.info("loading javascript source: " + udfFn);
                GScriptEngine scriptEngine = 
                    vertexProcesses.getScriptEngine("rhino", "udfs/js/vm_init.js");
                boolean udfExists = (Boolean) scriptEngine.invoke("_udf_exists", udfKey);
                if (!udfExists) {
                    log.info("loading udf: " + udfFn);
                    scriptEngine.evalScript(udfFn);
                }
                JavascriptProcess<JSONVertex> jsProcess = 
                    new JavascriptProcess<JSONVertex>(pid, scriptEngine);
                scriptEngine.invoke("_udf_instance", udfKey, pid, jsProcess);
                vertexProcesses.start(pid, instanceName, jv, jsProcess);
                
                JSONObject simObj = new JSONObject();
                simObj.put("instance_name", instanceName);
                simObj.put("udf_key", udfKey);
                simObj.put("obj_key", key);
                simObj.put("obj_type", VERTEX_TYPE);
                simObj.put("start_time", System.currentTimeMillis());
                indexSimObject(pid, PROCESS_TYPE, simObj);
                return pid;
                
            } else {
                throw new Exception("unsupported vertex udf type: " + udfType);
            }
        /*
        } else if (_type.equals(EDGE_TYPE)) {
            JSONEdge je = getEdge(key);
            edgeProcesses.start(pid,
                                key + "-" + processName,
                                je,
                                edgeProcessor);
        */
        } else {
            throw new Exception("object type '" + _type + "' not implemented [" + key + "]");
        }
    }
    
    public void emit(String key, String processName, JSONObject msg) 
        throws Exception {
        emitByQuery(TYPE_FIELD + ":" + PROCESS_TYPE +
                    " instance_name:" + key + "-" + processName, 
                    msg);
    }
    
    public void emitByPid(String pid, JSONObject msg) throws Exception {
        emitByQuery(TYPE_FIELD + ":" + PROCESS_TYPE + 
                    " pid:" + pid, 
                    msg);
    }
        
    public void emitByQuery(String query, JSONObject msg)
        throws Exception {
        log.info("emitByQuery: query = " + query);
        List<JSONObject> simObjs = querySimIndex(query);
        for(JSONObject simObj: simObjs) {
            log.info("emitByQuery(" + query + ", ...) -> simObj = " + simObj.toString(4));
            String _type = simObj.getString("obj_type");
            String instanceName = simObj.getString("instance_name");
            
            if (_type.equals(GRAPH_TYPE)) {
                graphProcesses.publish(instanceName, msg);
                
            } else if (_type.equals(VERTEX_TYPE)) {
                vertexProcesses.publish(instanceName, msg);
                
            } else if (_type.equals(EDGE_TYPE)) {
                edgeProcesses.publish(instanceName, msg);
            
            } else if (_type.equals(CHANNEL_TYPE)) {
                endpointChannelProcesses.publish(instanceName, msg);
            
            } else {
                throw new Exception("unknown object type " + 
                                    TYPE_FIELD + ":" + _type);
            }
        }
    }
    
    //
    // endpoint channel management & broadcast
    //
    public String createEndpointChannel(String channelName)
        throws Exception {
        String pid = generateKey();
        EndpointChannelProcess endpointProcess = 
            new EndpointChannelProcess(channelName);
        endpointChannelProcesses.start(pid,
                                       channelName, 
                                       channelName, 
                                       endpointProcess);
        JSONObject simObj = new JSONObject();
        simObj.put("name", channelName);
        simObj.put("start_time", System.currentTimeMillis());
        indexSimObject(pid, CHANNEL_TYPE, simObj);
        return pid;
    }
    
    public Channel<JSONObject> getEndpointChannelObject(String channelName)
        throws Exception {
        return getEndpointChannelProcess(channelName).getChannel();
    }
    
    public EndpointChannelProcess getEndpointChannelProcess(String channelName) 
        throws Exception {
        return (EndpointChannelProcess) endpointChannelProcesses.getProcess(channelName);
    }
    
    public void destroyEndpointChannelByName(String name)
        throws Exception {
        endpointChannelProcesses.kill(name);
    }
    
    public void publishToEndpointByName(String channelName, 
                                        JSONObject msg)
        throws Exception {
        endpointChannelProcesses.publish(channelName, msg);
    }
    
    public void publishToEndpointByQuery(String query,
                                         JSONObject msg)
        throws Exception {
        emitByQuery(query, msg);
    }
    
    public void publishToEndpointByPid(String pid,
                                       JSONObject msg)
        throws Exception {
        emitByQuery(TYPE_FIELD + ":" + CHANNEL_TYPE +
                    " pid:" + pid,
                    msg);
    }
    
    public void subscribeToEndpointByName(String channelName,
                                          Channel<JSONObject> inboundChannel)
        throws Exception {
        getEndpointChannelProcess(channelName).addSubscriber(inboundChannel);
    }
    
    public void unsubscribeToEndpointByName(String channelName,
                                          Channel<JSONObject> inboundChannel)
        throws Exception {
        getEndpointChannelProcess(channelName).removeSubscriber(inboundChannel);
    }
    
    //
    // graph manipulation
    //
    
    public String addVertex(JSONObject jo) throws Exception {
        return addVertex(generateKey(), jo);
    }
    
    public String addVertex(String key, JSONObject jo) throws Exception {
        JSONVertex jv = new JSONVertex(key, jo);
        gr.addVertex(jv);
        vertices.put(key, jv);
        indexObject(key, VERTEX_TYPE, jo);
        return key;
    }
    
    public String addEdge(JSONObject jo, 
        String vKeyFrom, String vKeyTo, String rel, double weight) throws Exception {
        return addEdge(generateKey(), jo, vKeyFrom, vKeyTo, rel, weight);
    }
    
    public String addEdge(String key, JSONObject jo, 
        String vKeyFrom, String vKeyTo, String rel, double weight) throws Exception {
        JSONVertex fromVertex = getVertex(vKeyFrom);
        JSONVertex toVertex   = getVertex(vKeyTo);
        JSONEdge<JSONVertex> je = 
            new JSONEdge<JSONVertex>(fromVertex, toVertex, rel);
        je.put(KEY_FIELD, key);
        je.inherit(jo);
        gr.addEdge(fromVertex, toVertex, je);
        gr.setEdgeWeight(je, weight);
        indexObject(key, EDGE_TYPE, jo);
        return key;
    }
    
    public void indexObject(String key, String type, JSONObject jo) throws Exception {
        Document doc = new Document();
        doc.add(new Field(TYPE_FIELD, type,
            Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS));
        doc.add(new Field(KEY_FIELD, key,
            Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS));

        if (null != jo &&
            null != JSONObject.getNames(jo)) {
            for (String k: JSONObject.getNames(jo)) {
                doc.add(new Field(k, jo.getString(k),
                        Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS));
            }
        }
        
        indexWriter.updateDocument(new Term(KEY_FIELD, key), doc);
        refreshGraphIndex();
    }

    public void indexSimObject(String key, String type, JSONObject jo) throws Exception {
        Document doc = new Document();
        doc.add(new Field(TYPE_FIELD, type,
            Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS));
        doc.add(new Field(KEY_FIELD, key,
            Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS));

        if (null != jo &&
            null != JSONObject.getNames(jo)) {
            for (String k: JSONObject.getNames(jo)) {
                doc.add(new Field(k, jo.getString(k),
                        Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS));
            }
        }
        
        simIndexWriter.updateDocument(new Term(KEY_FIELD, key), doc);
        refreshSimIndex();
    }
    
    public void deleteSimObject(String key, String type) throws Exception {
        deleteSimObjectsByQuery(KEY_FIELD + ":" + key + " " + TYPE_FIELD + ": " + type);
    }
        
        
    public void deleteSimObjectsByQuery(String queryStr) throws Exception {
        QueryParser qp = new QueryParser(Version.LUCENE_30, KEY_FIELD, analyzer);
        qp.setDefaultOperator(org.apache.lucene.queryParser.QueryParser.Operator.AND);
        qp.setAllowLeadingWildcard(true);
        Query query = qp.parse(queryStr);
        simIndexWriter.deleteDocuments(query);
        refreshSimIndex();
    }
    
    private void refreshGraphIndex() throws Exception {
        long t0 = System.currentTimeMillis();
        indexWriter.commit();
        IndexReader newReader = indexReader.reopen();
        if (newReader != indexReader) {
            searcher.close();
            indexReader.close();
            indexReader = newReader;
            searcher = new IndexSearcher(indexReader);
        }
        long elapsed = System.currentTimeMillis() - t0;
        //log.info("refreshGraphIndex: " + elapsed + "ms");
    }

    private void refreshSimIndex() throws Exception {
        long t0 = System.currentTimeMillis();
        simIndexWriter.commit();
        IndexReader newReader = simIndexReader.reopen();
        if (newReader != simIndexReader) {
            simSearcher.close();
            simIndexReader.close();
            simIndexReader = newReader;
            simSearcher = new IndexSearcher(simIndexReader);
        }
        long elapsed = System.currentTimeMillis() - t0;
        //log.info("refreshSimIndex: " + elapsed + "ms");
    }
    
    public List<JSONObject> queryGraphIndex(String queryStr) throws Exception {
        return query(searcher, queryStr);
    }
    
    public List<JSONObject> querySimIndex(String queryStr) throws Exception {
        return query(simSearcher, queryStr);
    }
    
    public List<JSONObject> query(Searcher indexSearcher, String queryStr) throws Exception {
        long start_t = System.currentTimeMillis();
        final List<JSONObject> results = new ArrayList<JSONObject>();
        QueryParser qp = new QueryParser(Version.LUCENE_30, KEY_FIELD, analyzer);
        qp.setDefaultOperator(org.apache.lucene.queryParser.QueryParser.Operator.AND);
        qp.setAllowLeadingWildcard(true);
        Query query = qp.parse(queryStr);
        org.apache.lucene.search.Filter filter = 
            new org.apache.lucene.search.CachingWrapperFilter(new QueryWrapperFilter(query));
        
        indexSearcher.search(
            new MatchAllDocsQuery(),
            filter,
            new Collector() {
                private int docBase;
                IndexReader reader;
                
                // ignore scoring
                public void setScorer(Scorer scorer) { }
                
                // accept docs out of order
                public boolean acceptsDocsOutOfOrder() {
                    return false;
                }
                
                public void collect(int doc) {
                    try {
                        Document d = reader.document(doc);
                        JSONObject result = new JSONObject();
                        for (Fieldable f : d.getFields()) {
                            result.put(f.name(), d.get(f.name()));
                        }
                        results.add(result);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
                
                public void setNextReader(IndexReader reader, int docBase) {
                    this.reader = reader;
                    this.docBase = docBase;
                }   
            });
        long end_t = System.currentTimeMillis();
        //log.info("query: hits.scoreDocs.length = " + results.size() + " (" + (end_t-start_t) + "ms)");
        return results;
    }
    
    public JSONObject getShortestPath(String vFromKey, String vToKey, double radius) throws Exception {
        JSONVertex vFrom = getVertex(vFromKey);
        JSONVertex vTo = getVertex(vToKey);
        List<JSONObject> results = new ArrayList<JSONObject>();
        DijkstraShortestPath dsp = new DijkstraShortestPath(gr, vFrom, vTo, radius);
        GraphPath<JSONVertex, JSONEdge> path = dsp.getPath();
        if (null == path) {
            return null;
        } else {
            if (path.getEdgeList().size() == 0) return null;
            JSONObject result = new JSONObject();
            List<String> edges = new ArrayList<String>();
            for(JSONEdge edge: path.getEdgeList()) {
                edges.add(edge.get(KEY_FIELD));
            }
            result.put("weight", path.getWeight());
            result.put("edges", edges);
            result.put("start_vertex", path.getStartVertex().getString(KEY_FIELD));
            result.put("end_vertex", path.getEndVertex().getString(KEY_FIELD));
            if (radius != Double.POSITIVE_INFINITY) {
                result.put("radius", radius);
            }
            return result;
        }
    }
    
    public boolean exists(String key) throws Exception {
        List<JSONObject> ar = queryGraphIndex(KEY_FIELD + ":\"" + key + "\"");
        return ar.size()>0;
    }
    
    public JSONObject getGraphObject(String key) throws Exception {
        List<JSONObject> ar = queryGraphIndex(KEY_FIELD + ":\"" + key + "\"");
        if (ar.size() == 0) return null;
        return ar.get(0);
    }
    
    public JSONVertex getVertex(String key) throws Exception {
        return vertices.get(key);
    }
    
    public Set<JSONEdge> getOutgoingEdgesOf(String key) throws Exception {
        return getOutgoingEdgesOf(getVertex(key));
    }
    
    public Set<JSONEdge> getOutgoingEdgesOf(JSONVertex vertex) throws Exception {
        return gr.outgoingEdgesOf(vertex);
    }
    
    public Set<JSONVertex> getOutgoingNeighborsOf(JSONVertex vertex) throws Exception {
        Set<String> rels = null;
        return getOutgoingNeighborsOf(vertex, rels);
    }
    
    public Set<JSONVertex> getOutgoingNeighborsOf(JSONVertex vertex, String rel) throws Exception {
        Set<String> rels = new HashSet<String>();
        rels.add(rel);
        return getOutgoingNeighborsOf(vertex, rels);
    }
    
    public Set<JSONVertex> getOutgoingNeighborsOf(JSONVertex vertex, Set<String> rels) throws Exception {
        Set<JSONEdge> edges = getOutgoingEdgesOf(vertex);
        Set<JSONVertex> neighbors = new HashSet<JSONVertex>();
        for(JSONEdge edge: edges) {
            if (rels != null) {
                String edgeRel = edge.get(RELATION_FIELD);
                if (rels.contains(edgeRel)) {
                    neighbors.add(edge.getTarget());
                }
            } else {
                neighbors.add(edge.getTarget());
            }
        }
        return neighbors;
    }
    
    public JSONEdge getEdge(String key) throws Exception {
        JSONObject jsonEdge = getGraphObject(key);
        JSONVertex fromVertex = getVertex(jsonEdge.getString(EDGE_SOURCE_FIELD));
        JSONVertex toVertex = getVertex(jsonEdge.getString(EDGE_TARGET_FIELD));
        return gr.getEdge(fromVertex, toVertex);
    }
    
    public boolean removeEdge(JSONEdge je) throws Exception {
        if (gr.removeEdge(je)) {
            return true;
        }
        return false;
    }
    
    public boolean removeVertex(JSONVertex jv) throws Exception {
        if (gr.removeVertex(jv)) {
            vertices.remove(jv.getString(KEY_FIELD));
            indexWriter.deleteDocuments(new Term(KEY_FIELD, jv.getString(KEY_FIELD)));
            refreshGraphIndex();
            jv = null;
            return true;
        }
        return false;
    }
    
    public void setEdgeWeight(JSONEdge je, double weight) throws Exception {
        gr.setEdgeWeight(je, weight);
    }
    
    public double getEdgeWeight(JSONEdge je) throws Exception {
        return gr.getEdgeWeight(je);
    }
    
    public JSONObject getKShortestPaths(String vFromKey, String vToKey, int k, int maxHops) throws Exception {
        JSONObject result = new JSONObject();
        result.put("k", k);
        KShortestPaths ksp;
        if (maxHops > 0) {
            ksp = new KShortestPaths(gr, getVertex(vFromKey), k, maxHops);
            result.put("max_hops", maxHops);
        } else {
            ksp = new KShortestPaths(gr, getVertex(vFromKey), k);
        }
        List<JSONObject> results = new ArrayList<JSONObject>();
        List<GraphPath<JSONVertex, JSONEdge>> paths = ksp.getPaths(getVertex(vToKey));
        for(GraphPath<JSONVertex, JSONEdge> gp: paths) {
            JSONObject resultObj = new JSONObject();
            JSONArray resultPath = new JSONArray();
            double pathWeight = gp.getWeight();
            resultObj.put("weight", pathWeight);
            List<JSONEdge> path = gp.getEdgeList();
            for(JSONEdge edge: path) {
                resultPath.put(edge.get(KEY_FIELD));
            }
            resultObj.put("path", resultPath);
            results.add(resultObj);
        }
        result.put("start_vertex", vFromKey);
        result.put("end_vertex", vToKey);
        result.put("paths", results);
        return result;
    }
    
    // TODO: efficiency.  right now this copies the entire graph into a new one.
    //       which sucks and is not realistic.  i'd hate to punt on hc/ec though.
    //
    private SimpleWeightedGraph<JSONVertex, JSONEdge> getSimpleWeightedGraph() throws Exception {
        SimpleWeightedGraph<JSONVertex, JSONEdge> swgr = 
            new SimpleWeightedGraph<JSONVertex, JSONEdge>(JSONEdge.class);
        for(String vKey: vertices.keySet()) {
            swgr.addVertex(getVertex(vKey));
        }
        for(JSONEdge je: gr.edgeSet()) {
            double weight = gr.getEdgeWeight(je);
            swgr.addEdge((JSONVertex) je.getSource(), (JSONVertex) je.getTarget(), je);
            swgr.setEdgeWeight(je, weight);
        }
        return swgr;
    }
    
    public List<JSONVertex> getHamiltonianCycle() throws Exception {
        return HamiltonianCycle.getApproximateOptimalForCompleteGraph(getSimpleWeightedGraph());
    }
    
    public List<JSONVertex> getEulerianCircuit() throws Exception {
        return EulerianCircuit.getEulerianCircuitVertices(getSimpleWeightedGraph());
    }
    
    public JSONObject getEKMF(String vSourceKey, String vSinkKey) throws Exception {
        JSONVertex source = getVertex(vSourceKey);
        JSONVertex sink = getVertex(vSinkKey);
        EdmondsKarpMaximumFlow<JSONVertex, JSONEdge> ekmf =
            new EdmondsKarpMaximumFlow<JSONVertex, JSONEdge>(gr);
        ekmf.calculateMaximumFlow(source, sink);
        
        JSONObject result = new JSONObject();
        JSONObject flowResult = new JSONObject();
        Map<JSONEdge, Double> flowMap = ekmf.getMaximumFlow();
        for(JSONEdge edge: flowMap.keySet()) {
            double flowValue = (double) flowMap.get(edge);
            String edgeKey = edge.get(KEY_FIELD);
            flowResult.put(edgeKey, flowValue);
        }
        result.put("flow", flowResult);
        result.put("maximum_flow_value", ekmf.getMaximumFlowValue());
        return result;
    }
    
    public int getChromaticNumber() throws Exception {
        return ChromaticNumber.findGreedyChromaticNumber(getSimpleWeightedGraph());
    }
    
    public JSONObject getKMST() throws Exception {
        JSONObject result = new JSONObject();
        KruskalMinimumSpanningTree<JSONVertex, JSONEdge> kmst = 
            new KruskalMinimumSpanningTree<JSONVertex, JSONEdge>(gr);
        if (null == kmst.getEdgeSet()) {
            return null;
        } else {
            List<String> edges = new ArrayList<String>();
            for(JSONEdge edge: kmst.getEdgeSet()) {
                edges.add(edge.get(KEY_FIELD));
            }
            result.put("edge_set", edges);
            result.put("spanning_tree_cost", kmst.getSpanningTreeCost());
            return result;
        }
    }
    
    public JSONObject getGreedyVertexCover() throws Exception {
        JSONObject result = new JSONObject();
        Set<JSONVertex> verts = VertexCovers.findGreedyCover(Graphs.undirectedGraph(gr));
        if (null == verts) {
            return null;
        } else {
            JSONArray vertKeys = new JSONArray();
            for(JSONVertex v: verts) {
                vertKeys.put(v.getKey());
            }
            result.put("cover_set", vertKeys);
            return result;
        }
    }
    
    public JSONObject get2ApproximationVertexCover() throws Exception {
        JSONObject result = new JSONObject();
        Set<JSONVertex> verts = VertexCovers.find2ApproximationCover(gr);
        if (null == verts) {
            return null;
        } else {
            result.put("cover_set", verts);
            return result;
        }
    }
    
    public JSONObject getConnectedSetByVertex(JSONVertex v) throws Exception {
        Set<JSONVertex> cset = connectivityInspector.connectedSetOf(v);
        if (null == cset) return null;
        JSONObject result = new JSONObject();
        List<String> csetKeys = new ArrayList<String>();
        for(JSONVertex vert: cset) {
            csetKeys.add(vert.getKey());
        }
        result.put("connected_set", csetKeys);
        return result;
    }
    
    public JSONObject getConnectedSets() throws Exception {
        List<Set<JSONVertex>> csets = connectivityInspector.connectedSets();
        if (null == csets) return null;
        List<Set<String>> csetsKeys = new ArrayList<Set<String>>();
        for(Set<JSONVertex> cset: csets) {
            Set<String> csetKeys = new HashSet<String>();
            for(JSONVertex vert: cset) {
                csetKeys.add(vert.getKey());
            }
            csetsKeys.add(csetKeys);
        }
        JSONObject result = new JSONObject();
        result.put("connected_sets", csetsKeys);
        return result;
    }
    
    public boolean isConnected() throws Exception {
        return connectivityInspector.isGraphConnected();
    }
    
    public boolean pathExists(JSONVertex vFrom, JSONVertex vTo) throws Exception {
        return connectivityInspector.pathExists(vFrom, vTo);
    }
    
    public JSONObject getAllMaximalCliques() throws Exception {
        BronKerboschCliqueFinder<JSONVertex, JSONEdge> cf = 
            new BronKerboschCliqueFinder<JSONVertex, JSONEdge>(gr);
        Collection<Set<JSONVertex>> cliques = cf.getAllMaximalCliques();
        JSONObject result = new JSONObject();
        JSONArray cliqueList = new JSONArray();
        for(Set<JSONVertex> cliqueListSet: cliques) {
            JSONArray cliqueSet = new JSONArray();
            for(JSONVertex v: cliqueListSet) {
                cliqueSet.put(v.get(KEY_FIELD));
            }
            cliqueList.put(cliqueSet);
        }
        result.put("cliques", cliqueList);
        return result;
    }

    public JSONObject getBiggestMaximalCliques() throws Exception {
        BronKerboschCliqueFinder<JSONVertex, JSONEdge> cf = 
            new BronKerboschCliqueFinder<JSONVertex, JSONEdge>(gr);
        Collection<Set<JSONVertex>> cliques = cf.getBiggestMaximalCliques();
                JSONObject result = new JSONObject();
        JSONArray cliqueList = new JSONArray();
        for(Set<JSONVertex> cliqueListSet: cliques) {
            JSONArray cliqueSet = new JSONArray();
            for(JSONVertex v: cliqueListSet) {
                cliqueSet.put(v.get(KEY_FIELD));
            }
            cliqueList.put(cliqueSet);
        }
        result.put("cliques", cliqueList);
        return result;
    }
    
    public JSONObject getAllShortestPathsFrom(JSONVertex vFrom) throws Exception {
        FloydWarshallShortestPaths<JSONVertex, JSONEdge> fwsp = 
            new FloydWarshallShortestPaths<JSONVertex, JSONEdge>(gr);
        
        JSONObject result = new JSONObject();
        result.put("diameter", fwsp.getDiameter());
        result.put("shortest_path_count", fwsp.getShortestPathsCount());
        
        if (vFrom == null) return result;
        
        result.put("source_vertex", vFrom.getKey());
        List<JSONObject> resultPaths = new ArrayList<JSONObject>();
        List<GraphPath<JSONVertex, JSONEdge>> paths = fwsp.getShortestPaths(vFrom);
        for(GraphPath<JSONVertex, JSONEdge> path: paths) {
            JSONObject resultPath = new JSONObject();
            List<String> edges = new ArrayList<String>();
            for(JSONEdge edge: path.getEdgeList()) {
                edges.add(edge.get(KEY_FIELD));
            }
            resultPath.put("weight", path.getWeight());
            resultPath.put("edges", edges);
            resultPath.put("start_vertex", path.getStartVertex().getString(KEY_FIELD));
            resultPath.put("end_vertex", path.getEndVertex().getString(KEY_FIELD));
            resultPaths.add(resultPath);
        }
        result.put("paths", resultPaths);
        return result;
    }
    
    public JSONObject getGraphCycles() throws Exception {
        CycleDetector<JSONVertex, JSONEdge> cd = 
            new CycleDetector<JSONVertex, JSONEdge>(gr);
        Set<JSONVertex> cycles = cd.findCycles();
        if (null == cycles) {
            return null;
        } else {
            JSONObject result = new JSONObject();
            List<String> resultCycles = new ArrayList<String>();
            for(JSONVertex v: cycles) {
                resultCycles.add(v.getKey());
            }
            result.put("cycles", resultCycles);
            return result;
        }
    }

    public JSONObject getGraphCyclesContainingVertex(JSONVertex v) throws Exception {
        CycleDetector<JSONVertex, JSONEdge> cd = 
            new CycleDetector<JSONVertex, JSONEdge>(gr);
        Set<JSONVertex> cycles = cd.findCyclesContainingVertex(v);
        if (null == cycles) {
            return null;
        } else {
            JSONObject result = new JSONObject();
            result.put("vertex", v.getKey());
            List<String> resultCycles = new ArrayList<String>();
            for(JSONVertex vert: cycles) {
                resultCycles.add(vert.getKey());
            }
            result.put("cycles", resultCycles);
            return result;
        }
    }
    
    /*
     * STATS
    */
    
    public int numVertices() throws Exception {
        return vertices.size();
    }
    
    public int numEdges() throws Exception {
        return gr.edgeSet().size();
    }
    
    /*
     *
     * EVENT LISTENERS
     *
     *
    */
    
    // VERTEX SET LISTENER
    
    public void vertexAdded(GraphVertexChangeEvent<JSONVertex> e) {
        try {
            // TODO: pub client-event message(s)
            
            //log.info("[event] vertexAdded: " + e.getVertex().get(KEY_FIELD));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    public void vertexRemoved(GraphVertexChangeEvent<JSONVertex> e) {
        try {
            if (e.getType() == GraphVertexChangeEvent.VERTEX_REMOVED) {
                JSONVertex jv = e.getVertex();
                vertices.remove(jv.getString(KEY_FIELD));
                indexWriter.deleteDocuments(new Term(KEY_FIELD, jv.getString(KEY_FIELD)));
                refreshGraphIndex();
                
                // TODO: terminate & de-index process objects for this object
                
                log.info("[event] vertexRemoved: " + jv.get(KEY_FIELD));
            } else {
                final String eventType;
                if (e.getType() == GraphVertexChangeEvent.BEFORE_VERTEX_REMOVED) eventType = "before_vertex_removed";
                else if (e.getType() == GraphVertexChangeEvent.VERTEX_REMOVED) eventType = "vertex_removed";
                else eventType = "vertex:unknown_event_type:" + e.getType();
                log.info("[event] vertexRemoved: " + e.getVertex().get(KEY_FIELD) + " -> " + eventType);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        
        // TODO: pub client-event message(s)
        
    }
    
    // EDGE SET LISTENER
    
    public void edgeAdded(GraphEdgeChangeEvent<JSONVertex, JSONEdge> e) {
        try {
            // TODO: pub client-event message(s)
            
            //log.info("[event] edgeAdded: " + e.getEdge().get(KEY_FIELD));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void edgeRemoved(GraphEdgeChangeEvent<JSONVertex, JSONEdge> e) {
        // http://www.jgrapht.org/javadoc/org/jgrapht/event/GraphEdgeChangeEvent.html
        try {
            // handle implicit deletions as a result of side-effects (e.g., removeVertex, etc.)
            if (e.getType() == GraphEdgeChangeEvent.EDGE_REMOVED) {
                JSONEdge je = e.getEdge();
                indexWriter.deleteDocuments(new Term(KEY_FIELD, je.get(KEY_FIELD)));
                refreshGraphIndex();
                
                // TODO: terminate & de-index process objects for this object
                
                log.info("[event] edgeRemoved: " + je.get(KEY_FIELD));
            } else {
                final String eventType;
                if (e.getType() == GraphEdgeChangeEvent.BEFORE_EDGE_REMOVED) eventType = "before_edge_removed";
                else if (e.getType() == GraphEdgeChangeEvent.EDGE_REMOVED) eventType = "edge_removed";
                else eventType = "edge:unknown_event_type:" + e.getType();
                log.info("[event] edgeRemoved: " + e.getEdge().get(KEY_FIELD) + " -> " + eventType);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        
        // TODO: pub client-event message(s)
        
    }
    
    
    // TRAVERSAL LISTENER
    
    public void connectedComponentFinished(ConnectedComponentTraversalEvent e) {
        log.info("connectedComponentFinished: " + e.toString());
    }
    
    public void connectedComponentStarted(ConnectedComponentTraversalEvent e) {
        log.info("connectedComponentStarted: " + e.toString());
    }
    
    public void edgeTraversed(EdgeTraversalEvent<JSONVertex, JSONEdge> e) {
        log.info("edgeTraversed: " + e.toString());
    }
    
    public void vertexFinished(VertexTraversalEvent<JSONVertex> e) {
        log.info("vertexFinished: " + e.toString());
    }
    
    public void vertexTraversed(VertexTraversalEvent<JSONVertex> e) {
        log.info("vertexTraversed: " + e.toString());
    }
    
    
}





