package poke.client;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Scanner;
import java.util.UUID;

import javax.imageio.ImageIO;

import poke.client.comm.CommListener;

import com.google.protobuf.ByteString;

public class ImageClient {
	
	   int clientID;
		public ImageClient(int clientID) {
			this.clientID = clientID;
		}
	public void run(ClientCommand cc) {

		try {

			byte[] myByeImage;
			BufferedImage originalImage = ImageIO.read(new File(
					"./resources/images/test.png"));

			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ImageIO.write(originalImage, "png", baos);
			baos.flush();
			myByeImage = baos.toByteArray();
			baos.close();
			
			String uniqueRequestID = UUID.randomUUID().toString().replaceAll("-", "");			
			ByteString bs = ByteString.copyFrom(myByeImage);
			cc.sendImage(uniqueRequestID, "Test Image", bs , clientID);
			
		} catch (IOException e) {
			e.printStackTrace();
		}

	}
public static void main(String[] args){
	try {
		
		Scanner sc = new Scanner(System.in);
		
		ClientCommand cc = new ClientCommand("localhost", 5571);
		CommListener listener = new ClientPrintListener("First Client");
		cc.addListener(listener);

		ImageClient cone = new ImageClient(4);
		
		String clientInput = "";

		//do {

			System.out.println("Do you want to send images (Y/N)?");
			clientInput = sc.nextLine();

			if (clientInput != null && "Y".equalsIgnoreCase(clientInput)) {

				for(int i=0;i<20;i++){
				System.out.println("Sending Image... "+i);
				
				cone.run(cc);
				}
			}

	//	}

	//	while (!"N".equalsIgnoreCase(clientInput));
				
		
		// we are running asynchronously
	//	System.out.println("\nExiting in few seconds");
	//	Thread.sleep(150000000);
	//	System.exit(0);

	} catch (Exception e) {
		e.printStackTrace();
	}
}
}
