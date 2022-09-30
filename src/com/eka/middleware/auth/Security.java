package com.eka.middleware.auth;

import java.util.Map;
import java.util.Properties;

import org.pac4j.core.config.Config;
import org.pac4j.undertow.handler.CallbackHandler;
import org.pac4j.undertow.handler.SecurityHandler;

import com.eka.middleware.auth.pac4j.AuthConfigFactory;
import com.eka.middleware.auth.pac4j.AuthHandlers;

import io.undertow.server.handlers.PathHandler;

public class Security {
	private static final PathHandler path = new PathHandler();
public static PathHandler init() throws Exception{
	
    //path.addExactPath("/login", SecurityHandler.build(AuthHandlers.indexHandler(), AuthConfigFactory.getAnonymousClientConfig()));
	path.addPrefixPath("/files/gui/middleware/pub/server/ui/welcome/", SecurityHandler.build(AuthHandlers.indexHandler(), AuthConfigFactory.getAnonymousClientConfig()));
	path.addExactPath("/jwt", SecurityHandler.build(AuthHandlers.mainHandler, AuthConfigFactory.getBasicDirectAuthConfig()));
	path.addExactPath("/JWT", SecurityHandler.build(AuthHandlers.mainHandler, AuthConfigFactory.getBasicDirectAuthConfig()));
    path.addPrefixPath("/", AuthHandlers.mainHandler);
    return path;
}

public static void addPublicExactPath(String resourceExactPath) {
	path.addExactPath(resourceExactPath, SecurityHandler.build(AuthHandlers.indexHandler(), AuthConfigFactory.getAnonymousClientConfig()));
}

public static void addPublicPrefixPath(String resourcePrefixPath) {
	path.addExactPath(resourcePrefixPath, SecurityHandler.build(AuthHandlers.indexHandler(), AuthConfigFactory.getAnonymousClientConfig()));
}

public static String addExternalOIDCAuthorizationServer(Map<String, Object> props,String name) throws Exception {
	
	Config conf= AuthConfigFactory.getOIDCAuthClientConfig(props);
	path.addExactPath("/execute/"+name, SecurityHandler.build(AuthHandlers.mainHandler, conf));
	path.addExactPath("/callback", CallbackHandler.build(conf, null));
	return "Login url 'https://<server>:<port>/execute/"+name+"'";
}

public static void removePath(String resourcePath) throws Exception {
	path.removeExactPath(resourcePath);
	path.removePrefixPath(resourcePath);
}

}
