package com.eka.middleware.auth.manager;

import io.undertow.server.HttpServerExchange;

public class AuthorizationRequest {
public static String getContent(final HttpServerExchange exchange, final String requestPath) {
	switch (requestPath) {
	case "GET/JWT":
		return JWT.generate(exchange);
	case "GET/OIDC":
		return JWT.generate(exchange);
	case "GET/SAML":
		return JWT.generate(exchange);
	default:
		break;
	}
	return null;
}
}
//SecurityHandler.build(DemoHandlers.protectedIndex, config, "OidcClient"))