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