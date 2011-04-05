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
    
    
    
}

