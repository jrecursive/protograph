
TestJSGraphProcess = function(_process) {
    this._process = _process;

    this.beforeKill = function() {
        log(this._process.getPid() + ": beforeKill");
    };
            
    this.beforeRemoveVertex = function(vertex) {
        log(this._process.getPid() + ": beforeRemoveVertex");
    };
            
    this.beforeRemoveEdge = function(edge) {
        log(this._process.getPid() + ": beforeRemoveEdge");
    };
            
    this.afterRemoveEdge = function(edge) {
        log(this._process.getPid() + ": afterRemoveEdge");
    };
            
    this.message = function(msg) {
        var _key = this._process.getContext().get("_key");
        if (!msg.visited) msg.visited = {};
        if (msg.visited[_key]) return;
        msg.visited[_key] = _process.nanoTime();
        var sent = 0;
        var neighbors = _process.getGraph().getOutgoingNeighborsOf(_process.getContext()).toArray();
        for (var i=0; i<neighbors.length; i++) {
            var vertex = neighbors[i];
            log("vertex = " + vertex);
            if (!msg.visited[vertex.get("_key")]) {
                _emit(_process, vertex.getKey(), vertex.getKey() + "-TestJSGraphProcess", msg);
                msg.visited[vertex.getKey()] = _process.nanoTime();
                sent++;
            }
        }
        if (sent == 0) {
            _process.getGraph().publishToEndpointByName(
                "test_endpoint", 
                _jsobj_to_JSONObject(msg.visited)
            );
            log(this._process.getPid() + ": " +
                _key + 
                ": endpoint: " + TAFFY.JSON.stringify(msg.visited));
        }
    };
    
    return this;
};

