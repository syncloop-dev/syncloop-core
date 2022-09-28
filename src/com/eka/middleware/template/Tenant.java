package com.eka.middleware.template;

import java.util.Map;

import com.eka.middleware.heap.HashMap;

public class Tenant {
private String name;
private static Map<String, Tenant> tenantMap=new HashMap();

public static Tenant getTenant(String name) {
	if(name==null) {
		name="DEFAULT";
		tenantMap.put(name, new Tenant(""));
	}
	if(tenantMap.get(name)==null) {
		tenantMap.put(name, new Tenant(name));
	}
	return tenantMap.get(name);
}

private Tenant(String name) {
	this.name=name;
}

public String getName() {
	return name;
}

public void setName(String name) {
	this.name = name;
}

}
