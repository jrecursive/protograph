/*
 * Copyright 2010 John Muellerleile
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

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;

public class GraphServer {
    final private static int GRAPHD_PORT = 10101;

    public static void main(String[] args) throws Exception {
        // Configure the server.
        
        // CONFIGURATION FILE
        
        ServerBootstrap bootstrap = new ServerBootstrap(
                new NioServerSocketChannelFactory(
                        Executors.newCachedThreadPool(),
                        Executors.newCachedThreadPool()));

        GraphServerHandler handler = new GraphServerHandler();
        bootstrap.setPipelineFactory(new GraphServerPipelineFactory(handler));

        // Bind and start to accept incoming connections.
        bootstrap.bind(new InetSocketAddress(GRAPHD_PORT));
    }
}
