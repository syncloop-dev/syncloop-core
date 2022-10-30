package com.eka.middleware.template;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

import javax.crypto.spec.SecretKeySpec;

import org.pac4j.jwt.config.signature.SecretSignatureConfiguration;
import org.pac4j.jwt.profile.JwtGenerator;

import com.eka.middleware.auth.Security;
import com.eka.middleware.heap.HashMap;
import com.eka.middleware.service.PropertyManager;
import com.eka.middleware.service.ServiceUtils;

public class Tenant {
	private final String name;
	public final String id;
	public final SecretSignatureConfiguration secConf;
	public final SecretKeySpec KEY;// =ServiceUtils.setKey(JWT_MASALA);
	private static Map<String, Tenant> tenantMap = new HashMap();
	public final JwtGenerator jwtGenerator;
	private static final Object syncObject = new Object();

	public static Tenant getTenant(String name) {

		synchronized (syncObject) {
			try {
				if (name == null)
					name = "default";
				if (tenantMap.get(name) == null) {
					tenantMap.put(name, new Tenant(name));
				}
			} catch (Exception e) {
				ServiceUtils.printException("Failed while loading tenant.", e);
			}
		}
		return tenantMap.get(name);
	}

	public static boolean exists(String name) {
		if (name == null || name.trim().length() == 0)
			return false;
		return tenantMap.get(name) != null;
	}

	
	public static Tenant getTempTenant(String name) {
		return new Tenant(name,null);
	}

	private Tenant(String name,String id) {
		this.name = name;
		this.id = null;
		secConf = null;
		KEY = null;
		jwtGenerator = null;
	}
	
	private Tenant(String name) throws Exception {
		this.name = name;
		String key = PropertyManager.getGlobalProperties(name).getProperty(Security.PRIVATE_PROPERTY_KEY_NAME);
		
		if (key == null)
			throw new Exception("Tenant public key not found or tenant not found. Tenant name: " + name);
		key=Base64.getEncoder().encodeToString(key.substring(0, 32).getBytes());
		this.id = key;
		secConf = new SecretSignatureConfiguration(id);
		KEY = ServiceUtils.getKey(id);
		jwtGenerator = new JwtGenerator(secConf);
		Date expiryDate=new Date();
		expiryDate=ServiceUtils.addHoursToDate(expiryDate, 24);
		jwtGenerator.setExpirationTime(expiryDate);
	}

	public String getName() {
		return name;
	}

}
