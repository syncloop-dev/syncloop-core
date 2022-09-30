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
	private static final Map<String, Object> usersMap = new ConcurrentHashMap<String, Object>();
	private static UserProfileManager upm = null;

	public static final Map<String, Object> getUsers() throws SystemException {
		if (PropertyManager.hasfileChanged("profiles.json") || usersMap.size() == 0) {
			try {
				byte bytes[] = PropertyManager.readConfigurationFile("profiles.json");
				if (bytes != null) {
					String json = new String(bytes);
					final Map<String, Object> map = ServiceUtils.jsonToMap(json);
					final Map<String, Object> umap = ((Map<String, Object>) map.get("users"));
					umap.forEach((k, v) -> {
						Map<String, Object> user = (Map<String, Object>) v;
						if (user.get("password") != null) {
							String pass = user.get("password").toString();
							if(pass.trim().length()==0)
								user.remove("password");
							if (!pass.startsWith("[#]")) {
								String passHash = "[#]" + ServiceUtils.generateUUID(pass + k);
								user.put("password", passHash);
							}
						}
					});
					usersMap.clear();
					usersMap.putAll(umap);
					json = ServiceUtils.toPrettyJson(map);
					PropertyManager.writeConfigurationFile("profiles.json", json.getBytes());
					PropertyManager.hasfileChanged("profiles.json");// Called again to verify and update the new changed
																	// file datetime.
				}
			} catch (Exception e) {
				throw new SystemException("EKA_MWS_1001", e);
			}
		}
		return usersMap;
	}

	public static void addUser(AuthAccount account) throws SystemException {
		try {
			byte bytes[] = PropertyManager.readConfigurationFile("profiles.json");
			String json = new String(bytes);
			final Map<String, Object> map = ServiceUtils.jsonToMap(json);
			final Map<String, Object> umap = ((Map<String, Object>) map.get("users"));
			Map<String, Object> user = new HashMap();
			user.put("profile", account.getAuthProfile());
			umap.put(account.getUserId(), user);
			json = ServiceUtils.toPrettyJson(map);
			PropertyManager.writeConfigurationFile("profiles.json", json.getBytes());
		} catch (Exception e) {
			throw new SystemException("EKA_MWS_1001", e);
		}
	}

	public static void removeUser(String id) throws SystemException {
		try {
			byte bytes[] = PropertyManager.readConfigurationFile("profiles.json");
			String json = new String(bytes);
			final Map<String, Object> map = ServiceUtils.jsonToMap(json);
			final Map<String, Object> umap = ((Map<String, Object>) map.get("users"));
			umap.remove(id);
			json = ServiceUtils.toPrettyJson(map);
			PropertyManager.writeConfigurationFile("profiles.json", json.getBytes());
		} catch (Exception e) {
			throw new SystemException("EKA_MWS_1001", e);
		}
	}

	public static UserProfileManager create() throws SystemException {
		if (upm == null)
			upm = new UserProfileManager();
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
		try {
			if (account != null && verifyCredential(account, credential)) {
				return account;
			}
		} catch (SystemException e) {
			ServiceUtils.printException("Login exception for " + id, e);
		}

		return null;
	}

	@Override
	public Account verify(Credential credential) {
		// TODO Auto-generated method stub
		return null;
	}

	private boolean verifyCredential(Account account, Credential credential) throws SystemException {
		if (credential instanceof PasswordCredential) {
			char[] password = ((PasswordCredential) credential).getPassword();
			Map<String, Object> usersMap = (Map<String, Object>) getUsers();
			String userId = account.getPrincipal().getName();
			Map<String, Object> user = (Map<String, Object>) usersMap.get(userId);
			if (user == null) {
				return false;
			}
			if(user.get("password")==null)
				return false;
			char[] expectedPassword = user.get("password").toString().toCharArray();
			String pass = new String(password);
			String passHash = "[#]" + ServiceUtils.generateUUID(pass + userId);
			return Arrays.equals(passHash.toCharArray(), expectedPassword);
		}
		return false;
	}

	public AuthAccount getAccount(UserProfile up) {
		String id = (String) up.getId();
		if(up.getAttribute("email")!=null)
			id=(String)up.getAttribute("email");
		return getAccount(id);
	}

	private AuthAccount getAccount(final String id) {
		Map<String, Object> usersMap = null;
		try {
			usersMap = (Map<String, Object>) getUsers();
		} catch (SystemException e) {
			ServiceUtils.printException("Could not load users list: " + id, e);
			return null;
		}
		Map<String, Object> user = (Map<String, Object>) usersMap.get(id);

		if (user != null) {
			final Map<String, Object> profile = (Map<String, Object>) user.get("profile");
			AuthAccount authAccount = new AuthAccount(id);
			authAccount.setProfile(profile);
			return authAccount;
		} else {
			final Map<String, Object> profile = createDefaultProfile();
			AuthAccount authAccount = new AuthAccount(id);
			authAccount.setProfile(profile);
			// authAccount.getAuthProfile().put("groups", authAccount);
			return authAccount;
		}
	}

	private Map<String, Object> createDefaultProfile() {
		Map<String, Object> profile = new HashMap<String, Object>();
		String groups[] = { "Guest" };
		profile.put("groups", groups);
		return profile;
	}
}
