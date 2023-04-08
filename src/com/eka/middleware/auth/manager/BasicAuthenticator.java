package com.eka.middleware.auth.manager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.credentials.Credentials;
import org.pac4j.core.credentials.UsernamePasswordCredentials;
import org.pac4j.core.credentials.authenticator.Authenticator;
import org.pac4j.core.exception.CredentialsException;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.util.CommonHelper;
import org.pac4j.core.util.Pac4jConstants;
import org.pac4j.http.credentials.authenticator.test.SimpleTestUsernamePasswordAuthenticator;

import com.eka.middleware.auth.AuthAccount;
import com.eka.middleware.auth.UserProfileManager;
import com.eka.middleware.server.MiddlewareServer;
import com.eka.middleware.service.ServiceUtils;
import com.eka.middleware.template.SystemException;

import io.undertow.security.idm.PasswordCredential;

public class BasicAuthenticator extends SimpleTestUsernamePasswordAuthenticator {

	private static UserProfileManager identityManager=null;
	public static Logger LOGGER = LogManager.getLogger(BasicAuthenticator.class);
    	
	@Override
    public void validate(final Credentials cred, final WebContext context, final SessionStore sessionStore) {
		LOGGER.trace("Validating user...........");
        if (cred == null) {
            throw new CredentialsException("No credential");
        }
        LOGGER.trace("IdentifyManager...........");
        if(identityManager==null)
    		try {
    			identityManager = UserProfileManager.create();
    			UserProfileManager.getUsers();
    			LOGGER.info("Users loaded...........");
    		} catch (SystemException e) {
    			ServiceUtils.printException("UserProfileManager issue", e);
    		}
        LOGGER.trace("Casting cred...........");
        final var credentials = (UsernamePasswordCredentials) cred;
        var username = credentials.getUsername();
        LOGGER.trace("User forund: "+username+"...........");
        var password = credentials.getPassword();
        LOGGER.trace("Password forund: *******...........");
        if (CommonHelper.isBlank(username)) {
        	CredentialsException ex=new CredentialsException("Username cannot be blank");
        	ServiceUtils.printException("User is blank", ex);
            throw ex;
        }
        if (CommonHelper.isBlank(password)) {
        	CredentialsException ex=new CredentialsException("Password cannot be blank");
        	ServiceUtils.printException("Password is blank", ex);
            throw ex;
        }
        LOGGER.trace("Veify user: "+username+"...........");
        AuthAccount account=identityManager.verify(username, new PasswordCredential(password.toCharArray()));

        if (account==null) {
        	CredentialsException ex= new CredentialsException("Username : '" + username + "' does not match password");
            ServiceUtils.printException("Credentials match failed", ex);
            throw ex;
        }
        
        LOGGER.trace("Account user: "+account.getUserId()+"...........");
        
        final var profile = new CommonProfile();
        profile.setId(username);
        profile.addAttribute(Pac4jConstants.USERNAME, account.getUserId());
        credentials.setUserProfile(profile);
        LOGGER.trace("Adding profile: "+profile+"...........");
    }//*/
}
