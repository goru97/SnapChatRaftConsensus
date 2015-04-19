/*
 * copyright 2015, gaurav bajaj
 * 
 * Gaurav Bajaj licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package poke.server.managers;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import poke.comm.App.JoinMessage;
import poke.comm.App.Request;
import poke.core.Mgmt.CompleteRaftMessage;
import poke.core.Mgmt.CompleteRaftMessage.ElectionAction;
import poke.core.Mgmt.Management;
import poke.core.Mgmt.MgmtHeader;
import poke.core.Mgmt.RequestVoteMessage;
import poke.server.ServerInitializer;
import poke.server.conf.ClusterConf;
import poke.server.conf.ServerConf;
import poke.server.conf.ClusterConf.Cluster;
import poke.server.conf.ClusterConf.ClusterNode;
import poke.server.resources.ResourceFactory;
import poke.util.RaftMessageBuilder;

public class CompleteRaftManager {
	protected static Logger logger = LoggerFactory.getLogger("election");
	protected static AtomicReference<CompleteRaftManager> instance = new AtomicReference<CompleteRaftManager>();

	private static ServerConf conf;
	private static ClusterConf clusterConf;
	private int currentTerm=0; //leader’s term
	private int leaderId=-1;
	private int votedFor=-1;
	private int voteCount=0;
	private int candidateId;
	private State state= State.FOLLOWER; //state of the node: Follower, Candidate, Leader
	private Timer electionTimeout = new Timer();
    private RaftHeartMonitor raftMonitor;
	//The system must remain in one of these three states
	public  enum State {
		FOLLOWER, CANDIDATE, LEADER
	}

	
	public static ServerConf getConf(){
		return conf;
	}
	public static ClusterConf getClusterConf(){
		return clusterConf;
	}
	public static CompleteRaftManager initManager(ServerConf conf, ClusterConf clusterConf) {
		CompleteRaftManager.clusterConf = clusterConf;
		CompleteRaftManager.conf = conf;
		instance.compareAndSet(null, new CompleteRaftManager());
		return instance.get();
	}

	public static CompleteRaftManager getInstance() {
		// TODO throw exception if not initialized!
		return instance.get();
	}

	//for internal replication use
	public String getCurrentState(){
		return this.state.toString();
	}
	
	public int getCurrentTerm(){
		return currentTerm;
	}
	
	public int getLeaderId(){
		return this.leaderId;
	}
	
	//Reset it to follower state
	
	private void resetNode(){

		System.out.println("Resetting Node");
		this.state=State.FOLLOWER;
		this.leaderId=-1;
		this.votedFor=-1;
		this.voteCount=0;
		if(raftMonitor!=null)
			raftMonitor.setLeader(false);
		raftMonitor = null;
	}

	//Vote the candidate
	private void sendVoteNotice(Management mgmt){


		int destinationId = mgmt.getHeader().getOriginator();
		int term = mgmt.getRaftMessage().getTerm();
		if(this.currentTerm < term){
			this.currentTerm = term;

			Channel candidateChannel = ConnectionManager.getConnection(destinationId, true);	
			System.out.println("Sending Vote Notice to NodeId --> "+destinationId);
			candidateChannel.writeAndFlush(RaftMessageBuilder.buildVoteMessage(conf.getNodeId(), currentTerm));
			electionTimeout.cancel();
			electionTimeout = new Timer();
			electionTimeout.schedule (new TimerTask() {

				@Override
				public void run() {
					System.out.println("Append not called by leader. Re-election!!!");
					resetNode();
					startElection();

				}
			}, getRandomElectionTimeOut());

		}

		//if(this.currentTerm > term) implement this scenario to make our raft 
		//partition tolerant
	}

	private int getRandomElectionTimeOut(){
		int randomTimeOut = new Random().nextInt(10000 - 5000 + 1) + 5000;
		return randomTimeOut;
	}

	//Prepare Raft Message for Voting
	private void sendRequestVote() {
		ConnectionManager.broadcastAndFlush(RaftMessageBuilder.buildRequestVote(conf.getNodeId(), currentTerm));
		System.out.println("Node "+conf.getNodeId()+" became candidate and sending requests!");
	}

	private boolean isLeader() {
		if((voteCount>((ConnectionManager.getNumMgmtConnections())/2)))
			return true;
		else
			return false;
	}


	//Start election
	private void startElection() {
		state=State.CANDIDATE;

		//candidate will vote for itself and then request for votes
		currentTerm++;
		voteCount++;
		//can vote only once in a term.
		if(this.votedFor==-1){
			votedFor=conf.getNodeId();
			//request for votes
			sendRequestVote();
		}

	}


	

	// Time to celebrate on becoming new Leader
	private void sendLeaderNotice()  {

// Start sending append notices
		raftMonitor = new RaftHeartMonitor();
		raftMonitor.start();
//Start cluster join messages
		Thread c = new ClusterConnectionManager(conf, ResourceFactory.getInstance().getClusterConf());
		c.start();
		
	}

//Start the raft consensus process
	public void startMyRaft(){
		resetNode();
		electionTimeout.cancel();
		electionTimeout = new Timer();
		electionTimeout.schedule(
				new TimerTask() {

					@Override
					public void run() {
						System.out.println("Starting raft with election");
						startElection();

					}
				}

				, getRandomElectionTimeOut());

	}

	public void processRequest(Management mgmt) {

		//	if (!mgmt.hasRaftMessage())
		//	return;

		CompleteRaftMessage req = mgmt.getRaftMessage();

		//When another node sends a CompleteRaftMessage, the manager will check for 
		//its term

		/*	if(req.hasTerm()){
			if(req.getTerm() > this.currentTerm)
				this.currentTerm = req.getTerm();
		}
		 */

		int electionActionVal = req.getAction().getNumber();
	
		switch (electionActionVal) {

		case ElectionAction.APPEND_VALUE:
			
			if(this.currentTerm < req.getTerm()){
				this.currentTerm = req.getTerm();
				this.leaderId = mgmt.getHeader().getOriginator();
				resetNode();
			}

		else{
			if(leaderId!=conf.getNodeId()){
				state=State.FOLLOWER;
				leaderId=mgmt.getHeader().getOriginator();
				currentTerm=mgmt.getRaftMessage().getTerm();
				votedFor=-1;
				//reset timer else call for election
				if(electionTimeout!=null)
				electionTimeout.cancel();
				electionTimeout=new Timer();
				electionTimeout.schedule (new TimerTask() {

					@Override
					public void run() {
						System.out.println("Append not called by leader. Re-election!!! ");
						resetNode();
						startElection();

					}
				}, getRandomElectionTimeOut());
			}
			System.out.println("Receiving Append Messages from ***Leader ID --> "+leaderId+"***");
		}
			break;

		case ElectionAction.REQUESTVOTE_VALUE:

			//send vote to the originator
			//System.out.println("Voted For before: "+votedFor+" term: "+msg.getTerm());
			if(this.votedFor == -1){
				votedFor=mgmt.getHeader().getOriginator();
				sendVoteNotice(mgmt);
				//System.out.println("Voted for: "+votedFor);
			}
			break;
		case ElectionAction.VOTE_VALUE:
			voteCount++;
			votedFor=-1;
//			System.out.println("Leader --> "+isLeader());
			if(isLeader()){
				//currentTerm=mgmt.getRaftMessage().getTerm();
				state=State.LEADER;
				leaderId=conf.getNodeId();	
				sendLeaderNotice();
				votedFor=-1;
				voteCount=0;
				System.out.println("Node "+leaderId+" is the leader!");	
				//leaderId=-1;
				electionTimeout.cancel();
				electionTimeout=new Timer();
			}
			break;

		default:
			break;

		}

	}
	private static class ClusterConnectionManager extends Thread {

		private Map<Integer, Channel> connMap = new HashMap<Integer, Channel>();
		private List<Cluster> clusterList;
		private ClusterConf clusterConf;
		private ServerConf conf;
		public ClusterConnectionManager(ServerConf conf,ClusterConf clusterConf) {
			this.conf = conf;
			this.clusterConf = clusterConf;
			if (clusterConf !=null)
			clusterList = clusterConf.getClusters();
		}

		public void registerConnection(int nodeId, Channel channel) {
			
			connMap.put(nodeId, channel);
		}

		public ChannelFuture connect(String host, int port) {

			ChannelFuture channel = null;
			EventLoopGroup workerGroup = new NioEventLoopGroup();

			try {
				//	logger.info("Attempting to  connect to : "+host+" : "+port);
				Bootstrap b = new Bootstrap();
				b.group(workerGroup).channel(NioSocketChannel.class)
				.handler(new ServerInitializer(false));

				b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000);
				b.option(ChannelOption.TCP_NODELAY, true);
				b.option(ChannelOption.SO_KEEPALIVE, true);

				channel = b.connect(host, port).syncUninterruptibly();
				//ClusterLostListener cll = new ClusterLostListener(this);
				//channel.channel().closeFuture().addListener(cll);

			} catch (Exception e) {
				//e.printStackTrace();
				//logger.info("Cound not connect!!!!!!!!!!!!!!!!!!!!!!!!!");
				return null;
			}

			return channel;
		}

		public Request createClusterJoinMessage(int fromCluster, int fromNode,
				int toCluster, int toNode) {
			//logger.info("Creating join message");
			Request.Builder req = Request.newBuilder();

			JoinMessage.Builder jm = JoinMessage.newBuilder();
			jm.setFromClusterId(fromCluster);
			jm.setFromNodeId(fromNode);
			jm.setToClusterId(toCluster);
			jm.setToNodeId(toNode);

			req.setJoinMessage(jm.build());
			return req.build();

		}

		@Override
		public void run() {
			
			while (true) {
				//logger.info(""+isLeader);
				if(CompleteRaftManager.getInstance().getCurrentState().equalsIgnoreCase("Leader")){

					try {
						
						
	Outer:						for(Cluster cluster:clusterList){
							List<ClusterNode> nodes = cluster.getNodes();
							int key = cluster.getId();
							//logger.info("For cluster "+ key +" nodes "+ nodes.size());
							if (!connMap.containsKey(key)) {
							
							for (ClusterNode n : nodes) {
								String host = n.getIp();
								int port = n.getPort();
								ChannelFuture channel = connect(host, port);
								Request req = createClusterJoinMessage(clusterConf.getClusterId(),
										conf.getNodeId(), key, port);
								if (channel != null) {
									channel = channel.channel().writeAndFlush(req);
									//										logger.info("Message flushed"+channel.isDone()+ " "+
									//												 channel.channel().isWritable());
									if (channel.channel().isWritable()) {
										registerConnection(key,
												channel.channel());
										logger.info("Connection to cluster " + key
												+ " added");
										
										break Outer;
									}
								}
							}
							}
							}
						
						
					} catch (NoSuchElementException e) {
						//logger.info("Restarting iterations");
						
						try {
							Thread.sleep(3000);
						} catch (InterruptedException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						}
					}
				} else {
					try {
						Thread.sleep(3000);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				try {
					Thread.sleep(3000); //Give some time to react
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}

	public static class ClusterLostListener implements ChannelFutureListener {
		ClusterConnectionManager ccm;

		public ClusterLostListener(ClusterConnectionManager ccm) {
			this.ccm = ccm;
		}

		@Override
		public void operationComplete(ChannelFuture future) throws Exception {
			logger.info("Cluster " + future.channel()
					+ " closed. Removing connection");
			// TODO remove dead connection
		}
	}

private static class RaftHeartMonitor extends Thread{
	
	private boolean isLeader = true;

	public void setLeader(boolean isLeader) {
		this.isLeader = isLeader;
	}

	@Override
	public void run(){
		
		while (isLeader){
			System.out.println("Sending append messages to followers!");
			sendAppendNotice();
           try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		}
	}
	
	private void sendAppendNotice(){
		ConnectionManager.broadcastAndFlush(RaftMessageBuilder.buildAppendMessage(conf.getNodeId()));
	}

}

}
