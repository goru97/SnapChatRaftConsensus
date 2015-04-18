package poke.resources.vo;

import poke.server.queue.PerChannelQueue;

public class ClientData {
	private PerChannelQueue channel;
	
	public ClientData(PerChannelQueue channel){
		this.channel = channel;
		
	}
	
	public PerChannelQueue getPQChannel() {
		return channel;
	}

	public void setPQChannel(PerChannelQueue channel) {
		this.channel = channel;
	}
}
