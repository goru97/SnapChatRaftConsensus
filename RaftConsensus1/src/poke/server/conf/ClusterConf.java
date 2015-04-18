package poke.server.conf;

import java.util.List;

public class ClusterConf {
    int clusterId;

    public ClusterConf(){}

    public List<Cluster> getClusters() {
        return clusters;
    }

    public void setClusters(List<Cluster> clusters) {
        this.clusters = clusters;
    }

    public int getClusterId() {
        return clusterId;
    }

    public void setClusterId(int clusterId) {
        this.clusterId = clusterId;
    }

    List<Cluster> clusters;




public static class Cluster {
    public List<ClusterNode> getNodes() {
        return nodes;
    }

    public void setNodes(List<ClusterNode> nodes) {
        this.nodes = nodes;
    }


	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}
    int id;
    List<ClusterNode> nodes;

}

public static class ClusterNode{
    String ip;
    int port;

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

}
}