package poke.resources;

import io.netty.channel.Channel;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

import poke.comm.Image.Request;
import poke.server.conf.ServerConf;
import poke.server.managers.CompleteRaftManager;
import poke.server.managers.ConnectionManager;
import poke.server.resources.ImgResource;
import poke.util.RaftMessageBuilder;
public class ImageResource implements ImgResource{

	private String imagePath ="../../resources/receivedImages/";
	private ServerConf conf;
	private int nodeId =-1;
	@Override
	public Request process(Request request){
		boolean isLeader = false;
		int currentLeaderId = -1;
		//Integer clientId = request.getHeader().getClientId();
		
				String currentState = CompleteRaftManager.getInstance().getCurrentState();
			System.out.println("Current State --> "+currentState);
				if(currentState.equalsIgnoreCase("Leader")){
					isLeader = true;
					Request req = RaftMessageBuilder.buildIntraClusterImageMessage(request,isLeader);
					ConnectionManager.broadcastAndFlush(req);
					//save image to file system	
					

					byte[] byteImage = request.getPayload().getData().toByteArray();
					String key = request.getPayload().getReqId();
					InputStream in = new ByteArrayInputStream(byteImage);
					BufferedImage bImageFromConvert;

					System.out.println("********Image recieved********");
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
					if(request.getHeader().hasIsLeader()){   // if a message sent by leader
						if(request.getHeader().getIsLeader()){
						//save image to file system	
							

							byte[] byteImage = request.getPayload().getData().toByteArray();
							String key = request.getPayload().getReqId();
							InputStream in = new ByteArrayInputStream(byteImage);
							BufferedImage bImageFromConvert;

							System.out.println("********Image recieved********");
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
						}
						
					}
					else{ //send message to current leader
						currentLeaderId = CompleteRaftManager.getInstance().getLeaderId();
						Channel channel = ConnectionManager.getConnection(currentLeaderId, false);
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
