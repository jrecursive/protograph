package cc.osint.graphd.server;

public class GraphServerProtocol {

    /* graph management */
        
    final protected static String CMD_GOODBYE = "bye";        // disconnect
    final protected static String CMD_USE = "use";            // select graph to use
    final protected static String CMD_CREATE = "create";      // create graph
    final protected static String CMD_DROP = "drop";          // delete graph
    final protected static String CMD_NAMECON = "namecon";    // "name" this connection
    final protected static String CMD_CLSTATE = "clstate";    // dump client state (debug)
    final protected static String CMD_SSTAT = "sstat";        // dump server status (debug)
    final protected static String CMD_GSTAT = "gstat";        // dump graph status (debug)
    final protected static String CMD_LISTG = "listg";        // list names of graphs
    
    /* vertex, edge management, querying, attributes */
    
    final protected static String CMD_Q = "q";                // query graph objects by property
    final protected static String CMD_QP = "qp";              // query processes by property
    final protected static String CMD_EXISTS = "exists";      // key exists?
    final protected static String CMD_CVERT = "cvert";        // create vertex
    final protected static String CMD_CEDGE = "cedge";        // create edge
    final protected static String CMD_SET = "set";            // set a property on an edge or vertex
    final protected static String CMD_DEL = "del";            // delete object (vertex or edge)
    final protected static String CMD_GET = "get";            // get object (vertex or edge)
    final protected static String CMD_SPY = "spy";            // dump JSONVertex or JSONEdge explicitly
    final protected static String CMD_INCW = "incw";          // increment edge weight
    
    /* analysis */
    
    final protected static String CMD_SPATH = "spath";        // shortest path between two vertices
    final protected static String CMD_KSPATH = "kspath";      // k-shortest paths between two vertices (w/ opt. maxHops)
    final protected static String CMD_HC = "hc";              // hamiltonian cycle "traveling salesman problem"
    final protected static String CMD_EC = "ec";              // eulerian circuit
    final protected static String CMD_EKMF = "ekmf";          // edmonds karp maximum flow
    final protected static String CMD_CN = "cn";              // chromatic number "graph coloring"
    final protected static String CMD_KMST = "kmst";          // compute (kruskal's) minimum spanning tree
    final protected static String CMD_VCG = "vcg";            // vertex cover (greedy)
    final protected static String CMD_VC2A = "vc2a";          // vertex cover (2 approximation)
    final protected static String CMD_CSETV = "csetv";        // maximally connected set of V
    final protected static String CMD_CSETS = "csets";        // all maximally connected sets
    final protected static String CMD_ISCON = "iscon";        // is graph connected?
    final protected static String CMD_UPATHEX = "upathex";    // does any UNDIRECTED path exist from v0 -> v1?
    final protected static String CMD_FAMC = "famc";          // Bron Kerosch Clique Finder: find all maximal cliques
    final protected static String CMD_FBMC = "fbmc";          // Bron Kerosch Clique Finder: find biggest maximal cliques
    final protected static String CMD_ASPV = "aspv";          // all shortest paths from V (via Floyd-Warshall)
    final protected static String CMD_GCYC = "gcyc";          // get vertex set for the subgraph of all cycles
    final protected static String CMD_VCYC = "vcyc";          // get vertex set for the subgraph of all cycles that contain V
    
    /* simulation */
    
    final protected static String CMD_EMIT = "emit";          // emit a message to a running process
    
    /* responses */
    
    final protected static String R_OK = "-ok";                // standard reply
    final protected static String R_ERR = "-err";              // error processing request
    final protected static String R_UNK = "-unk";              // unknown request
    final protected static String R_NOT_IMPL = "-not_impl";    // cmd not implemented
    final protected static String R_NOT_FOUND = "-not_found";  // object not found
    final protected static String R_NOT_EXIST = "-not_exist";  // requested resource does not exist
    final protected static String R_UNKNOWN_OBJECT_TYPE = 
                                    " unknown_object_type"; // should theoretically never happen; if a get(key)
                                                            //   returns anything other than edge or vertex
    final protected static String R_BATCH_OK = "-batch-ok";    // response at the end of a selector-driven batch of responses
    
    /* protocol misc */
    
    final protected static String NL = "\n";
    final protected static String SPACE = " ";

}
