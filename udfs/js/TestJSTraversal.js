
TestJSTraversal = function(startingState) {
    this.startingState = startingState;
    
    log("TestJSTraversal: constructor");
    log("TestJSTraversal: startingState = " + 
        this.startingState);
    
    this.connectedComponentStarted = function(msg) {
        log("connectedComponentStarted: " + msg);
    };
    
    this.connectedComponentFinished = function(msg) {
        log("connectedComponentFinished: " + msg);
    };
    
    this.edgeTraversed = function(msg) {
        log("edgeTraversed: " + msg);
    };
    
    this.vertexFinished = function(msg) {
        log("vertexFinished: " + msg);
    };
    
    this.vertexTraversed = function(msg) {
        log("vertexTraversed: " + msg);
    };
    
    return this;
};

log("TestJSTraversal: loaded");

