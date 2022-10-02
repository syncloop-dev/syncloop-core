package com.eka.middleware.auth;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.pac4j.core.config.Config;
import org.pac4j.undertow.handler.CallbackHandler;
import org.pac4j.undertow.handler.SecurityHandler;

import com.eka.middleware.auth.pac4j.AuthConfigFactory;
import com.eka.middleware.auth.pac4j.AuthHandlers;
import com.eka.middleware.template.Tenant;

import io.undertow.server.handlers.PathHandler;

public class Security {
	private static final PathHandler path = new PathHandler();
	public static final String defaultTenantPage="/files/gui/middleware/pub/server/ui/tenant/newTenant.html";
	public static final String defaultWelcomePage="/files/gui/middleware/pub/server/ui/welcome/index.html";
	private static final Set<String> paths=new HashSet<String>();
	private static final Set<String> publicPaths=new HashSet<String>();
	private static Map<String, List<String>> publicPrefixPathsMap=new ConcurrentHashMap();
	private static Map<String, List<String>> publicExactPathsMap=new ConcurrentHashMap();
	private static Map<String, String> defaultNewUserPathMap=new ConcurrentHashMap();
	public static Set<String> getPath() {
		return paths;
	}
public static PathHandler init() throws Exception{
	path.addExactPath("/", SecurityHandler.build(AuthHandlers.defaultHandler(), AuthConfigFactory.getAnonymousClientConfig()));
	addPublicPrefixPath("/files/gui/middleware/pub/server/ui/welcome/", Tenant.getTenant("default"));
	addPublicPrefixPath("/files/gui/middleware/pub/server/ui/welcome/", Tenant.getTenant("dev"));
	path.addExactPath("/jwt", SecurityHandler.build(AuthHandlers.mainHandler, AuthConfigFactory.getBasicDirectAuthConfig()));
	paths.add("/jwt");
	path.addExactPath("/JWT", SecurityHandler.build(AuthHandlers.mainHandler, AuthConfigFactory.getBasicDirectAuthConfig()));
	paths.add("/JWT");
	
//	path.addPrefixPath("/public", SecurityHandler.build(AuthHandlers.defaultHandler(), AuthConfigFactory.getAnonymousClientConfig()));
//	publicPaths.add("/public");
	path.addPrefixPath("/", AuthHandlers.mainHandler);
	paths.add("/");
	
    return path;
}

public static void addPublicPrefixPath(String resourcePrefixPath, Tenant tenant) {
	List<String> tennatPublicPaths=publicPrefixPathsMap.get(tenant.getName());
	if(tennatPublicPaths==null || tennatPublicPaths.size()==0) {
		tennatPublicPaths=new ArrayList();
		publicPrefixPathsMap.put(tenant.getName(), tennatPublicPaths);
	}
	if(!tennatPublicPaths.contains(resourcePrefixPath)) {
		tennatPublicPaths.add(resourcePrefixPath);
		path.addPrefixPath(resourcePrefixPath, SecurityHandler.build(AuthHandlers.indexHandler(), AuthConfigFactory.getAnonymousClientConfig()));
		path.addPrefixPath("/tenant/"+tenant.getName()+resourcePrefixPath, SecurityHandler.build(AuthHandlers.indexHandler(), AuthConfigFactory.getAnonymousClientConfig()));
	}
}

public static void addPublicExactPath(String resourceExactPath, Tenant tenant) {
	List<String> tennatPublicPaths=publicExactPathsMap.get(tenant.getName());
	if(tennatPublicPaths==null || tennatPublicPaths.size()==0) {
		tennatPublicPaths=new ArrayList();
		publicExactPathsMap.put(tenant.getName(), tennatPublicPaths);
	}
	if(!tennatPublicPaths.contains(resourceExactPath)) {
		tennatPublicPaths.add(resourceExactPath);
		path.addExactPath(resourceExactPath, SecurityHandler.build(AuthHandlers.indexHandler(), AuthConfigFactory.getAnonymousClientConfig()));
		path.addExactPath("/tenant/"+tenant.getName()+"/"+resourceExactPath, SecurityHandler.build(AuthHandlers.indexHandler(), AuthConfigFactory.getAnonymousClientConfig()));
	}
}

public static boolean isPublic(String path,String tenantName) {
	List<String> tennatPublicPaths=publicPrefixPathsMap.get(tenantName); 
	if(tennatPublicPaths!=null) {
		for (String resourcePath : tennatPublicPaths) {
			if(path.startsWith(resourcePath))
				return true;
		}
	}
	if(publicExactPathsMap.get(tenantName)!=null && publicExactPathsMap.get(tenantName).contains(path))
		return true;
	return false;
}

public static void addDefaultNewUserPath(String path,String tenantName) {
	defaultNewUserPathMap.put(tenantName,path);
}

public static void removeDefaultNewUserPath(String path,String tenantName) {
	defaultNewUserPathMap.remove(tenantName);
}

public static String gerDefaultNewUserPath(String tenantName) {
	return defaultNewUserPathMap.get(tenantName);
}

public static String addExternalOIDCAuthorizationServer(Map<String, Object> props,String resourcePath) throws Exception {
	Config conf= AuthConfigFactory.getOIDCAuthClientConfig(props);
	paths.add(resourcePath);
	path.addExactPath(resourcePath, SecurityHandler.build(AuthHandlers.mainHandler, conf));
	paths.add("/callback");
	path.addExactPath("/callback", CallbackHandler.build(conf, null));
	return "Login url 'https://<server>:<port>"+resourcePath+"'";
}

public static List<String> getPublicPrefixPaths(String tenantName){
	return publicPrefixPathsMap.get(tenantName);
}

public static List<String> getPublicExactPaths(String tenantName){
	return publicExactPathsMap.get(tenantName);
}

public static void removePath(String resourcePath) throws Exception {
	path.removeExactPath(resourcePath);
	path.removePrefixPath(resourcePath);
}

}
