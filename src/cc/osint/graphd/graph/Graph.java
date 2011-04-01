package cc.osint.graphd.graph;

import java.lang.*;
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
import cc.osint.graphd.graph.*;

import org.jetlang.channels.*;
import org.jetlang.core.Callback;
import org.jetlang.core.Disposable;
import org.jetlang.core.Filter;
import org.jetlang.fibers.Fiber;
import org.jetlang.fibers.PoolFiberFactory;
import org.jetlang.fibers.ThreadFiber;

public class Graph 
    implements GraphListener<JSONVertex, JSONEdge>,
               VertexSetListener<JSONVertex>,
               TraversalListener<JSONVertex, JSONEdge> {

    private static final Logger log = Logger.getLogger(
        Graph.class.getName());

    /* graph */
    
    private ListenableDirectedWeightedGraph<JSONVertex, JSONEdge> gr;
    private ConnectivityInspector<JSONVertex, JSONEdge> connectivityInspector;
    private ConcurrentHashMap<String, JSONVertex> vertices;
    
    /* indexing */
    
    private IndexWriter indexWriter;
    private IndexReader indexReader;
    private Searcher searcher;
    final private RAMDirectory luceneDirectory;
    final private Analyzer analyzer = new WhitespaceAnalyzer();
    
    /* simulation: process management */

    ExecutorService executorService = Executors.newCachedThreadPool();
    PoolFiberFactory poolFiberFactory = new PoolFiberFactory(executorService);
    Channel<JSONObject> globalChannel = new MemoryChannel<JSONObject>();
    Channel<JSONObject> eventChannel = new MemoryChannel<JSONObject>();
    
    /* simulation: process registry */
    
    private IndexWriter p_indexWriter;
    private IndexReader p_indexReader;
    private Searcher p_searcher;
    final private RAMDirectory p_luceneDirectory;

    /* statics */
    
    final public static String TYPE_FIELD = "_type";
    final public static String KEY_FIELD = "_key";
    final public static String WEIGHT_FIELD = "_weight";
    final public static String EDGE_FROM_FIELD = "_source";
    final public static String EDGE_TO_FIELD = "_target";
    final public static String RELATION_FIELD = "_rel";
    final public static String DATA_FIELD = "_data";
    final public static String VERTEX_TYPE = "v";
    final public static String EDGE_TYPE = "e";

    public Graph() throws Exception {
        gr = new ListenableDirectedWeightedGraph<JSONVertex, JSONEdge>(JSONEdge.class);
        connectivityInspector = new ConnectivityInspector<JSONVertex, JSONEdge>(gr);
        vertices = new ConcurrentHashMap<String, JSONVertex>();
        
        luceneDirectory = new RAMDirectory();
        indexWriter = new IndexWriter(
            luceneDirectory,
            new StandardAnalyzer(Version.LUCENE_30),
            IndexWriter.MaxFieldLength.LIMITED);
        indexReader = indexWriter.getReader();
        searcher = new IndexSearcher(indexReader);
        
        p_luceneDirectory = new RAMDirectory();
        p_indexWriter = new IndexWriter(
            p_luceneDirectory,
            new StandardAnalyzer(Version.LUCENE_30),
            IndexWriter.MaxFieldLength.LIMITED);
        p_indexReader = p_indexWriter.getReader();
        p_searcher = new IndexSearcher(p_indexReader);
        
        gr.addVertexSetListener(this);
        gr.addGraphListener(this);
        gr.addVertexSetListener(connectivityInspector);
        gr.addGraphListener(connectivityInspector);
    }
    
    private static String generateKey() throws Exception {
        return UUID.randomUUID().toString();
    }
    
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
                        Field.Store.YES, Field.Index.ANALYZED_NO_NORMS));
            }
        }
        
        indexWriter.updateDocument(new Term(KEY_FIELD, key), doc);
        refreshIndex();
    }
    
    private void refreshIndex() throws Exception {
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
        log.info("refreshIndex(): " + elapsed + "ms");
    }
    
    private void p_refreshIndex() throws Exception {
        long t0 = System.currentTimeMillis();
        p_indexWriter.commit();
        IndexReader p_newReader = p_indexReader.reopen();
        if (p_newReader != p_indexReader) {
            p_searcher.close();
            p_indexReader.close();
            p_indexReader = p_newReader;
            p_searcher = new IndexSearcher(p_indexReader);
        }
        long elapsed = System.currentTimeMillis() - t0;
        log.info("p_refreshIndex(): " + elapsed + "ms");
    }
    
    public List<JSONObject> queryGraphIndex(String queryStr) throws Exception {
        return query(searcher, queryStr);
    }
    
    public List<JSONObject> queryProcessIndex(String queryStr) throws Exception {
        return query(p_searcher, queryStr);
    }
    
    public List<JSONObject> query(Searcher indexSearcher, String queryStr) throws Exception {
        long start_t = System.currentTimeMillis();
        final List<JSONObject> results = new ArrayList<JSONObject>();
        QueryParser qp = new QueryParser(Version.LUCENE_30, KEY_FIELD, analyzer);
        qp.setAllowLeadingWildcard(true);
        Query query = qp.parse(queryStr);
        log.info("query = " + query.toString());
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
        log.info("query: hits.scoreDocs.length = " + results.size() + " (" + (end_t-start_t) + "ms)");
        return results;
    }
    
    public JSONObject getShortestPath(String vFromKey, String vToKey, double radius) throws Exception {
        JSONVertex vFrom = getVertex(vFromKey);
        JSONVertex vTo = getVertex(vToKey);
        log.info("vFrom = " + vFrom.toString());
        log.info("vTo = " + vTo.toString());
        List<JSONObject> results = new ArrayList<JSONObject>();
        DijkstraShortestPath dsp = new DijkstraShortestPath(gr, vFrom, vTo, radius);
        GraphPath<JSONVertex, JSONEdge> path = dsp.getPath();
        if (null == path) {
            return null;
        } else {
            JSONObject result = new JSONObject();
            List<JSONObject> edges = new ArrayList<JSONObject>();
            for(JSONEdge edge: path.getEdgeList()) {
                edges.add(edge.asJSONObject());
            }
            result.put("weight", path.getWeight());
            result.put("edges", edges);
            result.put("start_vertex", path.getStartVertex());
            result.put("end_vertex", path.getEndVertex());
            return result;
        }
    }
    
    public boolean exists(String key) throws Exception {
        List<JSONObject> ar = queryGraphIndex(KEY_FIELD + ":\"" + key + "\"");
        return ar.size()>0;
    }
    
    public JSONObject get(String key) throws Exception {
        List<JSONObject> ar = queryGraphIndex(KEY_FIELD + ":\"" + key + "\"");
        if (ar.size() == 0) return null;
        return ar.get(0);
    }
    
    public JSONVertex getVertex(String key) throws Exception {
        return vertices.get(key);
    }
    
    public JSONEdge getEdge(String key) throws Exception {
        JSONObject jsonEdge = get(key);
        JSONVertex fromVertex = getVertex(jsonEdge.getString(EDGE_FROM_FIELD));
        JSONVertex toVertex = getVertex(jsonEdge.getString(EDGE_TO_FIELD));
        return gr.getEdge(fromVertex, toVertex);
    }
    
    public void removeEdge(JSONEdge je) throws Exception {
        if (gr.removeEdge(je)) {
            log.info("removeEdge(" + je.get(KEY_FIELD) + "): ok");
        }
    }
    
    public void removeVertex(JSONVertex jv) throws Exception {
        if (gr.removeVertex(jv)) {
            vertices.remove(jv.getString(KEY_FIELD));
            indexWriter.deleteDocuments(new Term(KEY_FIELD, jv.getString(KEY_FIELD)));
            refreshIndex();
            log.info("removeVertex(" + jv.get(KEY_FIELD) + ")");
            jv = null;
        }
    }
    
    public void setEdgeWeight(JSONEdge je, double weight) throws Exception {
        gr.setEdgeWeight(je, weight);
    }
    
    public double getEdgeWeight(JSONEdge je) throws Exception {
        return gr.getEdgeWeight(je);
    }
    
    public List<JSONObject> getKShortestPaths(String vFromKey, String vToKey, int n, int maxHops) throws Exception {
        KShortestPaths ksp;
        
        if (maxHops > 0) {
            ksp = new KShortestPaths(gr, getVertex(vFromKey), n, maxHops);
        } else {
            ksp = new KShortestPaths(gr, getVertex(vFromKey), n);
        }
        
        List<JSONObject> results = new ArrayList<JSONObject>();
        List<GraphPath<JSONVertex, JSONEdge>> paths = ksp.getPaths(getVertex(vToKey));
        for(GraphPath<JSONVertex, JSONEdge> gp: paths) {
            JSONObject result = new JSONObject();
            JSONArray resultPath = new JSONArray();
            double pathWeight = gp.getWeight();
            result.put("weight", pathWeight);
            List<JSONEdge> path = gp.getEdgeList();
            for(JSONEdge edge: path) {
                resultPath.put(edge.asJSONObject());
            }
            result.put("path", resultPath);
            results.add(result);
        }
        
        log.info("paths = " + paths.toString());
        return results;
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
            swgr.addEdge((JSONVertex) je.getV1(), (JSONVertex) je.getV2(), je);
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
        result.put("maximum_flow", ekmf.getMaximumFlow());
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
            List<JSONObject> edges = new ArrayList<JSONObject>();
            for(JSONEdge edge: kmst.getEdgeSet()) {
                edges.add(edge.asJSONObject());
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
            result.put("cover_set", verts);
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
        result.put("connected_set", cset);
        return result;
    }
    
    public JSONObject getConnectedSets() throws Exception {
        List<Set<JSONVertex>> csets = connectivityInspector.connectedSets();
        if (null == csets) return null;
        JSONObject result = new JSONObject();
        result.put("connected_sets", csets);
        return result;
    }
    
    public boolean isConnected() throws Exception {
        return connectivityInspector.isGraphConnected();
    }
    
    public boolean pathExists(JSONVertex vFrom, JSONVertex vTo) throws Exception {
        return connectivityInspector.pathExists(vFrom, vTo);
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
        // http://www.jgrapht.org/javadoc/index.html?org/jgrapht/event/GraphVertexChangeEvent.html
        // INFO: vertexAdded(org.jgrapht.event.GraphVertexChangeEvent[source=([{'id':'a','city':'minneapolis'}], [])])
        
        log.info("vertexAdded(" + e.toString() + ")");
    }
    
    public void vertexRemoved(GraphVertexChangeEvent<JSONVertex> e) {
        // http://www.jgrapht.org/javadoc/index.html?org/jgrapht/event/GraphVertexChangeEvent.html
        // 
    
        //log.info("vertexRemoved(" + e.toString() + ")");
        
        try {
            final String eventType;
            
            if (e.getType() == GraphVertexChangeEvent.BEFORE_VERTEX_REMOVED) eventType = "before_vertex_removed";
            else if (e.getType() == GraphVertexChangeEvent.VERTEX_REMOVED) eventType = "vertex_removed";
            else eventType = "vertex:unknown_event_type:" + e.getType();
            
            log.info("<event> [vertexRemoved] " + e.getVertex().get(KEY_FIELD) + " -> " + eventType);
            
            if (e.getType() == GraphVertexChangeEvent.VERTEX_REMOVED) {
                JSONVertex jv = e.getVertex();
                indexWriter.deleteDocuments(new Term(KEY_FIELD, jv.getString(KEY_FIELD)));
                refreshIndex();
                log.info("<implicit> removeVertex: id [" + jv.get(KEY_FIELD) + "] + index entries");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        
        // pub client-event message(s)
        
    }
    
    // EDGE SET LISTENER
    
    public void edgeAdded(GraphEdgeChangeEvent<JSONVertex, JSONEdge> e) {
        // http://www.jgrapht.org/javadoc/org/jgrapht/event/GraphEdgeChangeEvent.html
        // INFO: edgeAdded(org.jgrapht.event.GraphEdgeChangeEvent[source=([{'id':'a','city':'minneapolis'}, {'id':'b','city':'duluth'}, {'id':'c','city':'rockville'}], [flight-to=({'id':'a','city':'minneapolis'},{'id':'b','city':'duluth'}), flight-to=({'id':'b','city':'duluth'},{'id':'c','city':'rockville'}), flight-to=({'id':'a','city':'minneapolis'},{'id':'c','city':'rockville'})])])
        
        // pub client-event message(s)
        
        log.info("edgeAdded(" + e.toString() + ")");
    }

    public void edgeRemoved(GraphEdgeChangeEvent<JSONVertex, JSONEdge> e) {
        // http://www.jgrapht.org/javadoc/org/jgrapht/event/GraphEdgeChangeEvent.html
        
        try {
        
            final String eventType;
            if (e.getType() == GraphEdgeChangeEvent.BEFORE_EDGE_REMOVED) eventType = "before_edge_removed";
            else if (e.getType() == GraphEdgeChangeEvent.EDGE_REMOVED) eventType = "edge_removed";
            else eventType = "edge:unknown_event_type:" + e.getType();
            
            log.info("<event> [edgeRemoved] " + e.getEdge().get(KEY_FIELD) + " -> " + eventType);
            
            // handle implicit deletions (as a result of removeVertex, et al)
            if (e.getType() == GraphEdgeChangeEvent.EDGE_REMOVED) {
            
                // handle implicitly deleted edges
                JSONEdge je = e.getEdge();
                indexWriter.deleteDocuments(new Term(KEY_FIELD, je.get(KEY_FIELD)));
                refreshIndex();
                log.info("<implicit> removeEdge: id [" + je.get(KEY_FIELD) + "] + index entries");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        
        // pub client-event message(s)
        
        log.info("edgeRemoved(" + e.toString() + ")");
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





