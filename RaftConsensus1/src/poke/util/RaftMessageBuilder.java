package poke.util;

import io.netty.channel.Channel;
import poke.comm.App.ClientMessage;
import poke.comm.App.Header;
import poke.comm.App.Payload;
import poke.comm.App.Request;
import poke.core.Mgmt.CompleteRaftMessage;
import poke.core.Mgmt.Management;
import poke.core.Mgmt.MgmtHeader;
import poke.core.Mgmt.RequestVoteMessage;
import poke.core.Mgmt.CompleteRaftMessage.ElectionAction;
import poke.server.managers.CompleteRaftManager;
import poke.server.managers.ConnectionManager;

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

	public static Management buildRequestVote(int nodeId,int currentTerm){
		RequestVoteMessage.Builder reqVoteBuilder = RequestVoteMessage.newBuilder();
		reqVoteBuilder.setCandidateId(nodeId);

		CompleteRaftMessage.Builder raftMsgbuilder = CompleteRaftMessage.newBuilder();
		raftMsgbuilder.setAction(ElectionAction.REQUESTVOTE).setTerm(currentTerm).setRequestVote(reqVoteBuilder.build());

		Management.Builder mgmtBuilder = Management.newBuilder();

		MgmtHeader header = mgmtBuilder.getHeader();
		MgmtHeader.Builder mgmtHeaderBuilder = MgmtHeader.newBuilder();
		mgmtHeaderBuilder.setOriginator(nodeId);

		mgmtBuilder.setHeader(mgmtHeaderBuilder.build());
		mgmtBuilder.setRaftMessage(raftMsgbuilder.build());
		return mgmtBuilder.build();
	}

	public static Management buildVoteMessage(int nodeId, int currentTerm){
		Management.Builder mgmtBuilder = Management.newBuilder();
		MgmtHeader.Builder mgmtHeaderBuilder = MgmtHeader.newBuilder();
		mgmtHeaderBuilder.setOriginator(nodeId); 
		CompleteRaftMessage.Builder raftMsgBuilder= CompleteRaftMessage.newBuilder();
		raftMsgBuilder.setTerm(currentTerm)
		.setAction(ElectionAction.VOTE); //setting action so that candidate can use it appropriately.
		Management finalMsg = mgmtBuilder.setHeader(mgmtHeaderBuilder.build()).setRaftMessage(raftMsgBuilder.build()).build();
		return finalMsg;
	}
	
	public static Management buildAppendMessage(int nodeId){
		Management.Builder mgmtBuilder = Management.newBuilder();

		MgmtHeader.Builder mgmtHeaderBuilder = MgmtHeader.newBuilder();
		mgmtHeaderBuilder.setOriginator(nodeId);

		CompleteRaftMessage.Builder raftMsgBuilder = CompleteRaftMessage.newBuilder();
		//	raftMsgBuilder.setAction(ElectionAction.LEADER);

		raftMsgBuilder.setTerm(CompleteRaftManager.getInstance().getCurrentTerm()).setAction(ElectionAction.APPEND);

		Management mgmt = mgmtBuilder.setHeader(mgmtHeaderBuilder.build())
				.setRaftMessage(raftMsgBuilder.build()).build();
		return mgmt;
	}
}
