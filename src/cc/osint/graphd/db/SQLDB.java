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

package cc.osint.graphd.db;

import java.lang.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.sql.*;
import javax.sql.*;
import org.json.*;

public class SQLDB {
    private static final Logger log = Logger.getLogger(
        SQLDB.class.getName());
    
    final private String name;
    final Connection conn;

    public SQLDB(String name) throws Exception {
        this.name = name;
        // process registry
        Class.forName("org.hsqldb.jdbc.JDBCDriver" );
        conn = DriverManager.getConnection("jdbc:hsqldb:mem:" + name, 
                                           "SA", 
                                           "");
    }
    
    public Connection getConnection() {
        return conn;
    }
    
    public void update(String expression) throws Exception {
        log.info("update(" + expression + ")");
        Statement st = null;
        st = conn.createStatement();
        int i = st.executeUpdate(expression);
        if (i == -1) {
            log.info("db error: " + expression);
            throw new Exception("db error: " + expression);
        }
        st.close();
    }
    
    public JSONObject query(String expression) throws Exception {
        log.info("query(" + expression + ")");
        Statement st = null;
        ResultSet rs = null;
        st = conn.createStatement();
        rs = st.executeQuery(expression);
        JSONObject result = jsonizeResultSet(rs);
        st.close();
        return result;
    }
    
    public void shutdown() throws SQLException {
        Statement st = conn.createStatement();
        st.execute("SHUTDOWN");
        conn.close();
    }
    
    private JSONObject jsonizeResultSet(ResultSet rs) throws Exception {
        List<JSONObject> results = new ArrayList<JSONObject>();
        ResultSetMetaData md = rs.getMetaData();
        int colmax = md.getColumnCount();
        int i;
        for (; rs.next(); ) {
            JSONObject result = new JSONObject();
            for (i=1; i<=colmax; i++) {
                String colName = md.getColumnName(i).toLowerCase();
                String colClassName = md.getColumnClassName(i);
                String colType = md.getColumnTypeName(i);
                Object obj = rs.getObject(i);
                result.put(colName, obj);
                log.info(colName + ": " + 
                         colClassName + ": " + 
                         colType + ": " + 
                         obj.toString());
            }
            results.add(result);
        }
        JSONObject result = new JSONObject();
        result.put("results", results);
        return result;
    }
}

