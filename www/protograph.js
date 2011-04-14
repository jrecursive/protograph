/*
 * Protograph.js :)
 * just a shunt for now
*/

Protograph = {};
Protograph.Graph = function(viewport, repulsion, stiffness, friction) {
    this.viewport = viewport;
    this.sys = arbor.ParticleSystem(repulsion, stiffness, friction, false, 55, 0.02, 0.0); // create the system with sensible repulsion/stiffness/friction
    //this.sys.parameters({gravity:true}); // use center-gravity to make the graph settle nicely (ymmv)
    this.sys.renderer = Renderer(this.viewport); // our newly created renderer will have its .init() method called shortly by sys...
    
    this.resize = function(w, h) {
        this.sys.screenSize(w, h);
    };
    
    this.addEdge = function(s, t) {
        this.sys.addEdge(s, t);
    };
    
    this.addEdge = function(s, t, data) {
        if (!objs[s]) makenode(s);
        if (!objs[t]) makenode(t);
        if (!objs[make_edge_name(s,t)]) {
            if (use_dom) {
                makeedge(s,t);
            }
        }
        this.sys.addEdge(s, t, data);
    };
    
    this.addVertex = function(v, attrs) {
        if (!objs[v]) makenode(v);
        this.sys.addNode(v, attrs);
    };
    
}

function makenode(s) {
    var imgtypes = [];
    imgtypes.push("monsterid");
    imgtypes.push("wavatar");
    imgtypes.push("retro");
    imgtypes.push("identicon");
    var imgtype = imgtypes[Math.floor(Math.random()*imgtypes.length)];
    var rhash = Sha1.hash((""+Math.random()));
    $("#stuff").append("<div class='vertex' id='v_" + s + "'><img style='z-index:8; align:center; vertical-align: center; position:relative; top:2px;' src='http://www.gravatar.com/avatar/" + rhash + "?d=" + imgtype + "&s=30'></div>");
    objs[s] = $("#v_" + s);
    objs[s].css("position", "absolute")
            .css("z-index", 9)
            .css("border", "1px solid #2c4643")
            .css("background-color", "#222")
            //.css("opacity", .8)
            .css("font", "24px verdana")
            .css("padding", "6px")
            //.css("padding-top", "9px")
            //.css("margin", "1px")
            .css("color", "white")
            .css("border-radius", "10px")
            //.css("overflow", "hidden")
            //.css("width", 45)
            //.css("height", 45)
            //.html("&nbsp;*&nbsp;");
            //.html("&nbsp;" + s + "&nbsp;");
}

function make_edge_name(s,t) {
    return "e_" + s + "_" + t;
}

function makeedge(s,t) {
    var edge_name = make_edge_name(s,t);
    $("#stuff").append("<div id='" + edge_name + "'></div>");
    //$("#stuff").append("<hr id='" + edge_name + "'></div>");
    objs[edge_name] = $("#" + edge_name);
    objs[edge_name].css("height", "2px")
                .css("align", "left")
		        .css("position", "absolute")
                //.css("border", "1px #2c4643 solid")
                .css("border", "1px #2c4643 solid")
                .css("height", "0px")
                .css("color", "#2c4643")
                .css("margin", "0px")
                .css("padding", "0px")
                .css("-webkit-transform-origin", "0% 50%");
                //.css("background-color", "black");
}

