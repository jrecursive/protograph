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

package cc.osint.graphd.server;

import java.lang.*;
import java.lang.ref.*;
import java.util.*;
import java.util.concurrent.*;
import org.jboss.netty.channel.Channel;
import cc.osint.graphd.processes.*;

public class GraphCommand {
    
    // command execution parameters:
    
    protected Channel responseChannel = null;
    protected String clientId = null;
    protected ConcurrentHashMap<String, String> clientState = null;
    protected InboundChannelProcess inboundChannelProcess = null;
    protected String request = null;
    protected String cmd = null;
    protected String[] args = null;
    
    // system use only:
    
    protected boolean poisonPill = false;

}