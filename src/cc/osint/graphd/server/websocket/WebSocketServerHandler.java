package cc.osint.graphd.server.websocket;

import static org.jboss.netty.handler.codec.http.HttpHeaders.*;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.*;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Values.*;
import static org.jboss.netty.handler.codec.http.HttpMethod.*;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.*;
import static org.jboss.netty.handler.codec.http.HttpVersion.*;
import java.security.MessageDigest;
import java.util.logging.*;
import java.util.*;
import java.util.concurrent.*;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ChannelState;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.jboss.netty.handler.codec.http.HttpHeaders.Values;
import org.jboss.netty.handler.codec.http.websocket.DefaultWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocket.WebSocketFrame;
import org.jboss.netty.handler.codec.http.websocket.WebSocketFrameDecoder;
import org.jboss.netty.handler.codec.http.websocket.WebSocketFrameEncoder;
import org.jboss.netty.util.CharsetUtil;
import org.json.*;
import cc.osint.graphd.util.*;
import cc.osint.graphd.client.*;
import cc.osint.graphd.client.handlers.*;

public class WebSocketServerHandler 
    extends SimpleChannelUpstreamHandler {
    
    private static final Logger log = Logger.getLogger(WebSocketServerHandler.class.getName());
    private static final String WEBSOCKET_PATH = "/websocket";
    
    static ConcurrentHashMap<String, ProtographClient> clientMap;
    static {
        clientMap = new ConcurrentHashMap<String, ProtographClient>();
    }
    
    class ClientCmd {
        protected ChannelHandlerContext ctx;
        protected String cmd;
    }
    
    static Runnable runnable;
    static Thread clientCommandThread;
    static LinkedBlockingQueue<ClientCmd> cmdQueue;
    static {
        cmdQueue = new LinkedBlockingQueue<ClientCmd>();
        runnable = new Runnable() {
            public void run() {
                try {
                    while(true) {
                        try {
                            ClientCmd cmd = cmdQueue.take();
                            log.info("clientCmd [" + cmd.ctx + "]: " + cmd.cmd);

                            String clientId = "" + cmd.ctx.getChannel().getId();
                            if (null == clientMap.get(clientId)) {
                                clientMap.put(clientId, 
                                              new ProtographClient("localhost", 
                                                                   10101,
                                                                   new WebsocketEventHandler(cmd.ctx)));
                            }
                            ProtographClient client = clientMap.get(clientId);
                            
                            List<JSONObject> results = client.exec(cmd.cmd.trim() + "\n");
                            if (null == results) {
                                send(cmd.ctx, "no result!");
                            } else {
                                if (results.size() == 0) {
                                    send(cmd.ctx, "OK");
                                } else {
                                    int c=0;
                                    for(JSONObject result: results) {
                                        c++;
                                        send(cmd.ctx, c + ": " + result.toString(4));
                                    }
                                }
                            }
                        } catch (Exception clex) {
                            clex.printStackTrace();
                        }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        };
        clientCommandThread = new Thread(runnable);
        clientCommandThread.start();
    }
    
    @Override
    public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e) 
        throws Exception {
        if (e instanceof ChannelStateEvent) {
            String clientId = "" + e.getChannel().getId();
            ChannelState state = ((ChannelStateEvent)e).getState();
            if (state == state.CONNECTED &&
                ((ChannelStateEvent)e).getValue() == null) {
                
                // TODO: send client disconnection messages to any related
                //       or interested parties (processes, watched objects, 
                //       watching objects, etc.)
                
                ProtographClient cl = clientMap.get(clientId);
                if (null != cl) {
                    cl.disconnect();
                }
                clientMap.remove(clientId);
                log.info("WEBSOCKETS: DISCONNECTED: " + clientId);
            } else {
                log.info("WEBSOCKETS: NETTY: handleUpstream: " + e.toString());
            }
        }
        super.handleUpstream(ctx, e);
    }
    
    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        Object msg = e.getMessage();
        if (msg instanceof HttpRequest) {
            handleHttpRequest(ctx, (HttpRequest) msg);
        } else if (msg instanceof WebSocketFrame) {
            handleWebSocketFrame(ctx, (WebSocketFrame) msg);
        }
    }

    private void handleHttpRequest(ChannelHandlerContext ctx, HttpRequest req) throws Exception {
        // Allow only GET methods.
        if (req.getMethod() != GET) {
            sendHttpResponse(
                    ctx, req, new DefaultHttpResponse(HTTP_1_1, FORBIDDEN));
            return;
        }

        log.info("req.getUri() = " + req.getUri());
        log.info("getWebSocketLocation(req) = " + getWebSocketLocation(req));
        if (req.getUri().startsWith("/app/")) {
            HttpResponse res = new DefaultHttpResponse(HTTP_1_1, OK);
            String fn = req.getUri().replaceAll("\\/app\\/", "");
            log.info("fn = " + fn);
            if (fn.indexOf("..")!=-1) throw new Exception ("illegal");
            if (fn.indexOf("~")!=-1) throw new Exception ("illegal");
            if (fn.indexOf("/")!=-1) throw new Exception ("illegal");
            fn = "./www/" + fn;
            log.info("fn [2] = " + fn);
            ChannelBuffer content = 
                ChannelBuffers.copiedBuffer(TextFile.get(fn), CharsetUtil.US_ASCII);
            res.setHeader(CONTENT_TYPE, "text/html; charset=UTF-8");
            setContentLength(res, content.readableBytes());
            res.setContent(content);
            sendHttpResponse(ctx, req, res);
            return;
        }

        // Serve the WebSocket handshake request.
        if (req.getUri().equals(WEBSOCKET_PATH) &&
            Values.UPGRADE.equalsIgnoreCase(req.getHeader(CONNECTION)) &&
            WEBSOCKET.equalsIgnoreCase(req.getHeader(Names.UPGRADE))) {

            // Create the WebSocket handshake response.
            HttpResponse res = new DefaultHttpResponse(
                    HTTP_1_1,
                    new HttpResponseStatus(101, "Web Socket Protocol Handshake"));
            res.addHeader(Names.UPGRADE, WEBSOCKET);
            res.addHeader(CONNECTION, Values.UPGRADE);

            // Fill in the headers and contents depending on handshake method.
            if (req.containsHeader(SEC_WEBSOCKET_KEY1) &&
                req.containsHeader(SEC_WEBSOCKET_KEY2)) {
                // New handshake method with a challenge:
                res.addHeader(SEC_WEBSOCKET_ORIGIN, req.getHeader(ORIGIN));
                res.addHeader(SEC_WEBSOCKET_LOCATION, getWebSocketLocation(req));
                String protocol = req.getHeader(SEC_WEBSOCKET_PROTOCOL);
                if (protocol != null) {
                    res.addHeader(SEC_WEBSOCKET_PROTOCOL, protocol);
                }

                // Calculate the answer of the challenge.
                String key1 = req.getHeader(SEC_WEBSOCKET_KEY1);
                String key2 = req.getHeader(SEC_WEBSOCKET_KEY2);
                int a = (int) (Long.parseLong(key1.replaceAll("[^0-9]", "")) / key1.replaceAll("[^ ]", "").length());
                int b = (int) (Long.parseLong(key2.replaceAll("[^0-9]", "")) / key2.replaceAll("[^ ]", "").length());
                long c = req.getContent().readLong();
                ChannelBuffer input = ChannelBuffers.buffer(16);
                input.writeInt(a);
                input.writeInt(b);
                input.writeLong(c);
                ChannelBuffer output = ChannelBuffers.wrappedBuffer(
                        MessageDigest.getInstance("MD5").digest(input.array()));
                res.setContent(output);
            } else {
                // Old handshake method with no challenge:
                res.addHeader(WEBSOCKET_ORIGIN, req.getHeader(ORIGIN));
                res.addHeader(WEBSOCKET_LOCATION, getWebSocketLocation(req));
                String protocol = req.getHeader(WEBSOCKET_PROTOCOL);
                if (protocol != null) {
                    res.addHeader(WEBSOCKET_PROTOCOL, protocol);
                }
            }
            
            // Upgrade the connection and send the handshake response.
            ChannelPipeline p = ctx.getChannel().getPipeline();
            p.remove("aggregator");
            p.replace("decoder", "wsdecoder", new WebSocketFrameDecoder());

            ctx.getChannel().write(res);

            p.replace("encoder", "wsencoder", new WebSocketFrameEncoder());
            return;
        }

        // Send an error page otherwise.
        sendHttpResponse(
                ctx, req, new DefaultHttpResponse(HTTP_1_1, FORBIDDEN));
    }

    private void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
        try {
            // Send the uppercased string back.
            String request = frame.getTextData();
            log.info("WebSocketServerHandler: handleWebSocketFrame: request = " + request);
            ClientCmd cmd = new ClientCmd();
            cmd.ctx = ctx;
            cmd.cmd = request;
            cmdQueue.put(cmd);
            /*
            ctx.getChannel().write(
                    new DefaultWebSocketFrame(frame.getTextData().toUpperCase()));
            */
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    private static void send(ChannelHandlerContext ctx, String msg) throws Exception {
        ctx.getChannel().write(new DefaultWebSocketFrame(msg));
    }

    private void sendHttpResponse(ChannelHandlerContext ctx, HttpRequest req, HttpResponse res) {
        // Generate an error page if response status code is not OK (200).
        if (res.getStatus().getCode() != 200) {
            res.setContent(
                    ChannelBuffers.copiedBuffer(
                            res.getStatus().toString(), CharsetUtil.UTF_8));
            setContentLength(res, res.getContent().readableBytes());
        }

        // Send the response and close the connection if necessary.
        ChannelFuture f = ctx.getChannel().write(res);
        if (!isKeepAlive(req) || res.getStatus().getCode() != 200) {
            f.addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
            throws Exception {
        e.getCause().printStackTrace();
        e.getChannel().close();
    }

    private String getWebSocketLocation(HttpRequest req) {
        return "ws://" + req.getHeader(HttpHeaders.Names.HOST) + WEBSOCKET_PATH;
    }
}
