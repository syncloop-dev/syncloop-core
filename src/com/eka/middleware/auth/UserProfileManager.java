package com.eka.middleware.auth;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.pac4j.core.profile.UserProfile;

import com.eka.middleware.service.PropertyManager;
import com.eka.middleware.service.ServiceUtils;
import com.eka.middleware.template.SystemException;

import io.undertow.security.idm.Account;
import io.undertow.security.idm.Credential;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.idm.PasswordCredential;

public class UserProfileManager implements IdentityManager {
	private static final Map<String, Object> users = new ConcurrentHashMap<String, Object>();
	private static UserProfileManager upm=null;
	
	public static final Map<String, Object> getUsers() throws SystemException {
		try {
			byte bytes[] = PropertyManager.readConfigurationFile("profiles.json");
			if (bytes!=null) {
				String json = new String(bytes);
				final Map<String, Object> map = ServiceUtils.jsonToMap(json);
				final Map<String, Object> tanent = ((Map<String, Object>)map.get("dev"));
				tanent.forEach((k,v)->{
					Map<String, Object> user = (Map<String, Object>) v;
					String pass=user.get("password").toString();
					if(!pass.startsWith("[#]")) {
						String passHash="[#]"+ServiceUtils.generateUUID(pass+k);
						user.put("password", passHash);
					}
				});
				users.putAll(map);
				json=ServiceUtils.toPrettyJson(users);
				PropertyManager.writeConfigurationFile("profiles.json", json.getBytes());
			}
		} catch (Exception e) {
			throw new SystemException("EKA_MWS_1001", e);
		}
		return users;
	}
	
	
	
	public static UserProfileManager create() throws SystemException{
		if(upm==null)
			upm=new UserProfileManager();
		return upm;
	}
	
	public static UserProfileManager getUserProfileManager() {
		return upm;
	}
	
	private UserProfileManager() throws SystemException {
		getUsers();
	}

	@Override
	public Account verify(Account account) {
		// An existing account so for testing assume still valid.
		return account;
	}

	@Override
	public AuthAccount verify(String id, Credential credential) {
		AuthAccount account = getAccount(id);
		if (account != null && verifyCredential(account, credential)) {
			return account;
		}

		return null;
	}

	@Override
	public Account verify(Credential credential) {
		// TODO Auto-generated method stub
		return null;
	}

	private boolean verifyCredential(Account account, Credential credential) {
		if (credential instanceof PasswordCredential) {
			char[] password = ((PasswordCredential) credential).getPassword();
			Map<String, Object> tanent = (Map<String, Object>) users.get("dev");
			String userId=account.getPrincipal().getName();
			Map<String, Object> user = (Map<String, Object>) tanent.get(userId);
			if (user == null) {
				return false;
			}
			char[] expectedPassword = user.get("password").toString().toCharArray();
			String pass=new String(password);
			String passHash="[#]"+ServiceUtils.generateUUID(pass+userId);
			return Arrays.equals(passHash.toCharArray(), expectedPassword);
		}
		return false;
	}

	public AuthAccount getAccount(UserProfile up) {
		final String id=up.getId();
		return getAccount(id);
	}
	
	private AuthAccount getAccount(final String id) {
		final Map<String, Object> tanent = (Map<String, Object>) users.get("dev");
		Map<String, Object> user = (Map<String, Object>) tanent.get(id);
		
		if (user!=null) {
			final Map<String, Object> profile=(Map<String, Object>) user.get("profile");
			AuthAccount authAccount=new AuthAccount(id);
			authAccount.setProfile(profile);
			return authAccount;
		}else {
			final Map<String, Object> profile=createDefaultProfile();
			AuthAccount authAccount=new AuthAccount(id);
			authAccount.setProfile(profile);
			return authAccount;
		}
	}
	
	private Map<String, Object> createDefaultProfile(){
		Map<String, Object> profile=new HashMap<String, Object>();
		String groups[]= {"Guest"};
		profile.put("groups", groups);
		return profile;
	}
}
