package cc.osint.graphd.client;

import java.lang.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

import static org.jboss.netty.channel.Channels.*;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.handler.codec.frame.DelimiterBasedFrameDecoder;
import org.jboss.netty.handler.codec.frame.Delimiters;
import org.jboss.netty.handler.codec.string.StringDecoder;
import org.jboss.netty.handler.codec.string.StringEncoder;

public class ProtographClientPipelineFactory implements
        ChannelPipelineFactory {

    protected ProtographClient client;
    
    public ProtographClientPipelineFactory(ProtographClient client) {
        super();
        this.client = client;
    }
    
    public ChannelPipeline getPipeline() throws Exception {
        ChannelPipeline pipeline = pipeline();
        pipeline.addLast("framer", new DelimiterBasedFrameDecoder(
                65535 * 2, Delimiters.lineDelimiter()));
        pipeline.addLast("decoder", new StringDecoder());
        pipeline.addLast("encoder", new StringEncoder());
        pipeline.addLast("handler", client.getProtographClientHandler());
        return pipeline;
    }
}
