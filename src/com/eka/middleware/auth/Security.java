package com.eka.middleware.auth;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.pac4j.core.config.Config;
import org.pac4j.undertow.handler.CallbackHandler;
import org.pac4j.undertow.handler.SecurityHandler;

import com.eka.middleware.auth.pac4j.AuthConfigFactory;
import com.eka.middleware.auth.pac4j.AuthHandlers;

import io.undertow.server.handlers.PathHandler;

public class Security {
	private static final PathHandler path = new PathHandler();
	private static final Set<String> paths=new HashSet<String>();
	public static Set<String> getPath() {
		return paths;
	}
public static PathHandler init() throws Exception{
	
    //path.addExactPath("/login", SecurityHandler.build(AuthHandlers.indexHandler(), AuthConfigFactory.getAnonymousClientConfig()));
	path.addPrefixPath("/files/gui/middleware/pub/server/ui/welcome/", SecurityHandler.build(AuthHandlers.indexHandler(), AuthConfigFactory.getAnonymousClientConfig()));
	paths.add("/files/gui/middleware/pub/server/ui/welcome/");
	path.addExactPath("/jwt", SecurityHandler.build(AuthHandlers.mainHandler, AuthConfigFactory.getBasicDirectAuthConfig()));
	paths.add("/jwt");
	path.addExactPath("/JWT", SecurityHandler.build(AuthHandlers.mainHandler, AuthConfigFactory.getBasicDirectAuthConfig()));
	paths.add("/JWT");
	path.addPrefixPath("/", AuthHandlers.mainHandler);
	paths.add("/");
    return path;
}

public static void addPublicExactPath(String resourceExactPath) {
	paths.add(resourceExactPath);
	path.addExactPath(resourceExactPath, SecurityHandler.build(AuthHandlers.indexHandler(), AuthConfigFactory.getAnonymousClientConfig()));
}

public static void addPublicPrefixPath(String resourcePrefixPath) {
	paths.add(resourcePrefixPath);
	path.addExactPath(resourcePrefixPath, SecurityHandler.build(AuthHandlers.indexHandler(), AuthConfigFactory.getAnonymousClientConfig()));
}

public static String addExternalOIDCAuthorizationServer(Map<String, Object> props,String resourcePath) throws Exception {
	Config conf= AuthConfigFactory.getOIDCAuthClientConfig(props);
	paths.add(resourcePath);
	path.addExactPath(resourcePath, SecurityHandler.build(AuthHandlers.mainHandler, conf));
	paths.add("/callback");
	path.addExactPath("/callback", CallbackHandler.build(conf, null));
	return "Login url 'https://<server>:<port>"+resourcePath+"'";
}

public static void removePath(String resourcePath) throws Exception {
	path.removeExactPath(resourcePath);
	path.removePrefixPath(resourcePath);
}

}
