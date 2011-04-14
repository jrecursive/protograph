/*
 * udf management
*/

var TAFFY = new Object();
var _udf_registry = new Object();
var _processes = new Object();

function _udf_exists(udf_key) {
    return _udf_registry[udf_key] != undefined;
}

function _udf_instance(key, pid, _process) {
    eval("_processes[pid] = new " + key + "(_process);");
}

function _udf_call(pid, func) {
    var args = Array.prototype.slice.call(arguments);  
    args.shift(); // pid
    args.shift(); // func
    _processes[pid][func].apply(_processes[pid], args);
}

/*
 * sim
*/

function _emit(_process, objkey, prockey, msg) {
    var msgObj = _jsobj_to_JSONObject(msg);
    _process.emit(objkey, prockey, msgObj);
};

function _emit_by_query(_process, _q, msg) {
    var msgObj = _jsobj_to_JSONObject(msg);
    _process.emitByQuery(_q, msgObj);
};

/*
 * util
*/

function _JSONstring_to_js(jsonstr) {
    eval("var _x = " + jsonstr + ";");
    return _x;
}

function _jsobj_to_JSONObject(obj) {
    return new Packages.org.json.JSONObject(TAFFY.JSON.stringify(obj));
}

function exec(fn) {
    _udf_script_engine_.evalScript(fn);
}

function log(s) {
    print(s + "\n");
}

$g = {};
$g.Client = function() {
    this.client = new Packages.cc.osint.graphd.client.ProtographClient("localhost", 10101);
    
    this.exec = function(cmd) {
        var results = this.client.exec(cmd + "\n");
        if (null == results) {
            log("no result!");
            return null;
        } else {
            if (results.size() == 0) {
                return true;
            } else {
                var c=0;
                var r = [];
                for(var i=0; i<results.length; i++) {
                    var result = results[i];
                    c++;
                    log.info(c + ": " + result.toString(4));
                    r.push(_JSONstring_to_js(result.toString(4)));
                }
                return r;
            }
        }
    };
    
    this.disconnect = function() {
        this.client.disconnect();
    };
};

$g.client = new $g.Client();

exec("udfs/js/lib/taffy.js");

log("vm_init.js ok");

