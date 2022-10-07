package com.eka.middleware.auth.manager;

import java.util.Map;

import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.profile.UserProfile;
import org.pac4j.core.util.Pac4jConstants;
import org.pac4j.jwt.config.signature.SecretSignatureConfiguration;
import org.pac4j.jwt.profile.JwtGenerator;

import com.eka.middleware.auth.AuthAccount;
import com.eka.middleware.auth.UserProfileManager;
import com.eka.middleware.auth.pac4j.AuthHandlers;
import com.eka.middleware.server.MiddlewareServer;

import io.undertow.server.HttpServerExchange;

public class JWT {

	public static String generate(HttpServerExchange exchange) {
		String token = "";
		UserProfile up =AuthHandlers.getProfile(exchange);
        if (up != null) {
            final JwtGenerator jwtGenerator = new JwtGenerator(new SecretSignatureConfiguration(MiddlewareServer.JWT_MASALA));
            final var profile = new CommonProfile();
            AuthAccount authacc=UserProfileManager.getAccount(up.getId(), up);
            //Map<String, Object> tempProfile= UserProfileManager.createDefaultProfile(up, null);
            profile.setId(up.getId());
            profile.addAttribute(Pac4jConstants.USERNAME, up.getId());
            profile.addAttribute("tenant", authacc.getAuthProfile().get("tenant"));
            profile.addAttribute("groups", authacc.getAuthProfile().get("groups"));
            token = jwtGenerator.generate(profile);
        }
        return token;
	}
	
}
