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

package cc.osint.graphd.graph;

import org.apache.log4j.Logger;
import org.json.JSONObject;

public class JSONVertex extends JSONObject {
    static Logger log = Logger.getLogger(JSONVertex.class);
    
    public JSONVertex(String key) {
        super();
        try {
            super.put(Graph.KEY_FIELD, key);
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
            super.put(Graph.KEY_FIELD, key);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /*
     * vertex-specific pre-built "gets"
    */
    
    public String getKey() throws Exception {
        return getString(Graph.KEY_FIELD);
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
