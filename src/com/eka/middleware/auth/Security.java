package com.eka.middleware.auth;

import org.pac4j.undertow.handler.CallbackHandler;
import org.pac4j.undertow.handler.SecurityHandler;

import com.eka.middleware.auth.pac4j.AuthConfigFactory;
import com.eka.middleware.auth.pac4j.AuthHandlers;

import io.undertow.server.handlers.PathHandler;

public class Security {
public static PathHandler init() throws Exception{
	PathHandler path = new PathHandler();
    //path.addExactPath("/login", SecurityHandler.build(AuthHandlers.indexHandler(), AuthConfigFactory.getAnonymousClientConfig()));
	path.addPrefixPath("/files/gui/middleware/pub/server/ui/welcome/", SecurityHandler.build(AuthHandlers.indexHandler(), AuthConfigFactory.getAnonymousClientConfig()));
	path.addExactPath("/OIDC", SecurityHandler.build(AuthHandlers.mainHandler, AuthConfigFactory.getOIDCAuthClientConfig()));
	path.addExactPath("/oidc", SecurityHandler.build(AuthHandlers.mainHandler, AuthConfigFactory.getOIDCAuthClientConfig()));
	path.addExactPath("/jwt", SecurityHandler.build(AuthHandlers.mainHandler, AuthConfigFactory.getBasicDirectAuthConfig()));
	path.addExactPath("/JWT", SecurityHandler.build(AuthHandlers.mainHandler, AuthConfigFactory.getBasicDirectAuthConfig()));
	path.addExactPath("/callback", CallbackHandler.build(AuthConfigFactory.getOIDCAuthClientConfig(), null));
    path.addPrefixPath("/", AuthHandlers.mainHandler);
    return path;
}
}
