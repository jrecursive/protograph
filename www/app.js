
/*
 * app.js: protograph console application & websocket interface demo
 *
*/

var App = function(host, port, containerElementId, w, h, fontSize) {
    this.containerElementId = containerElementId;
    this.container = $("#" + containerElementId);
    this.w = w;
    this.h = h;
    this.fontSize = fontSize;
    self = this;
    
    this.resize = function(w, h) {
        this.w = w;
        this.h = h;
        this.container.css("width", w);
        this.container.css("height", h);
        $("#" + this.containerElementId + "Out")[0].rows =
            (this.container.height()*.9 - 25) / fontSize;
        this.consoleHandler.resize(w, h, fontSize);
    };
    
    this.redraw = function() {
        this.resize(this.w, this.h);
    };
    
    this.consoleHandler = 
        new Protograph.Console(this,
                               this.containerElementId,
                               this.containerElementId + "In", 
                               this.containerElementId + "Out");
    
    this.println = function(str) {
        this.consoleHandler.println(str);
    };
    
    this.connect = function() {
        this.connection.ws.connect();
    };
    
    this.send = function(cmd) {
        this.connection.ws.send(cmd);
    }
    
    this.ConnectionHandler = function(app, host, port) {
        this.app = app;
        this.host = host;
        this.port = port;
        self = this;
        self.ws = new Protograph.WebSocketConnection(host, port, self);
        
        this.onMessage = function(ev) {
            var msg = ev.data;
            this.app.println(msg);
            if (msg[0] != '@') {
                periodic_scrolldown(this.app.consoleHandler.out[0].id);
            }
            //console.log("onMessage " + msg);
        };
        
        this.onOpen = function(ev) {
            this.app.println("connected " + host + ":" + port);
            this.ws.send("listg");
        };
        
        this.onClose = function(ev) {
            //console.log("onClose " + ev);
            console.dir(ev);
        };
        
        this.err = function(msg) {
            //console.log("err: " + msg);
        };
        
        this.onNoWebSocketSupport = function() {
            alert("Your browser does not support WebSockets.");
        };
    };
    this.connection = new this.ConnectionHandler(this, host, port);
    
    //console.log("new App(" + containerElementId + ", " + w + ", " + h + ")");
};

var app = null;
$(document).ready(function() {
    //console.log("ready!");
    app = new App("thinkdifferent.ly", 10102, "console", $(window).width() * .5, $(window).height() * .8, 12);
    app.redraw();
    $(window).resize(function() {
        //console.log("resize!");
        app.resize($(window).width() * .5, $(window).height() * .8);
    });
    $("#consoleIn").focus();
    app.println("connecting...");
    app.connect();
});

