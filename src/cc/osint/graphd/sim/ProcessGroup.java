/*
 * Copyright 2011 John Muellerleile
 *
 * This file is licensed to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package cc.osint.graphd.sim;

import java.util.*;
import java.util.concurrent.*;
import java.lang.ref.*;
import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.jetlang.channels.*;
import org.jetlang.core.Callback;
import org.jetlang.core.Disposable;
import org.jetlang.core.Filter;
import org.jetlang.fibers.Fiber;
import org.jetlang.fibers.PoolFiberFactory;
import org.jetlang.fibers.ThreadFiber;
import cc.osint.graphd.graph.*;
import cc.osint.graphd.script.*;

public class ProcessGroup<T, M> {
    static Logger log = Logger.getLogger(ProcessGroup.class);
    
    private String name;
    private ConcurrentHashMap<String, GraphProcess<T, M>> processMap;
    private ExecutorService executorService;
    private PoolFiberFactory fiberFactory;
    private WeakReference<Graph> graphRef;
    private ConcurrentHashMap<String, GScriptEngine> scriptEngineMap;
    
    public ProcessGroup(Graph graph,
                        String name,
                        ExecutorService executorService,
                        PoolFiberFactory fiberFactory) {
        this.graphRef = new WeakReference<Graph>(graph);
        this.name = name;
        processMap = new ConcurrentHashMap<String, GraphProcess<T,M>>();
        this.executorService = executorService;
        this.fiberFactory = fiberFactory;
        scriptEngineMap = new ConcurrentHashMap<String, GScriptEngine>();
        log.info("process group instantiated: " + name);
    }
    
    public void start(String pid,
                      String name,
                      T context,
                      GraphProcess<T,M> graphProcess)
        throws Exception {
        Fiber fiber = fiberFactory.create();
        fiber.start();
        Channel<M> channel = new MemoryChannel<M>();
        graphProcess.setup(pid,
                           graphRef,
                           context,
                           name,
                           fiber,
                           channel);
        channel.subscribe(fiber, graphProcess);
        processMap.put(name, graphProcess);
    }
    
    public GraphProcess<T, M> getProcess(String name) throws Exception {
        return processMap.get(name);
    }
    
    public void kill(String name) throws Exception {
        processMap.get(name).kill();
        processMap.remove(name);
    }
    
    public synchronized void killAll() throws Exception {
        for(String name: processMap.keySet()) {
            kill(name);
        }
    }
    
    public Map<String, GraphProcess<T, M>> ps() throws Exception {
        return Collections.unmodifiableMap(processMap);
    }
    
    public void publish(String name, M msg) throws Exception {
        processMap.get(name).publish(msg);
    }
    
    public GScriptEngine getScriptEngine(String type) throws Exception {
        return getScriptEngine(type, null);
    }
    
    public GScriptEngine getScriptEngine(String type, String initFile) 
        throws Exception {
        GScriptEngine scriptEngine = scriptEngineMap.get(type);
        if (scriptEngine == null) {
            log.info("starting GScriptEngine(" + type + ")");
            scriptEngine = new GScriptEngine(name + "-" + type, type);
            if (initFile != null) {
                log.info("scriptEngine = " + scriptEngine);
                scriptEngine.getScriptEngine().put("_udf_script_engine_", scriptEngine);
                scriptEngine.evalScript(initFile);
            }
            scriptEngineMap.put(type, scriptEngine);
        }
        return scriptEngine;
    }
    
}