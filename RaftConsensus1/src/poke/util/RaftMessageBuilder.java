package poke.util;

import poke.comm.App.ClientMessage;
import poke.comm.App.Header;
import poke.comm.App.Payload;
import poke.comm.App.Request;

public class RaftMessageBuilder {

	public static Request buildIntraClusterImageMessage(Request req, int nodeId){
	Request.Builder r = Request.newBuilder();
	
	Header.Builder h = Header.newBuilder();
	h.setOriginator(nodeId);
	h.setTag("Sending Image");
	h.setTime(System.currentTimeMillis());
	r.setHeader(h.build());
	
	Payload.Builder p = Payload.newBuilder();
	ClientMessage.Builder c = ClientMessage.newBuilder();
	c.setMsgId(req.getBody().getClientMessage().getMsgId());
	c.setMsgImageBits(req.getBody().getClientMessage().getMsgImageBits());
	c.setMsgImageName(req.getBody().getClientMessage().getMsgImageName());
	c.setIsClient(false);
	p.setClientMessage(c.build());
	r.setBody(p.build());
	
	return r.build();
	}
}
