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
import org.json.*;

public abstract class ProtographClientResultHandler {
    Throwable throwable = null;
    private AtomicBoolean hasExecuted;

    public abstract void onResult(String result);
    
    public void onError(String err) {
        this.throwable = new Exception(err);
    }
    
    public ProtographClientResultHandler() {
        hasExecuted = new AtomicBoolean(false);
    }
    
    public void onComplete() {
        hasExecuted.set(true);
    }

    public boolean isComplete() {
        return hasExecuted.get();
    }
    
    public void onException(Throwable throwable) {
        this.throwable = throwable;
        throwable.printStackTrace();
    }
    
    public Throwable getException() {
        return this.throwable;
    }
}