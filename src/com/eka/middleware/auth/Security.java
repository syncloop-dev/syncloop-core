package com.eka.middleware.auth;

import java.util.Properties;

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
	path.addExactPath("/callback", CallbackHandler.build(AuthConfigFactory.getOIDCAuthClientConfig(), null));
    path.addPrefixPath("/", AuthHandlers.mainHandler);
    return path;
}

public static void addPublicExactPath(String resourceExactPath) {
	path.addExactPath(resourceExactPath, SecurityHandler.build(AuthHandlers.indexHandler(), AuthConfigFactory.getAnonymousClientConfig()));
}

public static void addPublicPrefixPath(String resourcePrefixPath) {
	path.addExactPath(resourcePrefixPath, SecurityHandler.build(AuthHandlers.indexHandler(), AuthConfigFactory.getAnonymousClientConfig()));
}

public static void addExternalOIDCAuthorizationServer(String resourcePath,Properties props) throws Exception {
	path.addExactPath(resourcePath.toUpperCase(), SecurityHandler.build(AuthHandlers.mainHandler, AuthConfigFactory.getOIDCAuthClientConfig(props)));
	path.addExactPath(resourcePath.toLowerCase(), SecurityHandler.build(AuthHandlers.mainHandler, AuthConfigFactory.getOIDCAuthClientConfig(props)));
	path.addExactPath(resourcePath, SecurityHandler.build(AuthHandlers.mainHandler, AuthConfigFactory.getOIDCAuthClientConfig(props)));
}

public static void addExternalOIDCAuthorizationServer() throws Exception {
	path.addExactPath("oidc".toUpperCase(), SecurityHandler.build(AuthHandlers.mainHandler, AuthConfigFactory.getOIDCAuthClientConfig()));
	path.addExactPath("oidc", SecurityHandler.build(AuthHandlers.mainHandler, AuthConfigFactory.getOIDCAuthClientConfig()));
	
}

}
