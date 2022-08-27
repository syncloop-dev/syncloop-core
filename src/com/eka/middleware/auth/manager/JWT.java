package com.eka.middleware.auth.manager;

import org.pac4j.core.profile.UserProfile;
import org.pac4j.jwt.config.signature.SecretSignatureConfiguration;
import org.pac4j.jwt.profile.JwtGenerator;
import org.pac4j.undertow.account.Pac4jAccount;

import com.eka.middleware.auth.pac4j.AuthHandlers;
import com.eka.middleware.server.MiddlewareServer;

import io.undertow.server.HttpServerExchange;

public class JWT {

	public static String generate(HttpServerExchange exchange) {
		String token = "";
		UserProfile up =AuthHandlers.getProfile(exchange);
        if (up != null) {
            final JwtGenerator jwtGenerator = new JwtGenerator(new SecretSignatureConfiguration(MiddlewareServer.JWT_MASALA));
            //Map<String, Object> map=new HashMap<String, Object>(account.getProfile());
            //map.put("sub", "1234567890");
            //map.put("exp", 1900000000L);
            token = jwtGenerator.generate(up);
        }
        return token;
	}
	
}
