package com.eka.middleware.distributed.offHeap;

import java.util.Collection;
import java.util.Iterator;
import java.util.Queue;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteQueue;
import org.apache.ignite.configuration.CollectionConfiguration;
import static org.apache.ignite.cache.CacheMode.PARTITIONED;
public class IgQueue<E> implements Queue<E> {
    private final IgniteQueue<E> igniteQueue;

    public IgQueue(Ignite ignite, String queueName) {
        // Create or get the Ignite queue
    	CollectionConfiguration colCfg = new CollectionConfiguration();
    	colCfg.setCacheMode(PARTITIONED);
        this.igniteQueue = ignite.queue(queueName, 0, colCfg);
        System.out.println("New queue created with queueName: "+colCfg);
    }

    @Override
    public boolean add(E e) {
        return igniteQueue.add(e);
    }

    @Override
    public boolean offer(E e) {
        return igniteQueue.offer(e);
    }

    @Override
    public E remove() {
        return igniteQueue.remove();
    }

    @Override
    public E poll() {
        return igniteQueue.poll();
    }

    @Override
    public E element() {
        return igniteQueue.element();
    }

    @Override
    public E peek() {
        return igniteQueue.peek();
    }

    @Override
    public int size() {
        return igniteQueue.size();
    }

    @Override
    public boolean isEmpty() {
        return igniteQueue.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return igniteQueue.contains(o);
    }

    @Override
    public Iterator<E> iterator() {
        return igniteQueue.iterator();
    }

    @Override
    public Object[] toArray() {
        return igniteQueue.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return igniteQueue.toArray(a);
    }

    @Override
    public boolean remove(Object o) {
        return igniteQueue.remove(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return igniteQueue.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        return igniteQueue.addAll(c);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return igniteQueue.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return igniteQueue.retainAll(c);
    }

    @Override
    public void clear() {
        igniteQueue.clear();
    }
    
    public static void main(String[] args) {
    	IgQueue iigq=new IgQueue<>(IgNode.getIgnite(), "test");
    	System.out.println("Test");
    	iigq.poll();
    	System.out.println("Test");
	}
}

