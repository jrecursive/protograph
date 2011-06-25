
RandomLogicValueGenerator = function(_process) {
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
        var _key = "" + this._process.getContext().get("_key");
        var outputs = _process.getGraph().getOutgoingNeighborsOf(_process.getContext(),"signal").toArray();
        for (var i=0; i<outputs.length; i++) {
            var vertex = outputs[i];
            var outputValue = Math.round(Math.random());
            _process.getGraph().emitByQuery(
                "obj_key:" + vertex.getKey(), 
                _jsobj_to_JSONObject({ "type": "state",
                  "from": _key,
                  "state": outputValue 
                })
            );
        }
    };
    return this;
};
