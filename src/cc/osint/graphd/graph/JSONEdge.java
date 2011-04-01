package cc.osint.graphd.graph;

import org.apache.log4j.Logger;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.json.JSONObject;

public class JSONEdge<V> 
    extends DefaultWeightedEdge 
    implements java.lang.Comparable {
    static Logger log = Logger.getLogger(JSONEdge.class);

    private V source;
    private V target;
    private String label;
    private JSONObject data;

    public JSONEdge(V source, V target, String label) {
        this.source = source;
        this.target = target;
        this.label = label;
        data = new JSONObject();
    }

    // deprecated: use getSource
    public V getV1() {
        return source;
    }

    // deprecated: use getTarget
    public V getV2() {
        return target;
    }
    
    public V getSource() {
        return source;
    }
    
    public V getTarget() {
        return target;
    }
    
    public void inherit(JSONObject jo) throws Exception {
        for(String k: JSONObject.getNames(jo)) {
            data.put(k, jo.get(k));
        }
    }

    public String toString() {
        return label;
    }

    public JSONEdge() {
        data = new JSONObject();
    }

    public String get(String k) throws Exception {
        return data.getString(k);
    }
    
    public void put(String k, String v) throws Exception {
        data.put(k, v);
    }
    
    public void remove(String k) throws Exception {
        data.remove(k);
    }

    public boolean has(String k) {
        return data.has(k);
    }
    
    public JSONObject asJSONObject() throws Exception {
        JSONObject jo = new JSONObject(data.toString());
        JSONObject je = new JSONObject();
        je.put(Graph.EDGE_SOURCE_FIELD, ((JSONObject) source).get(Graph.KEY_FIELD));
        je.put(Graph.EDGE_TARGET_FIELD, ((JSONObject) target).get(Graph.KEY_FIELD));
        je.put(Graph.DATA_FIELD, jo);
        return je;
    }
    
    public JSONObject asClientJSONObject() throws Exception {
        JSONObject jo = asJSONObject();
        
        /*
         * if the object has a Graph.DATA_FIELD (e.g., "_data"),
         *    collapse its fields into the containing object
         *
         * from the client point of view, the separation is
         *    irrelevant (it originally was used due to the
         *    way indexing used to work-- this is no longer
         *    the case and will probably be factored out)
         *
         * TODO: factor out Graph.DATA_FIELD use across the board
         *
        */
        JSONObject jo1 = new JSONObject();
        JSONObject dataObj = jo.getJSONObject(Graph.DATA_FIELD);
        for(String k: JSONObject.getNames(dataObj)) {
            jo1.put(k, dataObj.get(k));
        }
        for(String k: JSONObject.getNames(jo)) {
            if (k.equals(Graph.DATA_FIELD)) continue;
            jo1.put(k, jo.get(k));
        }
        return jo1;
    }

    public String toString(int d) throws org.json.JSONException {
        return data.toString(4).replaceAll("\"", "\\\"");
    }
    
    public int compareTo(Object o) {
        double thisWeight = getWeight();
        double oWeight = ((JSONEdge)o).getWeight();
        if (thisWeight == oWeight) return 0;
        else if (thisWeight > oWeight) return 1;
        else return -1;
    }
    
}
