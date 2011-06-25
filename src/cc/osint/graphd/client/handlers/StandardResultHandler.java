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

package cc.osint.graphd.client.handlers;

import java.lang.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.apache.log4j.Logger;
import org.json.*;

public class StandardResultHandler extends ProtographClientResultHandler {
    static Logger log = Logger.getLogger(StandardResultHandler.class);
    
    private List<String> results;
    
    private boolean success;
    private String err;
    
    private long lastOpElapsed;
    
    public StandardResultHandler() {
        super();
        results = new ArrayList<String>();
        success = true;
        err = null;
        lastOpElapsed = 0;
    }
    
    public boolean isSuccessful() {
        return success;
    }     
    
    /*
     * poll, return null on no results
    */
    public List<String> pollResults() throws Exception {
        if (!isComplete()) return null;
        return results;
    }
    
    /*
     * block forever
    */
    public List<String> waitForResults() {
        return blockingGetResults(0);
    }
    
    /*
     * block for maxWaitMillis
    */
    public List<String> blockingGetResults(int maxWaitMillis) {
        long startTime = System.currentTimeMillis();
        while(!isComplete()) {
            long elapsed = System.currentTimeMillis() - 
                startTime;
            if (maxWaitMillis != 0 &&
                elapsed >= maxWaitMillis) {
                lastOpElapsed = elapsed;
                return null;
            }
        }
        lastOpElapsed = 
            System.currentTimeMillis() - startTime;
        return results;
    }
    
    public long getLastOpElapsedMillis() {
        return lastOpElapsed;
    }
    
    public void onResult(String result) {
        //log.info("onResult: " + result);
        results.add(result);
    }
    
    public void onComplete() {
        super.onComplete();
        //log.info("onComplete");
    }
    
    public void onError(String err) {
        super.onError(err);
        //log.info("onError: " + err);
        this.err = err;
        success = false;
    }
    
    @Override
    public void onException(Throwable throwable) {
        super.onException(throwable);
        success = false;
    }
}