package com.eka.middleware.auth.manager;

import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.profile.UserProfile;
import org.pac4j.core.util.Pac4jConstants;
import org.pac4j.jwt.profile.JwtGenerator;

import com.eka.middleware.auth.AuthAccount;
import com.eka.middleware.auth.UserProfileManager;
import com.eka.middleware.auth.pac4j.AuthConfigFactory;
import com.eka.middleware.auth.pac4j.AuthHandlers;
import com.eka.middleware.service.ServiceUtils;

import io.undertow.server.HttpServerExchange;

public class JWT {
	private final static JwtGenerator jwtGenerator = new JwtGenerator(AuthConfigFactory.secConf);
	public static String generate(HttpServerExchange exchange) {
		String token = "";
		UserProfile up =AuthHandlers.getProfile(exchange);
        if (up != null) {
            final var profile = new CommonProfile();
            String id=(String)up.getAttribute("email");
            if(id==null)
            	id=up.getId();
            AuthAccount authacc=UserProfileManager.getAccount(id, up);
            //Map<String, Object> tempProfile= UserProfileManager.createDefaultProfile(up, null);
            profile.setId(up.getId());
            profile.addAttribute(Pac4jConstants.USERNAME, up.getId());
            profile.addAttribute("tenant", authacc.getAuthProfile().get("tenant"));
            profile.addAttribute("groups", authacc.getAuthProfile().get("groups"));
            token = jwtGenerator.generate(profile);
            token=ServiceUtils.encrypt(token, AuthConfigFactory.KEY);
        }
        return token;
	}
	
}
