package com.eka.middleware.auth.manager;

import org.apache.commons.lang3.StringUtils;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.profile.UserProfile;
import org.pac4j.core.util.Pac4jConstants;
import org.pac4j.jwt.profile.JwtGenerator;

import com.eka.middleware.auth.AuthAccount;
import com.eka.middleware.auth.Security;
import com.eka.middleware.auth.UserProfileManager;
import com.eka.middleware.auth.pac4j.AuthConfigFactory;
import com.eka.middleware.auth.pac4j.AuthHandlers;
import com.eka.middleware.service.PropertyManager;
import com.eka.middleware.service.ServiceUtils;
import com.eka.middleware.template.Tenant;

import io.undertow.server.HttpServerExchange;

import java.util.Date;
import java.util.Deque;

public class JWT {
	public static String generate(HttpServerExchange exchange) {
		String tenantName=ServiceUtils.setupRequestPath(exchange);
		String token = "";
		UserProfile up =AuthHandlers.getProfile(exchange);
        if (up != null) {
            final var profile = new CommonProfile();
            String id=(String)up.getAttribute("email");
            if(id==null)
            	id=up.getId();
            AuthAccount authacc=UserProfileManager.getAccount(id, up);
            //Map<String, Object> tempProfile= UserProfileManager.createDefaultProfile(up, null);
            profile.setId(id);
            profile.addAttribute(Pac4jConstants.USERNAME, up.getId());
            profile.addAttribute("tenant", authacc.getAuthProfile().get("tenant"));
            profile.addAttribute("groups", authacc.getAuthProfile().get("groups"));
            Tenant tenant = Tenant.getTenant(tenantName);

            Deque<String> stringDeque = exchange.getQueryParameters().get("expiration_time");
            String expirationTimeStr = (null == stringDeque) ? null : stringDeque.pop();
            int expirationTime = 8;
            if (StringUtils.isNotBlank(expirationTimeStr)) {
                expirationTime = Integer.parseInt(expirationTimeStr);
            }

            Date expiryDate = new Date();
            expiryDate = ServiceUtils.addHoursToDate(expiryDate, expirationTime == -1 ? Integer.MAX_VALUE : expirationTime);

            tenant.jwtGenerator.setExpirationTime(expiryDate);
            token = tenant.jwtGenerator.generate(profile);
//            try {
//            	String publicKey=PropertyManager.getGlobalProperties(tenantName).getProperty(Security.PUBLIC_PROPERTY_KEY_NAME);
//            	token=Security.getSecureString(token,publicKey);
//			} catch (Exception e) {
//				ServiceUtils.printException("Could not encrypt JWT using public key.", e);
//			}
            token=ServiceUtils.encrypt(token, tenantName);
        }
        return token;
	}
	
}
