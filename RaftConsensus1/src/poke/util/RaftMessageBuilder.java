package poke.util;

import poke.comm.Image.Header;
import poke.comm.Image.Request;

public class RaftMessageBuilder {

	public static Request buildIntraClusterImageMessage(Request req, boolean isLeader){
	Request.Builder reqBuilder = Request.newBuilder();
	reqBuilder.setPayload(req.getPayload());
	reqBuilder.setPing(req.getPing());
	
	Header.Builder headerBuilder = Header.newBuilder();
	headerBuilder.setCaption(req.getHeader().getCaption());
	headerBuilder.setClientId(req.getHeader().getClientId());
	headerBuilder.setClusterId(req.getHeader().getClusterId());
	headerBuilder.setIsClient(req.getHeader().getIsClient());
	headerBuilder.setIsLeader(isLeader);
	reqBuilder.setHeader(headerBuilder.build());
		return reqBuilder.build();
	}
}
