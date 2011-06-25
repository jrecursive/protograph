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
import org.apache.log4j.Logger;
import org.json.*;

public class EchoEventHandler extends ProtographClientEventHandler {
    static Logger log = Logger.getLogger(EchoEventHandler.class);
    
    public void onStatusEvent(String status) {
        log.info("onGenericEvent: " + status);
    }
    
    public void onMessageEvent(String channel, String message) {
        try {
            JSONObject obj = new JSONObject(message);
            log.info("onMessageEvent: <" + channel + "> " + obj.toString(4));
            //log.info("onMessageEvent: <" + channel + "> " + message);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    @Override
    public void onProtographEventException(Throwable throwable) {
        throwable.printStackTrace();
    }
    
}