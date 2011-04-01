package cc.osint.graphd.graph;

import org.apache.log4j.Logger;
import org.json.JSONObject;

public class JSONVertex extends JSONObject {
    static Logger log = Logger.getLogger(JSONVertex.class);
    final private static String KEY_FIELD = 
        cc.osint.graphd.graph.Graph.INDEX_KEY_FIELD;
    
    public JSONVertex(String key) {
        super();
        try {
            super.put(KEY_FIELD, key);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    public JSONVertex(String key, JSONObject jo) {
        super();
        try {
            if (null != jo &&
                null != JSONObject.getNames(jo)) {
                for(String k: JSONObject.getNames(jo)) {
                    super.put(k, jo.get(k));
                }
            }
            super.put(KEY_FIELD, key);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void put(String k, String v) throws Exception {
        super.put(k, v);
    }
    
    /*
     * note: remove is implicit since this still
     *       extends JSONObject
    */
    
    public String toString(int d) throws org.json.JSONException {
        return super.toString(4).replaceAll("\"", "\\\"");
    }

    public String toString() {
        String str = "";
        try {
            str = super.toString();

            JSONObject jo = new JSONObject(str);
            str = jo.toString();

            /*
            while (str.indexOf("\"") != -1) {
                str = str.replace("\"", "'");
            }
            */
            
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return str;
    }
}
