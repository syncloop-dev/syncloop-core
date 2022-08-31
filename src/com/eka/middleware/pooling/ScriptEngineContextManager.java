package com.eka.middleware.pooling;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.polyglot.Context;

public class ScriptEngineContextManager {
private static final Map<String, Context> contextMap=new ConcurrentHashMap<>();
public static synchronized Context getContext(String name) {
	Context ctx=contextMap.get(name);
	if(ctx==null) {
		ctx=Context.create("js");
		contextMap.put(name, ctx);
//		synchronized (ctx) {
//			if(contextMap.get(name)==null) {
//				contextMap.put(name, ctx);
//			}	
//		}
	}
	return ctx;
}

public static Context findContext(String name) {
	Context ctx=contextMap.get(name);
	return ctx;
}

public static Context removeContext(String name) {
	Context ctx=contextMap.get(name);
	if(ctx!=null) {
		//contextMap.remove(name, ctx);
	}
	return ctx;
}

public static void clear() {
	contextMap.clear();
}

}
