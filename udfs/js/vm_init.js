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

exec("udfs/js/lib/taffy.js");

log("vm_init.js ok");

