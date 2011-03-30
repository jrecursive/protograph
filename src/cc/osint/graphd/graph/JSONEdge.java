package cc.osint.graphd.graph;

import org.apache.log4j.Logger;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.json.JSONObject;

public class JSONEdge<V> 
    extends DefaultWeightedEdge 
    implements java.lang.Comparable {
    static Logger log = Logger.getLogger(JSONEdge.class);

    private V v1;
    private V v2;
    private String label;
    private JSONObject data;

    public JSONEdge(V v1, V v2, String label) {
        this.v1 = v1;
        this.v2 = v2;
        this.label = label;
        data = new JSONObject();
    }

    public V getV1() {
        return v1;
    }

    public V getV2() {
        return v2;
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

    public void put(String k, String v) throws Exception {
        data.put(k, v);
    }

    public String get(String k) throws Exception {
        return data.getString(k);
    }

    public boolean has(String k) {
        return data.has(k);
    }
    
    public JSONObject asJSONObject() throws Exception {
        JSONObject jo = new JSONObject(data.toString());
        JSONObject je = new JSONObject();
        je.put("v1", v1);
        je.put("v2", v2);
        je.put("rel", label);
        je.put("data", jo);
        return je;
    }

    public String toString(int d) throws org.json.JSONException {
        return data.toString(4).replaceAll("\"", "\\\"");
    }
    
    public int compareTo(Object o) {
        double thisWeight = getWeight();
        double oWeight = ((JSONEdge)o).getWeight();
        int thisRefWeight = (int) (100000.00 * thisWeight);
        int oRefWeight = (int) (100000.00 * oWeight);
        return thisRefWeight - oRefWeight;
    }
    
}
