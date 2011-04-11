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