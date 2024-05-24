package com.eka.lite.heap;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class HashMap<K,V> implements Map<K, V>{

	private Map<K, V> map;
	
	public HashMap(){
		map=new java.util.HashMap<K,V>();
	}

	@Override
	public int size() {
		// TODO Auto-generated method stub
		return map.size();
	}

	@Override
	public boolean isEmpty() {
		// TODO Auto-generated method stub
		return map.isEmpty();
	}

	@Override
	public boolean containsKey(Object key) {
		// TODO Auto-generated method stub
		return map.containsKey(key);
	}

	@Override
	public boolean containsValue(Object value) {
		// TODO Auto-generated method stub
		return map.containsValue(value);
	}

	@Override
	public V get(Object key) {
		// TODO Auto-generated method stub
		return (V) map.get(key);
	}

	@Override
	public V put(K key, V value) {
		// TODO Auto-generated method stub
		return (V) map.put(key, value);
	}

	@Override
	public V remove(Object key) {
		// TODO Auto-generated method stub
		return (V) map.remove(key);
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> m) {
		// TODO Auto-generated method stub
		map.putAll(m);
	}

	@Override
	public void clear() {
		map.clear();
		
	}

	@Override
	public Set<K> keySet() {
		// TODO Auto-generated method stub
		return map.keySet();
	}

	@Override
	public Collection<V> values() {
		// TODO Auto-generated method stub
		return map.values();
	}

	@Override
	public Set<Entry<K, V>> entrySet() {
		// TODO Auto-generated method stub
		return map.entrySet();
	}
}
