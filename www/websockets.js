
if (!Protograph) Protograph = {};

Protograph.WebSocketConnection = function(host, port, handler) {
    this.host = host;
    this.port = port;
    this.websocketURL = "ws://" + host + ":" + port + "/websocket";
    this.socket = null;
    this.handler = handler;
    self = this;
    
    this.connect = function() {
        if (window.WebSocket) {
            this.socket = new WebSocket(this.websocketURL);
            
            this.socket.onmessage = function(event) {
                console.log("onmessage: " + event);
                self.handler.onMessage(event);
            };
            
            this.socket.onopen = function(event) { 
                console.log("onopen: ");                
                self.handler.onOpen(event);
            };
            
            this.socket.onclose = function(event) {
                console.log("onclose: " + event);
                if (self.handler.onClose) self.handler.onClose(event);
            };
            return true;
            
        } else {
            console.log("no websockets");
            alert("Your browser does not support WebSockets.");
            if (self.handler.onNoWebSocketSupport) {
                self.handler.onNoWebSocketSupport();
            }
            if (self.handler.err) {
                self.handler.err("Your browser does not support WebSockets.");
            }
            return false;
        }
    };
    
    this.send = function(message) {
        console.log("send: " + event);
        if (!window.WebSocket) {
            if (self.handler.onNoWebSocketSupport) {
                self.handler.onNoWebSocketSupport();
            }
            return false;
        }
        if (self.socket.readyState == WebSocket.OPEN) {
            self.socket.send(message);
            true;
        } else {
            if (self.handler.err) {
                self.handler.err("cannot send '" + message + "', socket not open");
            }
            return false;
        }
    }

};

