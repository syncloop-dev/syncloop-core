package com.eka.middleware.auth.manager;

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
import com.eka.middleware.template.SystemException;

import io.undertow.security.idm.PasswordCredential;

public class BasicAuthenticator extends SimpleTestUsernamePasswordAuthenticator {

	private static UserProfileManager identityManager=null;
	/*   @Override
    public void validate(final Credentials cred, final WebContext context, final SessionStore sessionStore) {
        if (cred == null) {
            throw new CredentialsException("No credential");
        }
        
        if(identityManager==null)
		try {
			identityManager = UserProfileManager.create();
			UserProfileManager.getUsers();
		} catch (SystemException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        
        final var credentials = (UsernamePasswordCredentials) cred;
        var username = credentials.getUsername();
        var password = credentials.getPassword();
        if (CommonHelper.isBlank(username)) {
            throw new CredentialsException("Username cannot be blank");
        }
        if (CommonHelper.isBlank(password)) {
            throw new CredentialsException("Password cannot be blank");
        }
        
        AuthAccount account=identityManager.verify(username, new PasswordCredential(password.toCharArray()));
        
        if (account==null) {
            throw new CredentialsException("Username : '" + username + "' does not match password");
        }
        final var profile = new CommonProfile();
        profile.setId(username);
        profile.addAttribute(Pac4jConstants.USERNAME, username);
        credentials.setUserProfile(profile);
    }//*/
    	
	@Override
    public void validate(final Credentials cred, final WebContext context, final SessionStore sessionStore) {
        if (cred == null) {
            throw new CredentialsException("No credential");
        }
        if(identityManager==null)
    		try {
    			identityManager = UserProfileManager.create();
    			UserProfileManager.getUsers();
    		} catch (SystemException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		}
        final var credentials = (UsernamePasswordCredentials) cred;
        var username = credentials.getUsername();
        var password = credentials.getPassword();
        if (CommonHelper.isBlank(username)) {
            throw new CredentialsException("Username cannot be blank");
        }
        if (CommonHelper.isBlank(password)) {
            throw new CredentialsException("Password cannot be blank");
        }
        
        AuthAccount account=identityManager.verify(username, new PasswordCredential(password.toCharArray()));
        
        if (account==null) {
            throw new CredentialsException("Username : '" + username + "' does not match password");
        }
        //System.out.println("&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&Account Found&&&&&&&&&&&&&&&&&&&&&&&&&&&&&");
        //if (CommonHelper.areNotEquals(username, password)) {
          //  throw new CredentialsException("Username : '" + username + "' does not match password");
        //}
        final var profile = new CommonProfile();
        profile.setId(username);
        profile.addAttribute(Pac4jConstants.USERNAME, username);
        credentials.setUserProfile(profile);
    }//*/
}
