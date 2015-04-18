package poke.resources;

import io.netty.channel.Channel;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import javax.imageio.ImageIO;

import poke.comm.App.ClientMessage;
import poke.comm.App.ClusterMessage;
import poke.comm.App.Request;
import poke.resources.vo.ClientData;
import poke.server.conf.ServerConf;
import poke.server.managers.CompleteRaftManager;
import poke.server.managers.ConnectionManager;
import poke.server.queue.PerChannelQueue;
import poke.server.resources.ImgResource;
import poke.util.RaftMessageBuilder;
public class ImageResource implements ImgResource{
	private Map<Integer, ClientData> clientInfo;
	private Map<Integer, ClientData> clusterInfo;
	private String imagePath ="../../resources/receivedImages/";
	private ServerConf conf;
	private int nodeId =-1;
	private PerChannelQueue pqChannel;

	public ImageResource(){
		clientInfo = new HashMap<Integer, ClientData>();
		clusterInfo = new HashMap<Integer, ClientData>();
	}

	@Override
	public void setPQChannel(PerChannelQueue pqChannel){
		this.pqChannel = pqChannel;
	}

	public PerChannelQueue getPQChannel(){
		return this.pqChannel;
	}

	public void sendImgToClient(Request request, Channel channel){		
		channel.writeAndFlush(request);
	}

	public void sendImageToCluster(Request req, Channel channel){
channel.writeAndFlush(req);
	}


	@Override
	public Request process(Request request){

		if(request.hasJoinMessage()){ //Either a client or a cluster is requesting connection with our system
			String currentState = CompleteRaftManager.getInstance().getCurrentState();
			
			if(currentState.equalsIgnoreCase("Leader")){ //Joins can only be approved by the leader
				
				System.out.println("*****Join message received by Leader*****");
			boolean isClient= request.getBody().getClientMessage().getIsClient();
			
			if(isClient){  //client is requesting connection with our system
				int clientId = request.getBody().getClientMessage().getSenderUserName();
				if(!clientInfo.containsKey(clientId))
					clientInfo.put(clientId, new ClientData(getPQChannel()));
				else
					clientInfo.get(clientId).setPQChannel(getPQChannel());

			}
			else{ //Another cluster/System is requesting connection with our system
				int clusterId = request.getBody().getClusterMessage().getClusterId();
				if(!clusterInfo.containsKey(clusterId))
					clientInfo.put(clusterId, new ClientData(getPQChannel()));
				else
					clientInfo.get(clusterId).setPQChannel(getPQChannel());
             
			}
			
			
			}
			else if("Adjacent".equalsIgnoreCase(request.getHeader().getTag())) //Join request coming from adjacent nodes
            	//setup the adjacent node connections
     			ConnectionManager.addConnection(request.getJoinMessage().getFromNodeId(), getPQChannel().getChannel(), false);
			else{ //coming from cluster to follower
		
				System.out.println("*****Join message received by follower, redirecting to Leader*****");
				int currentLeaderId = CompleteRaftManager.getInstance().getLeaderId();
				Channel appChannel = ConnectionManager.getConnection(currentLeaderId, false);
				appChannel.writeAndFlush(request);
			}
			
		}

		else{  //There's an incoming image from client or cluster
			boolean isLeader = false;
			int currentLeaderId = -1;

			String currentState = CompleteRaftManager.getInstance().getCurrentState();
		
			if(currentState.equalsIgnoreCase("Leader")){
				isLeader = true;

				if(request.getBody().hasClusterMessage()){  // if image received from any cluster

					ClusterMessage msg = request.getBody().getClusterMessage();
					System.out.println("***Image received from cluster*** "+msg.getClusterId());
					int receiverId = msg.getClientMessage().getReceiverUserName();

					Iterator<Entry<Integer,ClientData>> i = clientInfo.entrySet().iterator();
					boolean foundTheClient = false;
					while(i.hasNext()){
						Map.Entry<Integer, ClientData> entry = i.next();
						if(entry.getKey() == receiverId){
							Channel channel = entry.getValue().getPQChannel().getChannel();
							sendImgToClient(request,channel);
							foundTheClient = true;
							break;
						}
					}
					if(!foundTheClient){
						Iterator<Entry<Integer,ClientData>> itr = clusterInfo.entrySet().iterator();
						while(i.hasNext()){
							Map.Entry<Integer, ClientData> entry = i.next();
								Channel channel = entry.getValue().getPQChannel().getChannel();
								sendImageToCluster(request,channel);
								foundTheClient = true;
								break;
						
					}
					
				}
				}
				
				else if(request.getBody().hasClientMessage()){ // if image received from any client
					
					ClientMessage msg = request.getBody().getClientMessage();
					System.out.println("***Image received from client*** "+msg.getReceiverUserName());
					int receiverId = msg.getReceiverUserName();

					Iterator<Entry<Integer,ClientData>> i = clientInfo.entrySet().iterator();
					boolean foundTheClient = false;
					while(i.hasNext()){
						Map.Entry<Integer, ClientData> entry = i.next();
						if(entry.getKey() == receiverId){
							Channel channel = entry.getValue().getPQChannel().getChannel();
							sendImgToClient(request,channel);
							foundTheClient = true;
							break;
						}
					}
					if(!foundTheClient){
						Iterator<Entry<Integer,ClientData>> itr = clusterInfo.entrySet().iterator();
						while(i.hasNext()){
							Map.Entry<Integer, ClientData> entry = i.next();
								Channel channel = entry.getValue().getPQChannel().getChannel();
								sendImageToCluster(request,channel);
								foundTheClient = true;
								break;
						
					}
					
				}
				}
				

				Request req = RaftMessageBuilder.buildIntraClusterImageMessage(request,conf.getNodeId()); //Replicate across our own cluster for fault tolerance
				ConnectionManager.broadcastAndFlush(req);


				byte[] byteImage = request.getBody().getClientMessage().getMsgImageBits().toByteArray();
				String key = request.getBody().getClientMessage().getMsgId();
				InputStream in = new ByteArrayInputStream(byteImage);
				BufferedImage bImageFromConvert;

				System.out.println("****Image recieved by leader****");
				try {
					File file = new File(imagePath, key + ".png");
					if (!file.exists()) {
						file.createNewFile();
					}
					bImageFromConvert = ImageIO.read(in);
					ImageIO.write(bImageFromConvert, "png", file);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}


			}

			else
				// if a message sent by leader then simply save the image
				if(request.getHeader().getOriginator() == CompleteRaftManager.getInstance().getLeaderId()){

					isLeader = false;
					//save image to file system	

					byte[] byteImage = request.getBody().getClientMessage().getMsgImageBits().toByteArray();
					String key = request.getBody().getClientMessage().getMsgId();
					InputStream in = new ByteArrayInputStream(byteImage);
					BufferedImage bImageFromConvert;

					System.out.println("****Image recieved by follower sent by leader; Saving in FileSystem****");
					try {
						File file = new File(imagePath, key + ".png");
						if (!file.exists()) {
							file.createNewFile();
						}
						bImageFromConvert = ImageIO.read(in);
						ImageIO.write(bImageFromConvert, "png", file);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}


				}


				else{ //if sent by a client, send message to current leader

					System.out.println("****Image recieved by follower sent by Client/Cluster; Redirecting to leader****");
					currentLeaderId = CompleteRaftManager.getInstance().getLeaderId();
					Channel appChannel = ConnectionManager.getConnection(currentLeaderId, false);
					//Channel mgmtChannel = ConnectionManager.getConnection(currentLeaderId, true);
					appChannel.writeAndFlush(request);
					//System.out.println("appChannel -- "+appChannel+" mgmtChannel -- "+mgmtChannel);
					//channel.writeAndFlush(request);

				}

		}
		
		return request;
	}

	@Override
	public void setConf(ServerConf conf) {
		this.conf = conf;
		this.nodeId =conf.getNodeId();
	}

	public ServerConf getConf(){
		return this.conf;
	}
}
