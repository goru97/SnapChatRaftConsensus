package poke.server.resources;

import poke.comm.Image.Request;

public interface ImgResource {
	Request process(Request request);
}
