
ORProcess = function(_process) {
    this._process = _process;
    this.inputState = {};

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
        if (msg.type == "clock") {
            log("processing clock pulse");
            this.clock(msg);
        } else if (msg.type == "state") {
            log("processing state message: " + msg.from + ", " + msg.state);
            this.state(msg);
        } else {
            log("unknown message type: " + msg.type);
        }
    };
    
    this.state = function(msg) {
        log("** this.inputState[" + msg.from + "] = " + msg.state);
        this.inputState[""+msg.from] = msg.state;
    };
        
    this.clock = function(msg) {
        var signals = _process.getGraph().queryGraphIndex("_type:e _target:" + 
            this._process.getContext().get("_key"));
        var outputValue = 0;
        for (var i=0; i<signals.size(); i++) {
            var signalObj = signals.get(i);
            var signalKey = "" + signalObj.getString("_source");
            if (this.inputState[signalKey] == "1") {
                log(">> UP signal from " + signalKey);
                outputValue = 1;
                break;
            }
        }
        log("ORProcess[" + this._process.getContext().get("_key") + "]->outputValue = " + outputValue);
        var outputs = _process.getGraph().getOutgoingNeighborsOf(_process.getContext(),"signal").toArray();
        for (var i=0; i<outputs.length; i++) {
            var vertex = outputs[i];
            this._process.getGraph().emitByQuery(
                "obj_key:" + vertex.getKey(), 
                _jsobj_to_JSONObject({
                    "type": "state",
                    "from": "" + this._process.getContext().get("_key"),
                    "state": outputValue 
                })
            );
        }
        this.inputState = {};
    };
    
    return this;
};
