package com.eka.middleware.distributed.offHeap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteException;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.messaging.MessagingListenActor;
import org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi;
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
    
    private static IgniteConfiguration setupIgniteCfg(TcpDiscoverySpi spi) {
    	// Creating a new configuration for the Ignite node
    	String port=ServiceUtils.getServerProperty("middleware.accessible.cluster.comm.port");
        if(port==null)
        	port="48100";
    	TcpCommunicationSpi commSpi = new TcpCommunicationSpi();
    	commSpi.setLocalPort(Integer.parseInt(port));
    	commSpi.setUsePairedConnections(true);
    	commSpi.setDirectBuffer(false);
    	
        IgniteConfiguration cfg = new IgniteConfiguration();
        UUID uuid=UUID.randomUUID();
        cfg.setConsistentId(uuid.toString());
        cfg.setCommunicationSpi(commSpi);
        cfg.setDiscoverySpi(spi);
        return cfg;
    }
    
    private static TcpDiscoverySpi setupSPI(TcpDiscoveryVmIpFinder ipFinder) {
    	TcpDiscoverySpi spi = new TcpDiscoverySpi();
    	String port=ServiceUtils.getServerProperty("middleware.accessible.cluster.port");
        if(port==null)
        	port="48500";
    	spi.setLocalPort(Integer.parseInt(port));
    	spi.setIpFinder(ipFinder);
    	//spi.setClientReconnectDisabled(true);
    	return spi;
    }
    
    private static TcpDiscoveryVmIpFinder setupIpFinder(List<String> addresses) {
    	TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
    	ipFinder.setAddresses(addresses);
    	return ipFinder;
    }
    
    private static List<String> getBindingAddresses(){
    	String nodes=ServiceUtils.getServerProperty("middleware.accessible.cluster.nodes");
        String nodeList[]=null;
        List<String> addresses=new ArrayList<>();
        String port=ServiceUtils.getServerProperty("middleware.accessible.cluster.port");
        if(port==null)
        	port="48500";
        if(nodes!=null && nodes.trim().length()>10) {
        	nodeList=nodes.split(",");
        	if(nodeList.length>0)
	        	for (String address : nodeList) {
	        		addresses.add(address.replace(" ", "")+":"+port);
				}
        }
        
        return addresses;
    }
    
	public static synchronized Ignite getIgnite() {
		
		if(ignite!=null)
			return ignite;
        
		List<String> addresses=getBindingAddresses();
		
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
		
		TcpDiscoveryVmIpFinder ipFinder = setupIpFinder(addresses);

        TcpDiscoverySpi spi = setupSPI(ipFinder);

        IgniteConfiguration cfg = setupIgniteCfg(spi);

        try {
        	ignite = Ignition.start(cfg);
            nodeId = ignite.cluster().localNode().id().toString();
            
           /* ignite.events().localListen(event -> {
            	System.out.println("Node disconnected. consistentId: "+event.node().consistentId().toString());
            	List<String> tenantNames=TenantRepository.getAllTenants();
            	tenantNames.forEach(name->{
            		Tenant tenant=Tenant.getTenant(name);
            		//String batchQueueName=(String)CacheManager.getCacheAsMap(tenant).get("BatchQueueName");
            		Queue queue = QueueManager.getQueue(tenant, "ServiceQueue");
            		Queue bq = QueueManager.getQueue(tenant, "BatchQueue");
            		final Map BSQC=CacheManager.getOrCreateNewCache(Tenant.getTenant(name), "BackupServiceQueueCache");
            		//List<String> batchIds=new ArrayList<>();
            		Map<String, String> batchData=new HashMap<>();
            		System.out.println("Recovering data for tenant :"+name);
            		String batchID=(String)bq.poll();
            		while(batchID!=null) {
            			String data=(String)BSQC.get(batchID);
            			if(batchID!=null && data!=null)
            				batchData.put(batchID, data);
            			batchID=(String)bq.poll();
            			System.out.println("Recovered batch :"+batchID);
            		}
            		System.out.println("Recovered data for tenant :"+name);
            		try {
            			System.out.println("Waiting for 10 seconds...");
            			Thread.sleep(10000);
            			System.out.println("Republishing data for tenant :"+name);
            			batchData.forEach((k,v)->{
            				try {
            					bq.add(k);
                				queue.add(v);
							} catch (Exception e) {
								ServiceUtils.printException("*********************************************************************", e);
							}
            				
            				System.out.println("Republishing batch: "+k);
            			});
            			batchData.clear();
            			System.out.println("Data published for tenant :"+name);
            		}catch (Exception e) {
            			ServiceUtils.printException("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++", e);
            			e.printStackTrace();
					}
            		
            	});
            	
            	
                return true; // Return true to continue listening
            }, EventType.EVT_NODE_LEFT, EventType.EVT_NODE_FAILED);*/
            
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
			double cpuUsage=node.metrics().getAverageCpuLoad();
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
	
	public static String getHighUsageNode(Tenant tenant) {
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
			double cpuUsage=node.metrics().getAverageCpuLoad();
			double cpu=map.get("cpu")!=null?(double)map.get("cpu"):0d;
			if(cpu<cpuUsage) {
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
