package cc.osint.graphd.client;

import java.lang.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import java.util.logging.Level;
import java.util.logging.Logger;
import jline.*;

import cc.osint.graphd.client.handlers.*;

public class ProtographClient {
    private static final Logger log = Logger.getLogger(
            ProtographClient.class.getName());
    
    private String host;
    private int port;
    private boolean connected;
    private boolean shutdown; 
    
    private ClientBootstrap bootstrap;
    private Channel channel;
    private ChannelFuture lastWriteFuture;

    private ProtographClientHandler protographClientHandler;
    private ProtographClientEventHandler eventHandler;

    public ProtographClient(String host, int port) 
        throws Exception {
        this(host, port, null);
    }
    
    public ProtographClient(String host, 
                            int port, 
                            ProtographClientEventHandler eventHandler) 
        throws Exception {
        this.host = host;
        this.port = port;
        connected = false;
        shutdown = false;
        bootstrap = null;
        channel = null;
        lastWriteFuture = null;
        this.eventHandler = eventHandler;
        ProtographClientHandler protographClientHandler = 
            new ProtographClientHandler(this);
        protographClientHandler.setProtographClient(this);
        clientCommandQueue = new LinkedBlockingQueue<ClientCommand>();
        connect();
    }
    
    public boolean connect() throws Exception {
        bootstrap = new ClientBootstrap(
                new NioClientSocketChannelFactory(
                        Executors.newCachedThreadPool(),
                        Executors.newCachedThreadPool()));
        ProtographClientPipelineFactory pipelineFactory = 
            new ProtographClientPipelineFactory(this);
        bootstrap.setPipelineFactory(pipelineFactory);
        
        ChannelFuture future = 
            bootstrap.connect(new InetSocketAddress(host, port));
        channel = future.awaitUninterruptibly().getChannel();
        
        if (!future.isSuccess()) {
            connected = false;
            future.getCause().printStackTrace();
            bootstrap.releaseExternalResources();
            log.info("Connection failure: " + host + ":" + port);
        } else {
            connected = true;
            log.info("Connection success: " + host + ":" + port);
        }
        return connected;
    }
    
    public void disconnect() throws Exception {
        /*
        //waitForFlush();
        if (lastWriteFuture != null)
            lastWriteFuture.awaitUninterruptibly();
        */
        shutdown = true;
        // TODO: proper shutdown sequence
        // wait for queue clear/stop
        // wait for flush
        // continue
        channel.getCloseFuture().awaitUninterruptibly();
        bootstrap.releaseExternalResources();
        shutdown = true;
    }
    
    public boolean isConnected() {
        return connected;
    }
    
    protected Channel getChannel() {
        return channel;
    }
    
    protected ProtographClientHandler getProtographClientHandler() {
        if (null == protographClientHandler) {
            protographClientHandler = 
                new ProtographClientHandler(this);
        }
        return protographClientHandler;
    }
    
    protected ProtographClientEventHandler getEventHandler() {
        return eventHandler;
    }
    
    private void send(String str) throws Exception {
        send(str, false);
    }
    
    private void send(String str, boolean waitForFlush) throws Exception {
        lastWriteFuture = channel.write(str);
        if (waitForFlush) lastWriteFuture.awaitUninterruptibly();
    }

    /*
     * async command processing thread
    */
    
    class ClientCommand {
        private String command;
        private ProtographClientResultHandler handler;
        
        public ClientCommand(String command,
                             ProtographClientResultHandler handler) {
            this.command = command;
            this.handler = handler;
        }
        
        public String getCommand() { return command; }
        public ProtographClientResultHandler getHandler() {
            return handler;
        }
    }
    final LinkedBlockingQueue<ClientCommand> clientCommandQueue;
    
    public void run() {
        try {
            while(true) {
                try {
                    ClientCommand clientCommand =
                        clientCommandQueue.take();
                    protographClientHandler.setResultHandler(
                        clientCommand.getHandler());
                    send(clientCommand.getCommand(), true);
                    while(!clientCommand.getHandler().isComplete());
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        } catch (Exception ex1) {
            ex1.printStackTrace();
        }
    }

    /*
     * send a raw command and block until a result
    */
    public List<JSONObject> exec(String str) throws Exception {
        StandardResultHandler resultHandler = 
            new StandardResultHandler();
        exec(str, resultHandler);
        List<String> resultList = resultHandler.waitForResults();
        if (resultHandler.isSuccessful()) {
            return interpretResults(resultList);
        } else {
            throw new Exception(resultHandler.getException());
        }
    }
    
    /*
     * send a raw command and do not block until a result
    */
    public void exec(String str, 
                     ProtographClientResultHandler resultHandler) 
        throws Exception {
        clientCommandQueue.put(new ClientCommand(str, resultHandler));
    }
            
    public static List<JSONObject> interpretResults(List<String> resultList) 
        throws Exception {
        List<JSONObject> res = new ArrayList<JSONObject>();
        if (resultList.size() == 0) {
            return res;
        } else {
            for(String str: resultList) {
                if (str.charAt(0) == '{') {
                    res.add(new JSONObject(str));
                } else if (str.charAt(0) == '[') {
                    JSONObject result = new JSONObject();
                    JSONArray resultArray = new JSONArray(str);
                    result.put("results", resultArray);
                    res.add(result);
                } else {
                    JSONObject result = new JSONObject();
                    result.put("value", str);
                    res.add(result);
                }
            }
        }
        return res;
    }
    
    /*
     * console client interface
    */

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println(
                    "Usage: " + ProtographClient.class.getSimpleName() +
                    " <host> <port>");
            return;
        }
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        ProtographClient client = new ProtographClient(host, port);
        
        // Read commands from the stdin.
        ConsoleReader reader = new ConsoleReader();
        for (;;) {
            String line = reader.readLine();
            if (line == null) {
                break;
            }
            
            // Sends the received line to the server.
            //client.send(line + "\r\n");
            List<JSONObject> results = client.exec(line.trim());
            for(JSONObject result: results) {
                log.info(result.toString(4));
            }

            // If user typed the 'bye' command, wait until the server closes
            // the connection.
            if (line.toLowerCase().equals("bye")) {
                client.disconnect();
                break;
            }
        }
        client.disconnect();
    }
}
