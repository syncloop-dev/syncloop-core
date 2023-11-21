package com.eka.middleware.distributed.offHeap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.cluster.ClusterGroup;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.eka.middleware.heap.HashMap;
import com.eka.middleware.service.ServiceUtils;
import com.eka.middleware.template.Tenant;


public class IgNode {
	public static Logger LOGGER = LogManager.getLogger(IgNode.class);
    private static Ignite ignite=null;
    private static String nodeId=null; 
    private IgNode() {}
	public static synchronized Ignite getIgnite() {
		
		if(ignite!=null)
			return ignite;
        // Creating a new configuration for the Ignite node
        IgniteConfiguration cfg = new IgniteConfiguration();

        // Setting up the TCP Discovery SPI to find other nodes in the network
        TcpDiscoverySpi spi = new TcpDiscoverySpi();
        TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
        //spi.failureDetectionTimeoutEnabled(false);//setFailureDetectionTimeout(10000);

        // Optionally set socket timeout, if needed
        //spi.setSocketTimeout(5000);
        String nodes=ServiceUtils.getServerProperty("middleware.accessible.cluster.nodes");
        String nodeList[]=null;
        List<String> addresses=new ArrayList<>();
        //addresses.add("localhost:47500..47509");
        if(nodes!=null && nodes.trim().length()>10) {
        	nodeList=nodes.split(",");
        	if(nodeList.length>0)
	        	for (String address : nodeList) {
	        		addresses.add(address.replace(" ", ""));
				}
        }
        if(addresses.size()==0) {
        	LOGGER.info("**************************************************************************************************************************");
        	LOGGER.info("* Ignite node configuration not found. Hence not starting the ignite node.                                               *");
        	LOGGER.info("* Caching will be locally stored on the java-heap memory.                                                                *");
        	LOGGER.info("* To enable off heap caching you must configure Ignite node.                                                             *");
        	LOGGER.info("* Example: Add this property to server configuration. 'middleware.accessible.cluster.nodes=localhost:47500..47509'       *");
        	LOGGER.info("* Server properties can only be managed by default tenant.                                                               *");
        	LOGGER.info("**************************************************************************************************************************");
        	return null;
        }
        // Set multiple IP addresses of the nodes in the cluster

        ipFinder.setAddresses(addresses);
        spi.setIpFinder(ipFinder);
        cfg.setConsistentId(UUID.randomUUID().toString());
        cfg.setDiscoverySpi(spi);
        
        // Starting the node with the given configuration
        try {
        	ignite = Ignition.start(cfg);
            nodeId = ignite.cluster().localNode().id().toString();
        }catch (Exception e) {
			e.printStackTrace();
		}
        return ignite;
    }
	
	public static String getRandomClusterNode(Tenant tenant) {
		if(nodeId==null)
			return tenant.id;
		
		int size=IgNode.getIgnite().cluster().nodes().size();
		if(size==1) {
			if(nodeId==null)
				nodeId = ignite.cluster().localNode().id().toString();
			return nodeId;
		}
		
		int nodeNumber=new Random().nextInt(size);
		final AtomicInteger ai=new AtomicInteger(0);
		final Map<String, Object> map=new HashMap<>();
		IgNode.getIgnite().cluster().nodes().forEach(node->{
			if(ai.getAndIncrement()==nodeNumber)
				map.put("id", node.id().toString());
		});
		
		return (String)map.get("id");
	}
	
	public static String getLowUsageNode(Tenant tenant) {
		if(nodeId==null)
			return tenant.id;
		
		int size=IgNode.getIgnite().cluster().nodes().size();
		if(size==1) {
			if(nodeId==null)
				nodeId = ignite.cluster().localNode().id().toString();
			return nodeId;
		}
		
		int nodeNumber=new Random().nextInt(size);
		final AtomicInteger ai=new AtomicInteger(0);
		final Map<String, Object> map=new HashMap<>();
		IgNode.getIgnite().cluster().nodes().forEach(node->{
			double cpuUsage=node.metrics().getCurrentCpuLoad();
			double cpu=map.get("cpu")!=null?(double)map.get("cpu"):1d;
			if(cpu>cpuUsage) {
				map.put("cpu",cpuUsage);
				map.put("id", node.id().toString());
			}
			if(map.get("id")==null && ai.getAndIncrement()==nodeNumber)
				map.put("id", node.id().toString());
		});
		
		return (String)map.get("id");
	}
	
	public static String getLocalNode(Tenant tenant) {
		if(nodeId==null)
			return tenant.id;
		else
			return nodeId;
	}
	
	public static void stop() {
		ignite.close();
	}
	public static String getNodeId() {
		return nodeId;
	}
}
