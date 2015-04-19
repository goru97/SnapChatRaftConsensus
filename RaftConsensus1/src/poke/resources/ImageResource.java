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
import poke.server.conf.ClusterConf;
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
	private ClusterConf clusterConf;

	@Override
	public void setClusterConf(ClusterConf clusterConf) {
		this.clusterConf = clusterConf;
	}

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
		System.out.println("Channel --> "+channel);
		channel.writeAndFlush(request).syncUninterruptibly();
	}

	public void sendImageToCluster(Request req, Channel channel){
		channel.writeAndFlush(req);
	}


	@Override
	public Request process(Request request){

		String currentState = CompleteRaftManager.getInstance().getCurrentState();

		if(currentState.equalsIgnoreCase("Leader")){

			if(request.hasJoinMessage()){ //Either a client or a cluster is requesting connection with our system

				boolean isCluster = request.getJoinMessage().hasFromClusterId();

				if(isCluster){
					System.out.println("***Message Received from cluster***"+ request.getJoinMessage().getFromClusterId());
					int clusterId = request.getJoinMessage().getFromClusterId();
					if(!clusterInfo.containsKey(clusterId))
						clusterInfo.put(clusterId, new ClientData(getPQChannel()));
					else
						clusterInfo.get(clusterId).setPQChannel(getPQChannel());	
					System.out.println("***Cluster Info size***"+clusterInfo.size());
				}


				else{  //client is requesting connection with our system

					if(request.getJoinMessage().hasToNodeId()) //Join request coming from adjacent nodes
						ConnectionManager.addConnection(request.getJoinMessage().getFromNodeId(), getPQChannel().getChannel(), false);

					else{ //Actual clients are sending join requests
						System.out.println("***Adding Client "+request.getJoinMessage().getFromNodeId()+" to the System****");
						int clientId = request.getJoinMessage().getFromNodeId();
						if(!clientInfo.containsKey(clientId))
							clientInfo.put(clientId, new ClientData(getPQChannel()));
						else
							clientInfo.get(clientId).setPQChannel(getPQChannel());


					}

				}





			}

			else{  //There's an incoming image from client or cluster
				int currentLeaderId = -1;

				byte[] byteImage =null ;
				String key = null;

				if(request.getBody().hasClusterMessage()){ // if image received from any cluster

					byteImage = request.getBody().getClusterMessage().getClientMessage().getMsgImageBits().toByteArray();
					key = request.getBody().getClusterMessage().getClientMessage().getMsgId();

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
							System.out.println("***Sending Image to the Client****");
							foundTheClient = true;
							break;
						}
					}
					if(!foundTheClient){
						if(request.getBody().getClusterMessage().getClusterId() != clusterConf.getClusterId()){//Do not handle requests coming from own cluster
							Iterator<Entry<Integer,ClientData>> itr = clusterInfo.entrySet().iterator();
							while(itr.hasNext()){
								Map.Entry<Integer, ClientData> entry = itr.next();
								Channel channel = entry.getValue().getPQChannel().getChannel();
								Request req = RaftMessageBuilder.buildClusterMessage(request, clusterConf.getClusterId());
								sendImageToCluster(req,channel);
								System.out.println("***Sending Image to other Clusters****");
								break;

							}
						}
					}
				}

				else if(request.getBody().hasClientMessage()){ // if image received from any client
					byteImage = request.getBody().getClientMessage().getMsgImageBits().toByteArray();
					key = request.getBody().getClientMessage().getMsgId();

					ClientMessage msg = request.getBody().getClientMessage();
					System.out.println("***Image received from client*** "+msg.getReceiverUserName());
					int receiverId = msg.getReceiverUserName();
					System.out.println("Client Info Size client data "+clientInfo.size());
					Iterator<Entry<Integer,ClientData>> i = clientInfo.entrySet().iterator();
					boolean foundTheClient = false;

					while(i.hasNext()){

						Map.Entry<Integer, ClientData> entry = i.next();
						if(entry.getKey() == receiverId){
							Channel channel = entry.getValue().getPQChannel().getChannel();
							sendImgToClient(request,channel);
							System.out.println("***Sending Image to the Client****");
							foundTheClient = true;
							break;
						}
					}
					if(!foundTheClient){
						System.out.println("***Cluster Size***"+clusterInfo.size());
						Iterator<Entry<Integer,ClientData>> itr = clusterInfo.entrySet().iterator();

						while(itr.hasNext()){
							Map.Entry<Integer, ClientData> entry = itr.next();
							Channel channel = entry.getValue().getPQChannel().getChannel();
							Request req = RaftMessageBuilder.buildClusterMessage(request, clusterConf.getClusterId());
							sendImageToCluster(req,channel);
							System.out.println("***Sending Image to other Clusters****");
							break;

						}

					}
				}


				Request req = RaftMessageBuilder.buildIntraClusterImageMessage(request,conf.getNodeId()); //Replicate across our own cluster for fault tolerance
				ConnectionManager.broadcastAndFlush(req);



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
		}
		else{

			int currentLeaderId =-1;
			if(request.hasJoinMessage()){

				//Either a client or a cluster is requesting connection with our system

				boolean isCluster = request.getJoinMessage().hasFromClusterId();

				if(isCluster){


					System.out.println("***Message Received from cluster***"+ request.getJoinMessage().getFromClusterId());
					int clusterId = request.getJoinMessage().getFromClusterId();
					if(!clusterInfo.containsKey(clusterId))
						clusterInfo.put(clusterId, new ClientData(getPQChannel()));
					else
						clusterInfo.get(clusterId).setPQChannel(getPQChannel());	
					System.out.println("***Cluster Info size***"+clusterInfo.size());

					/*
					currentLeaderId = CompleteRaftManager.getInstance().getLeaderId();
					Channel appChannel = ConnectionManager.getConnection(currentLeaderId, false);
					appChannel.writeAndFlush(request);
					 */
				}


				else{  //client is requesting connection with our system

					if(request.getJoinMessage().hasToNodeId()) //Join request coming from adjacent nodes
						ConnectionManager.addConnection(request.getJoinMessage().getFromNodeId(), getPQChannel().getChannel(), false);

					else{ //Actual clients are sending join requests

						System.out.println("***Adding Client "+request.getJoinMessage().getFromNodeId()+" to the System****");
						int clientId = request.getJoinMessage().getFromNodeId();
						if(!clientInfo.containsKey(clientId))
							clientInfo.put(clientId, new ClientData(getPQChannel()));
						else
							clientInfo.get(clientId).setPQChannel(getPQChannel());


						/*
						currentLeaderId = CompleteRaftManager.getInstance().getLeaderId();
						Channel appChannel = ConnectionManager.getConnection(currentLeaderId, false);
						appChannel.writeAndFlush(request);
						 */
					}

				}


			}
			else{
				// if a message sent by leader then simply save the image and forward it to Clusters or client connected
				if(request.getHeader().getOriginator() == CompleteRaftManager.getInstance().getLeaderId()){

					//save image to file system	


					byte[] byteImage = null;
					String key = null;

					if(request.getBody().hasClusterMessage()){
						byteImage = request.getBody().getClusterMessage().getClientMessage().getMsgImageBits().toByteArray();
						key = request.getBody().getClusterMessage().getClientMessage().getMsgId();
					
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
								System.out.println("***Sending Image to the Client****");
								foundTheClient = true;
								break;
							}
						}
						if(!foundTheClient){
							if(request.getBody().getClusterMessage().getClusterId() != clusterConf.getClusterId()){//Do not handle requests coming from own cluster
								Iterator<Entry<Integer,ClientData>> itr = clusterInfo.entrySet().iterator();
								while(itr.hasNext()){
									Map.Entry<Integer, ClientData> entry = itr.next();
									Channel channel = entry.getValue().getPQChannel().getChannel();
									Request req = RaftMessageBuilder.buildClusterMessage(request, clusterConf.getClusterId());
									sendImageToCluster(req,channel);
									System.out.println("***Sending Image to other Clusters****");
									break;

								}
							}
						}
					}
					else{
						 // if image received from any client
						byteImage = request.getBody().getClientMessage().getMsgImageBits().toByteArray();
						key = request.getBody().getClientMessage().getMsgId();

						ClientMessage msg = request.getBody().getClientMessage();
						System.out.println("***Image received from client*** "+msg.getReceiverUserName());
						int receiverId = msg.getReceiverUserName();
						System.out.println("Client Info Size client data "+clientInfo.size());
						Iterator<Entry<Integer,ClientData>> i = clientInfo.entrySet().iterator();
						boolean foundTheClient = false;

						while(i.hasNext()){

							Map.Entry<Integer, ClientData> entry = i.next();
							if(entry.getKey() == receiverId){
								Channel channel = entry.getValue().getPQChannel().getChannel();
								sendImgToClient(request,channel);
								System.out.println("***Sending Image to the Client****");
								foundTheClient = true;
								break;
							}
						}
						if(!foundTheClient){
							System.out.println("***Cluster Size***"+clusterInfo.size());
							Iterator<Entry<Integer,ClientData>> itr = clusterInfo.entrySet().iterator();

							while(itr.hasNext()){
								Map.Entry<Integer, ClientData> entry = itr.next();
								Channel channel = entry.getValue().getPQChannel().getChannel();
								Request req = RaftMessageBuilder.buildClusterMessage(request, clusterConf.getClusterId());
								sendImageToCluster(req,channel);
								System.out.println("***Sending Image to other Clusters****");
								break;

							}

						}
					
					}

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
					
					
					// if image received from any cluster
					byte[] byteImage =null ;
					String key = null;

					if(request.getBody().hasClusterMessage()){

						byteImage = request.getBody().getClusterMessage().getClientMessage().getMsgImageBits().toByteArray();
						key = request.getBody().getClusterMessage().getClientMessage().getMsgId();

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
								System.out.println("***Sending Image to the Client****");
								foundTheClient = true;
								break;
							}
						}
						if(!foundTheClient){
							if(request.getBody().getClusterMessage().getClusterId() != clusterConf.getClusterId()){//Do not handle requests coming from own cluster
								Iterator<Entry<Integer,ClientData>> itr = clusterInfo.entrySet().iterator();
								while(itr.hasNext()){
									Map.Entry<Integer, ClientData> entry = itr.next();
									Channel channel = entry.getValue().getPQChannel().getChannel();
									Request req = RaftMessageBuilder.buildClusterMessage(request, clusterConf.getClusterId());
									sendImageToCluster(req,channel);
									System.out.println("***Sending Image to other Clusters****");
									break;

								}
							}
						}
					}

					else if(request.getBody().hasClientMessage()){ // if image received from any client
						byteImage = request.getBody().getClientMessage().getMsgImageBits().toByteArray();
						key = request.getBody().getClientMessage().getMsgId();

						ClientMessage msg = request.getBody().getClientMessage();
						System.out.println("***Image received from client*** "+msg.getReceiverUserName());
						int receiverId = msg.getReceiverUserName();
						System.out.println("Client Info Size client data "+clientInfo.size());
						Iterator<Entry<Integer,ClientData>> i = clientInfo.entrySet().iterator();
						boolean foundTheClient = false;

						while(i.hasNext()){

							Map.Entry<Integer, ClientData> entry = i.next();
							if(entry.getKey() == receiverId){
								Channel channel = entry.getValue().getPQChannel().getChannel();
								sendImgToClient(request,channel);
								System.out.println("***Sending Image to the Client****");
								foundTheClient = true;
								break;
							}
						}
						if(!foundTheClient){
							System.out.println("***Cluster Size***"+clusterInfo.size());
							Iterator<Entry<Integer,ClientData>> itr = clusterInfo.entrySet().iterator();

							while(itr.hasNext()){
								Map.Entry<Integer, ClientData> entry = itr.next();
								Channel channel = entry.getValue().getPQChannel().getChannel();
								Request req = RaftMessageBuilder.buildClusterMessage(request, clusterConf.getClusterId());
								sendImageToCluster(req,channel);
								System.out.println("***Sending Image to other Clusters****");
								break;

							}

						}
					}
					

					InputStream in = new ByteArrayInputStream(byteImage);
					BufferedImage bImageFromConvert;

					System.out.println("****Image recieved by follower****");
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
