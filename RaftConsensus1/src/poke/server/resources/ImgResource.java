package poke.server.resources;

import poke.comm.App.Request;
import poke.server.conf.ServerConf;
import poke.server.queue.PerChannelQueue;

public interface ImgResource {
	Request process(Request request);
	void setChannel(PerChannelQueue channel);
	void setConf(ServerConf conf);
}
