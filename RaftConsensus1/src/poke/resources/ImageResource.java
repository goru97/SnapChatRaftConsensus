package poke.resources;

import io.netty.channel.Channel;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

import poke.comm.Image.Request;
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
	public void setChannel(PerChannelQueue pqChannel){
		this.pqChannel = pqChannel;
	}

	public PerChannelQueue getChannel(){
		return this.pqChannel;
	}
	@Override
	public Request process(Request request){
		boolean isLeader = false;
		int currentLeaderId = -1;
		boolean isClient= request.getHeader().getIsClient();
		
		
		if(isClient){
			int clientId = request.getHeader().getClientId();
			if(!clientInfo.containsKey(clientId))
				clientInfo.put(clientId, new ClientData(getChannel()));
			else
				clientInfo.get(clientInfo).setChannel(getChannel());
			
			ConnectionManager.addConnection(clientId, getChannel().getChannel(), false);
			
		}
		

		String currentState = CompleteRaftManager.getInstance().getCurrentState();
		System.out.println("Current State --> "+currentState);
		if(currentState.equalsIgnoreCase("Leader")){
			isLeader = true;
			Request req = RaftMessageBuilder.buildIntraClusterImageMessage(request,isLeader);
			ConnectionManager.broadcastAndFlush(req);
			//save image to file system	
/*
			Set<Integer> nodes =clientInfo.keySet();
			for(Integer i:nodes){
				ClientData clientData = clientInfo.get(i);
				PerChannelQueue channel = clientData.getChannel();
				channel.enqueueRequest(request, null);
			}
*/
			byte[] byteImage = request.getPayload().getData().toByteArray();
			String key = request.getPayload().getReqId();
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

		else{
			isLeader = false;
			if(request.getHeader().hasIsLeader()){   // if a message sent by leader then simply save the image
				if(request.getHeader().getIsLeader()){
					//save image to file system	


					byte[] byteImage = request.getPayload().getData().toByteArray();
					String key = request.getPayload().getReqId();
					InputStream in = new ByteArrayInputStream(byteImage);
					BufferedImage bImageFromConvert;

					System.out.println("****Image recieved by follower sent by leader****");
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
				else{ // in-case a follower send to another follower
					currentLeaderId = CompleteRaftManager.getInstance().getLeaderId();
					Channel channel = ConnectionManager.getConnection(currentLeaderId, false);
					channel.writeAndFlush(request);
				}

			}
			else{ //if sent by a client, send message to current leader

				System.out.println("****Image recieved by follower sent by client****");
				currentLeaderId = CompleteRaftManager.getInstance().getLeaderId();
				Channel appChannel = ConnectionManager.getConnection(currentLeaderId, false);
				Channel mgmtChannel = ConnectionManager.getConnection(currentLeaderId, true);

				System.out.println("appChannel -- "+appChannel+" mgmtChannel -- "+mgmtChannel);
				//channel.writeAndFlush(request);

			}

		}


		return request;

	}


	public void setConf(ServerConf conf) {
		this.conf = conf;
		this.nodeId =conf.getNodeId();
	}

	public ServerConf getConf(){
		return this.conf;
	}
}
