java -XX:+UseConcMarkSweepGC -XX:+UseParNewGC -XX:CMSInitiatingOccupancyFraction=70 -d64 -server -Djava.library.path=/usr/local/lib -Xmx256m -Dfile.encoding=UTF-8 -cp .:./bin:lib/* cc.osint.graphd.server.websocket.WebSocketServer $*