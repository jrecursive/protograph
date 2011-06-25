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
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.http.websocket.DefaultWebSocketFrame;

public class WebsocketEventHandler extends ProtographClientEventHandler {
    static Logger log = Logger.getLogger(EchoEventHandler.class);
    ChannelHandlerContext ctx;

    public WebsocketEventHandler(ChannelHandlerContext ctx) {
        this.ctx = ctx;
    }
    
    public void onStatusEvent(String status) {
        try {
            ctx.getChannel().write(new DefaultWebSocketFrame("# " + status));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    public void onMessageEvent(String channel, String message) {
        try {
            JSONObject obj = new JSONObject(message);
            ctx.getChannel().write(new DefaultWebSocketFrame("! " + channel + " " + message.toString().trim()));
            log.info("onMessageEvent: <" + channel + "> " + obj.toString(4));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    @Override
    public void onProtographEventException(Throwable throwable) {
        try {
            ctx.getChannel().write(new DefaultWebSocketFrame("X " + throwable.getMessage()));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        throwable.printStackTrace();
    }
    
}