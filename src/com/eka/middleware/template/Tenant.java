package com.eka.middleware.template;

import java.util.Map;

import com.eka.middleware.heap.HashMap;
import com.eka.middleware.service.ServiceUtils;

public class Tenant {
private final String name;
public final String id;
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

public static Tenant getTempTenant(String name) {
	return new Tenant(name);
}

public static boolean exists(String name) {
	if(name==null || name.trim().length()==0)
		return false;
	return tenantMap.get(name)!=null;
}

private Tenant(String name) {
	this.name=name;
	this.id = ServiceUtils.generateUUID(System.currentTimeMillis()+"");
}

public String getName() {
	return name;
}

}
