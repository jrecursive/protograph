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

// BASIC PERSISTENCE

public class Graph 
    implements GraphListener<JSONVertex, JSONEdge>,
               VertexSetListener<JSONVertex> {
    private ListenableDirectedWeightedGraph<JSONVertex, JSONEdge> gr;
    private ConcurrentHashMap<String, JSONVertex> vertices;
    
    private IndexWriter indexWriter;
    private IndexReader indexReader;
    private Searcher searcher;
    final private RAMDirectory luceneDirectory;
    
    final private static String INDEX_TYPE_FIELD = "_type";
    final private static String INDEX_KEY_FIELD = "_key";
    final private static String VERTEX_TYPE = "vertex";
    final private static String EDGE_TYPE = "edge";
    
    private static final Logger log = Logger.getLogger(
        Graph.class.getName());
    
    public Graph() throws Exception {
        gr = new ListenableDirectedWeightedGraph<JSONVertex, JSONEdge>(JSONEdge.class);
        vertices = new ConcurrentHashMap<String, JSONVertex>();
        luceneDirectory = new RAMDirectory();
        indexWriter = new IndexWriter(
            luceneDirectory,
            new StandardAnalyzer(Version.LUCENE_CURRENT),
            IndexWriter.MaxFieldLength.LIMITED);
        indexReader = indexWriter.getReader();
        searcher = new IndexSearcher(indexReader);
        gr.addVertexSetListener(this);
        gr.addGraphListener(this);
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
        je.put("id", key);
        je.inherit(jo);
        gr.addEdge(fromVertex, toVertex, je);
        gr.setEdgeWeight(je, weight);
        indexObject(key, EDGE_TYPE, jo);
        return key;
    }
    
    public void indexObject(String key, String type, JSONObject jo) throws Exception {
        Document doc = new Document();
        doc.add(new Field(INDEX_TYPE_FIELD, type,
            Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS));
        doc.add(new Field(INDEX_KEY_FIELD, key,
            Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS));

        if (null != jo &&
            null != JSONObject.getNames(jo)) {
            for (String k: JSONObject.getNames(jo)) {
                doc.add(new Field(k, jo.getString(k),
                        Field.Store.YES, Field.Index.ANALYZED_NO_NORMS));
            }
        }
        
        indexWriter.updateDocument(new Term(INDEX_KEY_FIELD, key), doc);
        refreshIndex();
    }
    
    private void refreshIndex() throws Exception {
        long t0 = System.currentTimeMillis();
        indexWriter.commit();
        //indexReader = indexWriter.getReader();
        searcher.close();
        indexReader.close();
        indexReader = indexWriter.getReader();
        searcher = new IndexSearcher(indexReader);
        long elapsed = System.currentTimeMillis() - t0;
        log.info("refreshIndex(): " + elapsed + "ms");
    }
    
    @SuppressWarnings("deprecation")
    public List<JSONObject> query(String query)
            throws Exception {
        ArrayList<JSONObject> matches = new ArrayList<JSONObject>();
        Analyzer analyzer = new WhitespaceAnalyzer();
        QueryParser qp = new QueryParser(Version.LUCENE_CURRENT,
                "id", analyzer);
        qp.setAllowLeadingWildcard(true);
        Query l_query = qp.parse(query);
        Filter l_filter = new CachingWrapperFilter(new QueryWrapperFilter(l_query));
        
        TopDocs hits = searcher.search(new MatchAllDocsQuery(), l_filter, 10000); // unlimited?
        for (int i = 0; i < hits.scoreDocs.length; i++) {
            int docId = hits.scoreDocs[i].doc;
            Document d = searcher.doc(docId);
            JSONObject result = new JSONObject();
            for (Fieldable f : d.getFields()) {
                result.put(f.name(), d.get(f.name()));
            }
            matches.add(result);
        }
        return matches;
    }
    
    public List<JSONObject> getShortestPath(String vFromKey, String vToKey) throws Exception {
        JSONVertex vFrom = getVertex(vFromKey);
        JSONVertex vTo = getVertex(vToKey);
        log.info("vFrom = " + vFrom.toString());
        log.info("vTo = " + vTo.toString());
        List<JSONObject> results = new ArrayList<JSONObject>();
        List<JSONEdge> path = DijkstraShortestPath.findPathBetween(gr, vFrom, vTo);
        if (null != path) {
            for(JSONEdge edge: path) {
                results.add(edge.asJSONObject());
            }
        } else {
            JSONObject errObj = new JSONObject();
            errObj.put("error", "no_path");
            results.add(errObj);
        }
        return results;
    }
    
    public boolean exists(String key) throws Exception {
        List<JSONObject> ar = query(INDEX_KEY_FIELD + ":\"" + key + "\"");
        return ar.size()>0;
    }
    
    public JSONObject get(String key) throws Exception {
        List<JSONObject> ar = query(INDEX_KEY_FIELD + ":\"" + key + "\"");
        if (ar.size() == 0) return null;
        return ar.get(0);
    }
    
    public JSONVertex getVertex(String key) throws Exception {
        return vertices.get(key);
    }
    
    public JSONEdge getEdge(String key) throws Exception {
        JSONObject jsonEdge = get(key);
        JSONVertex fromVertex = getVertex(jsonEdge.getString("_fromVertex"));
        JSONVertex toVertex = getVertex(jsonEdge.getString("_toVertex"));
        return gr.getEdge(fromVertex, toVertex);
    }
    
    public void removeEdge(JSONEdge je) throws Exception {
        if (gr.removeEdge(je)) {
            log.info("removeEdge(" + je.getString("id") + "): ok");
        }
    }
    
    public void removeVertex(JSONVertex jv) throws Exception {
        if (gr.removeVertex(jv)) {
            indexWriter.deleteDocuments(new Term(INDEX_KEY_FIELD, jv.getString("id")));
            refreshIndex();
            log.info("removeVertex(" + jv.getString("id") + ")");
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
            
            log.info("<event> [vertexRemoved] " + e.getVertex().getString("id") + " -> " + eventType);
            
            if (e.getType() == GraphVertexChangeEvent.VERTEX_REMOVED) {
                JSONVertex jv = e.getVertex();
                indexWriter.deleteDocuments(new Term(INDEX_KEY_FIELD, jv.getString("id")));
                refreshIndex();
                log.info("<implicit> removeVertex: id [" + jv.getString("id") + "] + index entries");
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
            
            log.info("<event> [edgeRemoved] " + e.getEdge().getString("id") + " -> " + eventType);
            
            // handle implicit deletions (as a result of removeVertex, et al)
            if (e.getType() == GraphEdgeChangeEvent.EDGE_REMOVED) {
            
                // handle implicitly deleted edges
                JSONEdge je = e.getEdge();
                indexWriter.deleteDocuments(new Term(INDEX_KEY_FIELD, je.getString("id")));
                refreshIndex();
                log.info("<implicit> removeEdge: id [" + je.getString("id") + "] + index entries");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        
        // pub client-event message(s)
        
        log.info("edgeRemoved(" + e.toString() + ")");
    }
    
}





