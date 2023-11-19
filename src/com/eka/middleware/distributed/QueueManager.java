package com.eka.middleware.distributed;

import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;

import com.eka.middleware.distributed.offHeap.IgNode;
import com.eka.middleware.distributed.offHeap.IgQueue;
import com.eka.middleware.template.Tenant;

public class QueueManager {
	private static final Map<String, Queue> queueMap=new ConcurrentHashMap<>(); 
	public static Queue<Object> getQueue(Tenant tenant,String name){
		name=tenant.id+"-"+name;
		Queue queue=queueMap.get(name);
		if(queue==null) {
			if(IgNode.getNodeId()!=null)
				queue=new IgQueue<Object>(IgNode.getIgnite(),name);
			else
				queue=new PriorityBlockingQueue<Object> (); 
			queueMap.put(name, queue);
		}
		return queue;
	}
	
	public static void deleteQueue(Tenant tenant,String name) {
		name=tenant.id+"-"+name;
		Queue queue=queueMap.get(name);
		if(queue!=null) {
			queue.remove(); 
			queueMap.remove(name);
		}
	}
}
