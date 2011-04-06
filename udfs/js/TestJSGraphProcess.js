
TestJSGraphProcess = function(_process) {
    this._process = _process;
    log("TestJSGraphProcess constructor: _process = " + _process);
    log("TestJSGraphProcess constructor: " + _process.getPid());

    this.beforeKill = function() {
        log(this._process.getPid() + ": beforeKill");
    };
            
    this.beforeRemoveVertex = function(vertex) {
        log(this._process.getPid() + ": beforeRemoveVertex");
    };
            
    this.beforeRemoveEdge = function(edge) {
        log(this._process.getPid() + ": beforeRemoveVertex");
    };
            
    this.afterRemoveEdge = function(edge) {
        log(this._process.getPid() + ": beforeRemoveVertex");
    };
            
    this.message = function(msg) {
        //log("Graph.KEY_FIELD = " + Packages.cc.osint.graphd.graph.Graph.KEY_FIELD);
        log("this._process = " + this._process);
        log("msg = " + msg.toString());
        log("msg.visited = " + msg.visited);
        if (this._process.getContext().get("_key") != "v2") {
            _emit(_process, "v2", "v2-proc", {"visited": ["v1"]});
        } else {
            log("i am v2");
        }
    };
    
    return this;
};

