package cc.osint.graphd.server;

public class GraphServerProtocol {

    /* graph management */
        
    final public    static String CMD_GOODBYE = "bye";        // disconnect
    final public    static String CMD_USE = "use";            // select graph to use
    final public    static String CMD_CREATE = "create";      // create graph
    final public    static String CMD_DROP = "drop";          // delete graph
    final public    static String CMD_NAMECON = "namecon";    // "name" this connection
    final public    static String CMD_CLSTATE = "clstate";    // dump client state (debug)
    final public    static String CMD_SSTAT = "sstat";        // dump server status (debug)
    final public    static String CMD_GSTAT = "gstat";        // dump graph status (debug)
    final public    static String CMD_LISTG = "listg";        // list names of graphs
    final public    static String CMD_EXEC = "exec";          // execute a file of commands
    
    /* vertex, edge management, querying, attributes */
    
    final public    static String CMD_Q = "q";                // query graph objects by property
    final public    static String CMD_QP = "qp";              // query processes by property
    final public    static String CMD_EXISTS = "exists";      // key exists?
    final public    static String CMD_CVERT = "cvert";        // create vertex
    final public    static String CMD_CEDGE = "cedge";        // create edge
    final public    static String CMD_SET = "set";            // set a property on an edge or vertex
    final public    static String CMD_DEL = "del";            // delete object (vertex or edge)
    final public    static String CMD_GET = "get";            // get object (vertex or edge)
    final public    static String CMD_SPY = "spy";            // dump JSONVertex or JSONEdge explicitly
    final public    static String CMD_INCW = "incw";          // increment edge weight
    
    /* analysis */
    
    final public    static String CMD_SPATH = "spath";        // shortest path between two vertices
    final public    static String CMD_KSPATH = "kspath";      // k-shortest paths between two vertices (w/ opt. maxHops)
    final public    static String CMD_HC = "hc";              // hamiltonian cycle "traveling salesman problem"
    final public    static String CMD_EC = "ec";              // eulerian circuit
    final public    static String CMD_EKMF = "ekmf";          // edmonds karp maximum flow
    final public    static String CMD_CN = "cn";              // chromatic number "graph coloring"
    final public    static String CMD_KMST = "kmst";          // compute (kruskal's) minimum spanning tree
    final public    static String CMD_VCG = "vcg";            // vertex cover (greedy)
    final public    static String CMD_VC2A = "vc2a";          // vertex cover (2 approximation)
    final public    static String CMD_CSETV = "csetv";        // maximally connected set of V
    final public    static String CMD_CSETS = "csets";        // all maximally connected sets
    final public    static String CMD_ISCON = "iscon";        // is graph connected?
    final public    static String CMD_UPATHEX = "upathex";    // does any UNDIRECTED path exist from v0 -> v1?
    final public    static String CMD_FAMC = "famc";          // Bron Kerosch Clique Finder: find all maximal cliques
    final public    static String CMD_FBMC = "fbmc";          // Bron Kerosch Clique Finder: find biggest maximal cliques
    final public    static String CMD_ASPV = "aspv";          // all shortest paths from V (via Floyd-Warshall)
    final public    static String CMD_GCYC = "gcyc";          // get vertex set for the subgraph of all cycles
    final public    static String CMD_VCYC = "vcyc";          // get vertex set for the subgraph of all cycles that contain V
    
    /* simulation */
    
    final public    static String CMD_QSIM = "qsim";                // query simulation index (udf, process, channel, subscriber)
    final public    static String CMD_DEFINE_UDF = "define_udf";    // define_udf <udf_key> <udf_type> <udf_data_url>
    final public    static String CMD_SPROC = "sproc";              // start process: sproc <key> <udf_key> <process_name>
    final public    static String CMD_EMIT = "emit";                // emit a message to a running process: emit <key> <process_name> <json>
    final public    static String CMD_CCHAN = "cchan";              // create a channel endpoint
    final public    static String CMD_PUBLISH = "publish";          // publish a message to a channel
    final public    static String CMD_SUBSCRIBE = "subscribe";      // subscribe to a channel
    final public    static String CMD_UNSUBSCRIBE = "unsubscribe";  // unsubscribe from a channel
    
    /* responses */
    
    final public    static String R_OK = "-ok";                         // standard reply
    final public    static String R_ERR = "-err";                       // error processing request
    final public    static String R_UNK = "-unk";                       // unknown request
    final public    static String R_NOT_IMPL = "-not_impl";             // cmd not implemented
    final public    static String R_NOT_FOUND = "-not_found";           // object not found
    final public    static String R_NOT_EXIST = "-not_exist";           // requested resource does not exist
    final public    static String R_ALREADY_EXIST = "-already_exist";   // requested resource does not exist
    final public    static String R_UNKNOWN_OBJECT_TYPE = 
                                    " unknown_object_type";             // should theoretically never happen; if a get(key)
                                                                        //   returns anything other than edge or vertex
    final public    static String R_BATCH_OK = "-batch-ok";             // response at the end of a selector-driven batch of responses
    
    /* protocol misc */
    
    final public    static String NL = "\n";
    final public    static String SPACE = " ";

}
