package poke.server.monitor;

public class AppHeartMonitor extends HeartMonitor{

	public AppHeartMonitor(int iamNode, String host, int port, int toNodeId) {
		super(iamNode, host, port, toNodeId);
	}

}
