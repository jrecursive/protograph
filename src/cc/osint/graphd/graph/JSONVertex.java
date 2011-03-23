package cc.osint.graphd.graph;

import org.apache.log4j.Logger;
import org.json.JSONObject;

public class JSONVertex extends JSONObject {
    static Logger log = Logger.getLogger(JSONVertex.class);

    public JSONVertex(String id) {
        super();
        try {
            super.put("id", id);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    public JSONVertex(String id, JSONObject jo) {
        super();
        try {
            for(String k: JSONObject.getNames(jo)) {
                super.put(k, jo.get(k));
            }
            super.put("id", id);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void put(String k, String v) throws Exception {
        super.put(k, v);
    }

    public String toString(int d) throws org.json.JSONException {
        return super.toString(4).replaceAll("\"", "\\\"");
    }

    public String toString() {
        String str = "";
        try {
            str = super.toString();

            JSONObject jo = new JSONObject(str);
            str = jo.toString();

            while (str.indexOf("\"") != -1) {
                str = str.replace("\"", "'");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return str;
    }
}
