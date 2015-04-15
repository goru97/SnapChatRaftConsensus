package poke.server.resources;

import poke.comm.Image.Request;
import poke.server.queue.PerChannelQueue;

public interface ImgResource {
	Request process(Request request);
	void setChannel(PerChannelQueue channel);
}
