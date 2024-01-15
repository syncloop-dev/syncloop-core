package com.eka.middleware.distributed.offHeap;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.cache.CacheWriteSynchronizationMode;
import org.apache.ignite.configuration.CacheConfiguration;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class IgMap<K, V> implements Map<K, V> {

	private final IgniteCache<K, V> cache;

	public IgMap(Ignite ignite, String cacheName) {
		CacheConfiguration<K, V> ccf=new CacheConfiguration<K, V>();
		ccf.setName(cacheName);
		ccf.setCacheMode(CacheMode.REPLICATED);
		ccf.setWriteSynchronizationMode(CacheWriteSynchronizationMode.FULL_SYNC);
		this.cache = (IgniteCache<K, V>) ignite.getOrCreateCache(ccf);
		//cache.co
	}

	@Override
	public int size() {
		return (int) cache.size();
	}

	@Override
	public boolean isEmpty() {
		return cache.size() == 0;
	}

	@Override
	public boolean containsKey(Object key) {
		return cache.containsKey((K) key);
	}

	@Override
	public boolean containsValue(Object value) {
		throw new UnsupportedOperationException("This operation is not supported yet.");
	}

	@Override
	public V get(Object key) {
		return cache.get((K) key);
	}

	@Override
	public V put(K key, V value) {
		return cache.getAndPut(key, value);
	}

	@Override
	public V remove(Object key) {
		return cache.getAndRemove((K) key);
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> m) {
		cache.putAll(m);
	}

	@Override
	public void clear() {
		cache.clear();
	}

	public void close() {
		if (!cache.isClosed()) {
			cache.clear();
			cache.close();
		}
	}

	@Override
	public Set<K> keySet() {
		throw new UnsupportedOperationException("This operation is not supported yet.");
	}

	@Override
	public Collection<V> values() {
		throw new UnsupportedOperationException("This operation is not supported yet.");
	}

	@Override
	public Set<Entry<K, V>> entrySet() {
		throw new UnsupportedOperationException("This operation is not supported yet.");
	}
}
