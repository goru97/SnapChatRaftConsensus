package poke.resources;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

import poke.comm.Image.Request;
import poke.server.managers.CompleteRaftManager;
import poke.server.managers.ConnectionManager;
import poke.server.resources.Resource;
import poke.util.RaftMessageBuilder;
import io.netty.channel.Channel;
public class ImageResource implements Resource{

	private String imagePath ="../../resources/receivedImages/";
	
	@Override
	public Request process(Request request){
		boolean isLeader = false;
		int currentLeaderId = -1;
		//Integer clientId = request.getHeader().getClientId();
		
				String currentState = CompleteRaftManager.getInstance().getCurrentState();
				if(currentState.equalsIgnoreCase("Leader")){
					isLeader = true;
					Request req = RaftMessageBuilder.buildIntraClusterImageMessage(request,isLeader);
					ConnectionManager.broadcastAndFlush(req);
					
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

	@Override
	public poke.comm.App.Request process(poke.comm.App.Request request) {
		// TODO Auto-generated method stub
		return null;
	}
}
