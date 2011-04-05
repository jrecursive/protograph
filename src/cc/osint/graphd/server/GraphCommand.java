package cc.osint.graphd.server;

import java.lang.*;
import java.util.*;
import java.util.concurrent.*;
import org.jboss.netty.channel.Channel;

public class GraphCommand {
    
    // command execution parameters:
    
    protected Channel responseChannel = null;
    protected String clientId = null;
    protected ConcurrentHashMap<String, String> clientState = null;
    protected String request = null;
    protected String cmd = null;
    protected String[] args = null;
    
    // system use only:
    
    protected boolean poisonPill = false;

}