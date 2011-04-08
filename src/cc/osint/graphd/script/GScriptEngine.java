package cc.osint.graphd.script;

import java.io.*;
import java.lang.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import javax.script.*;
import org.apache.log4j.Logger;
import org.json.*;
import cc.osint.graphd.util.*;

public class GScriptEngine {
    static Logger log = Logger.getLogger(GScriptEngine.class);
        
    private String vmName = "anonymous";
    private String vmType = null;
    private boolean onlyEval = false;
    
    ScriptEngineManager manager = null;
    ScriptEngine engine = null;
    Invocable invocableEngine = null;
    
	public GScriptEngine(String vm, String vm_type) throws Exception {
        this.vmName = vm;
        this.vmType = vm_type;
        log.info("new GScriptEngine(" + vm + ", " + vm_type + ")");
        manager = new ScriptEngineManager();
        engine = manager.getEngineByName(this.vmType);
        try {
            invocableEngine = (Invocable) engine;
            onlyEval = false;
        } catch(Exception ex) {
            ex.printStackTrace();
            invocableEngine = null;
            onlyEval = true;
        }
    }
    
    public Object eval(String s) throws Exception {
        return engine.eval(s);
    }
    
    public Object invoke(String fn, Object... args) throws Exception {
        return invocableEngine.invokeFunction(fn, args);
    }
    
    public Object invokeMethod(Object instance, String fn, Object... args) 
        throws Exception {
        return invocableEngine.invokeMethod(instance, fn, args);
    }
    
    public void put(String var, Object val) throws Exception {
        engine.put(var, val);
    }
    
    public Object get(String var) throws Exception {
        return engine.get(var);
    }
    
    public Object evalScript(String fn) throws Exception {
        File file = new File(fn);
        return engine.eval(TextFile.get(file.getCanonicalPath()));
    }

    public String getVMName() {
        return this.vmName; 
    }
	
	public String getVMType() {
	   return this.vmType;
    }
    
    public ScriptEngine getScriptEngine() {
        return engine;
    }
	
    public void dumpScriptEngines() throws Exception {
        ScriptEngineManager mgr = new ScriptEngineManager();
        List<ScriptEngineFactory> factories = 
            mgr.getEngineFactories();
        
        for (ScriptEngineFactory factory: factories) {
            log.info("ScriptEngineFactory Info");
            String engName = factory.getEngineName();
            String engVersion = factory.getEngineVersion();
            String langName = factory.getLanguageName();
            String langVersion = factory.getLanguageVersion();
            
            System.out.printf("\tScript Engine: %s (%s)\n", 
                engName, engVersion);
            
            List<String> engNames = factory.getNames();
            for(String name: engNames) {
                System.out.printf("\tEngine Alias: %s\n", name);
            }
            
            System.out.printf("\tLanguage: %s (%s)\n", 
                langName, langVersion);
        }
    }
}

