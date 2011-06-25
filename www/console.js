if (!Protograph) Protograph = {};

Protograph.Console = function(app, containerElementId, inputElementId, outputElementId) {
    var self = this;
    this.app = app;
    this.container = $("#" + containerElementId);
    this.in = $("#" + inputElementId);
    
    this.history = new Array();
    this.historyIndex = 0;
    this.lastScrollTimeoutId = 0;
    
    this.in.keypress(function(e) {
        if (e.which == 13) {
            e.preventDefault();
            self.onInput(self.in.val());
            self.clearInput();
        } else {
            self.currentVal = self.in.val();
        }
    });
    this.in.keydown(function(e) {
        if (e.which == 40) { // down arrow
            e.preventDefault();
            self.onArrowDown();
        } else if (e.which == 38) { // up arrow
            e.preventDefault();
            self.onArrowUp();
        }
    });
    self = this;
    
    this.out = $("#" + outputElementId);

    this.resize = function() {
        this.in.css("width", this.container.width()-10)
               .css("height", 25);
        this.out.css("width", this.container.width()-10);
    }
    
    this.onInput = function(str) {
        if (str == "") return;
        //console.log("console input: " + str);
        self.println(">> " + str);
        self.send(str);
        self.history.push(str);
        self.currentVal = "";
        self.historyIndex = 0;
    };
    
    this.onArrowUp = function() {
        //console.log("onArrowUp " + self.historyIndex + ", " + self.history.length);
        if (self.historyIndex == self.history.length) {
            return;
        }
        self.historyIndex++;
        self.in.val(self.history[self.history.length - self.historyIndex]);
        //console.log("onArrowUp");
    };
    
    this.onArrowDown = function() {
        //console.log("onArrowDown " + self.historyIndex + ", " + self.history.length);
        if (self.historyIndex == 0) {
            self.in.val(self.currentVal);
            return;
        }
        self.historyIndex--;
        self.in.val(self.history[self.history.length - self.historyIndex]);
    };
    
    this.onKeypress = function(key, isCtrl, isShift, isAlt) {
        //console.log(key + " [" + isCtrl + ", " + isShift + ", " + isAlt + ")");
    };
    
    this.clearInput = function() {
        self.in.val("");
    };
    
    this.clearOutput = function() {
        self.out.text("");
    };
    
    this.println = function(str) {
        self.out.html(this.out.html()+"\n"+str);
        /*
        if (self.lastScrollTimeoutId != 0) {
            clearTimeout(self.lastScrollTimeoutId);
        }
        self.lastScrollTimeoutId = setTimeout("periodic_scrolldown('" + outputElementId + "')", 250);
        */
    };
    
    this.print = function(str) {
        self.out.html(this.out.html()+str);
    };
    
    this.send = function(str) {
        self.app.send(str);
    };
};

function periodic_scrolldown(el) {
    $("#"+el)[0].scrollTop = $("#"+el)[0].scrollHeight;
    //$("#"+el)[0].height = $("#"+el)[0].scrollHeight;
}

