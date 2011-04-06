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
    private String bootstrap = null;
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
    
    public void setBootstrap(String bs) {
        this.bootstrap = bs;
    }
    
    public String getBootstrap() {
        return this.bootstrap;
    }
    
    public Object eval(String s) throws Exception {
        return engine.eval(s);
    }
    
    public Object invoke(String fn, Object... args) throws Exception {
        //log.info("invoke(" + fn + ", " + args + ")");
        return invocableEngine.invokeFunction(fn, args);
    }
    
    public void bind(String var, Object val) throws Exception {
        engine.put(var, val);
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
    
    public void test() throws Exception {
        
        //
        // list available script engines
        //
        dumpScriptEngines();

        ScriptEngineManager manager = new ScriptEngineManager();
        
        //
        // quercus/php
        //
		
		/*
		log.info("\n* testing quercus/php");
		ScriptEngine engine = manager.getEngineByName("quercus");
		System.out.println("1 + 1: " + engine.eval("<?php return 1 + 1; ?>"));
		*/
				
		//
		// rhino/js
		//
		log.info("\n* testing rhino/js");
		engine = manager.getEngineByName("rhino");
		/*
		 * or, alternatively:
		 *    engine = manager.getEngineByExtension("js");
		 *
        	*/
		engine.eval("print('1 + 1: ' + (1+1));");
		
		//
		// define a function in a rhino/js engine &
		//  subsequently call it from java code
		//
		log.info("\n* testing invocableEngine with rhino/js");
        engine.eval("function twogirls() {\n" +
                "\tprint('from javascript: twogirls() -> One cup!');\n" +
                "}\n");
        Invocable invocableEngine = (Invocable) engine;
        invocableEngine.invokeFunction("twogirls");
        
        List<String> namesList = new ArrayList<String>();
        namesList.add("Zed");
        
        engine.eval("function printNames1(namesList) {" +
                  "  var x;" +
                  "  var names = namesList.toArray();" +
                  "  for(x in names) {" +
                  "    print(names[x] + '\\n');" +
                  "  }" +
                  "}" +

                  "function addName(namesList, name) {" +
                  "  namesList.add(name);" +
                  "}");
        
        log.info("\n>> calling printNames1(namesList) via invokeFunction: ");
        invocableEngine.invokeFunction("printNames1", namesList);
        
        log.info("\n>> calling addName(namesList, 'Dirk Diggler') via invokeFunction: ");
        invocableEngine.invokeFunction("addName", namesList, "Dirk Diggler");
        
        log.info("\n>> calling printNames1(namesList) via invokeFunction: ");
        invocableEngine.invokeFunction("printNames1", namesList);
        
        //
        // read javascript from a file & execute it
        //
        /*
        log.info("\n* running file (resource) /vm/bootstrap.js as a stream w/ rhino/js");
        test_runjs("/vm/bootstrap.js");
        */
        
        //
        // access java objects from script
        //
        log.info("\n* create a java list and access it from script, iterating / printing it, then adding one");
        namesList = new ArrayList<String>();
        namesList.add("Jill");
        namesList.add("Bob");
        namesList.add("Laureen");
        namesList.add("Ed");
        
        engine.put("namesListKey", namesList);
        engine.eval("var x;" +
                  "var names = namesListKey.toArray();" +
                  "for(x in names) {" +
                  "  print('from script: ' + names[x] + '\\n');" +
                  "}" +
                  "namesListKey.add(\"Dana\");");
        
        log.info("\n* print the now modified list from java:");
        for (String name: namesList) {
            System.out.println("from java: " + name);
        }
    }

    public void test_runjs(String fn) throws Exception {
        ScriptEngineManager engineMgr = new ScriptEngineManager();
        ScriptEngine engine = engineMgr.getEngineByName("js");
        InputStream is = 
            this.getClass().getResourceAsStream(fn);
        try {
            Reader reader = new InputStreamReader(is);
            engine.eval(reader);
        } catch (ScriptException ex) {
            ex.printStackTrace();
        }
    }
}

