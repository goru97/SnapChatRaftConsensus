package poke.resources.vo;

import poke.server.queue.PerChannelQueue;

public class ClientData {
	private PerChannelQueue channel;
	
	public ClientData(PerChannelQueue channel){
		this.channel = channel;
		
	}
	
	public PerChannelQueue getChannel() {
		return channel;
	}

	public void setChannel(PerChannelQueue channel) {
		this.channel = channel;
	}
}
