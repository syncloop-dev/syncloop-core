package com.eka.middleware.auth;

import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.Cipher;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.keycloak.adapters.AdapterDeploymentContext;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.KeycloakDeploymentBuilder;
import org.keycloak.adapters.NodesRegistrationManagement;
import org.keycloak.adapters.undertow.UndertowAuthenticationMechanism;
import org.keycloak.adapters.undertow.UndertowUserSessionManagement;
import org.keycloak.representations.adapters.config.AdapterConfig;
import org.pac4j.core.config.Config;
import org.pac4j.undertow.handler.CallbackHandler;
import org.pac4j.undertow.handler.SecurityHandler;

import com.eka.middleware.auth.pac4j.AuthConfigFactory;
import com.eka.middleware.auth.pac4j.AuthHandlers;
import com.eka.middleware.server.ThreadManager;
import com.eka.middleware.service.DataPipeline;
import com.eka.middleware.service.PropertyManager;
import com.eka.middleware.service.ServiceUtils;
import com.eka.middleware.template.Tenant;

import io.undertow.security.api.AuthenticationMode;
import io.undertow.security.handlers.AuthenticationCallHandler;
import io.undertow.security.handlers.AuthenticationConstraintHandler;
import io.undertow.security.handlers.AuthenticationMechanismsHandler;
import io.undertow.security.handlers.SecurityInitialHandler;
import io.undertow.server.handlers.PathHandler;

public class Security {
	private static final PathHandler path = new PathHandler();
	public static final String defaultTenantPage = "/tenant/default/files/gui/middleware/pub/server/ui/tenant/newTenant.html";
	public static final String defaultWelcomePage = "/tenant/default/files/gui/middleware/pub/server/ui/welcome/index.html";
	private static final Set<String> paths = new HashSet<String>();
	private static final Set<String> publicPaths = new HashSet<String>();
	private static Map<String, List<String>> publicPrefixPathsMap = new ConcurrentHashMap();
	private static Map<String, List<String>> publicExactPathsMap = new ConcurrentHashMap();
	private static Map<String, String> defaultNewUserPathMap = new ConcurrentHashMap();
	public static Logger LOGGER = LogManager.getLogger(Security.class);
	public static String PRIVATE_PROPERTY_KEY_NAME="ekamw.private.key";
	public static String PUBLIC_PROPERTY_KEY_NAME="ekamw.public.key";
	public static Set<String> getPath() {
		return paths;
	}

	public static PathHandler init() throws Exception {
		// LOGGER.info("Initializing security.......\nEnter master password controller
		// code:");
		// Scanner in = new Scanner(System.in);
		// String pass=null;

		path.addExactPath("/",
				SecurityHandler.build(AuthHandlers.defaultHandler(), AuthConfigFactory.getAnonymousClientConfig()));

		addPublicPrefixPath("/files/gui/middleware/pub/server/ui/welcome/", Tenant.getTenant("default"));
		// addPublicPrefixPath("/files/gui/middleware/pub/server/ui/welcome/",
		// Tenant.getTenant("dev"));
		path.addExactPath("/jwt",
				SecurityHandler.build(AuthHandlers.mainHandler, AuthConfigFactory.getBasicDirectAuthConfig()));
		paths.add("/jwt");
		path.addExactPath("/JWT",
				SecurityHandler.build(AuthHandlers.mainHandler, AuthConfigFactory.getBasicDirectAuthConfig()));
		paths.add("/JWT");

//	path.addPrefixPath("/public", SecurityHandler.build(AuthHandlers.defaultHandler(), AuthConfigFactory.getAnonymousClientConfig()));
//	publicPaths.add("/public");
		path.addPrefixPath("/", AuthHandlers.mainHandler);
		paths.add("/");
		path.addExactPath("/keycloak", getKeyCloakAuthenticator("keycloak.json"));
		return path;
	}

	public static void generateKeyPair(String tenantName) {
		try {
			Properties props = PropertyManager.getGlobalProperties(tenantName);
			String pubKeyString = props.getProperty(PUBLIC_PROPERTY_KEY_NAME);
			String priKeyString = props.getProperty(PRIVATE_PROPERTY_KEY_NAME);
			//if (pubKeyString == null || priKeyString == null) {
				KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA");
				keyPairGen.initialize(515, new SecureRandom(ServiceUtils.generateUUID(System.currentTimeMillis()+"").getBytes()));
				KeyPair pair = keyPairGen.generateKeyPair();
				PrivateKey privKey = pair.getPrivate();
				byte[] privateKey = privKey.getEncoded();
				PublicKey pubKey = pair.getPublic();
				byte[] publicKey = pubKey.getEncoded();
				props.put(PUBLIC_PROPERTY_KEY_NAME, new String(Base64.getEncoder().encode(publicKey),StandardCharsets.UTF_8));
				props.put(PRIVATE_PROPERTY_KEY_NAME, new String(Base64.getEncoder().encode(privateKey),StandardCharsets.UTF_8));
				PropertyManager.saveGlobalProperties(tenantName, props);
			//}
		} catch (Exception e) {
			ServiceUtils.printException("Failed while generating keypair for '"+tenantName+"'", e);
		}
	}
public static void main(String[] args) {
	generateKeyPair("default");
	
}
	private static PrivateKey getPrivateKey(byte[] key) throws Exception {
		//X509EncodedKeySpec privateKeySpec = new X509EncodedKeySpec(key, "RSA");
		PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(key);
		KeyFactory keyFactory = KeyFactory.getInstance("RSA");
		PrivateKey privateKey = keyFactory.generatePrivate(spec);
		return privateKey;
	}

	private static PublicKey getPublicKey(byte[] key) throws Exception {
		//X509EncodedKeySpec privateKeySpec = new X509EncodedKeySpec(key, "RSA");
		PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(key);
		KeyFactory keyFactory = KeyFactory.getInstance("RSA");
		PublicKey publicKey = keyFactory.generatePublic(spec);
		return publicKey;
	}

	public static String getSecureString(final String data,final String publicKey) throws Exception {
		Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding", "BC");
		//String publicKey = ServiceUtils.getServerProperty("middleware.server.public.key");
		cipher.init(Cipher.ENCRYPT_MODE, getPublicKey(Base64.getDecoder().decode(publicKey.getBytes())));
		cipher.update(data.getBytes());
		byte[] cipherText = cipher.doFinal();
		cipherText=Base64.getEncoder().encode(cipherText);
		return new String(cipherText, StandardCharsets.UTF_8);
	}

	public static String getNormalString(final String secureString, final String privateKey) throws Exception {
		Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding", "BC");
		//String privateKey = ServiceUtils.getServerProperty("middleware.server.private.key");
		cipher.init(Cipher.DECRYPT_MODE, getPrivateKey(Base64.getDecoder().decode(privateKey.getBytes(StandardCharsets.UTF_8))));
		final byte[] decipheredText = cipher.doFinal(Base64.getDecoder().decode(secureString.getBytes(StandardCharsets.UTF_8)));
		return new String(decipheredText);
	}

	public static SecurityInitialHandler getKeyCloakAuthenticator(String keycloakJsonFileName) throws Exception {

		UserProfileManager identityManager = UserProfileManager.create();
		String keycloakJson = PropertyManager.getConfigFolderPath() + keycloakJsonFileName;
		final AdapterConfig adapterConfig = KeycloakDeploymentBuilder
				.loadAdapterConfig(new FileInputStream(keycloakJson));

		final KeycloakDeployment keycloakDeployment = KeycloakDeploymentBuilder.build(adapterConfig);

		final AdapterDeploymentContext adapterDeploymentContext = new AdapterDeploymentContext(keycloakDeployment);

		final UndertowAuthenticationMechanism keycloakAuthMech = new UndertowAuthenticationMechanism(
				adapterDeploymentContext, new UndertowUserSessionManagement(), new NodesRegistrationManagement(), 443,
				"");

		/* Handlers will be invoked in reverse order of declaration. */

		/* Innermost handler; causes the AuthenticationMechanism(s) to be invoked. */
		final AuthenticationCallHandler authenticationCallHandler = new AuthenticationCallHandler(
				ThreadManager::processRequest);

		/*
		 * Handler to answer question “is authentication required”. Always requires
		 * authentication.
		 */
		final AuthenticationConstraintHandler authenticationConstraintHandler = new AuthenticationConstraintHandler(
				authenticationCallHandler);

		/* Handler to install AuthenticationMechanism(s). */
		final AuthenticationMechanismsHandler authenticationMechanismsHandler = new AuthenticationMechanismsHandler(
				authenticationConstraintHandler, Arrays.asList(keycloakAuthMech));

		/* Outermost handler: installs SecurityContext. */
		final SecurityInitialHandler securityInitialHandler = new SecurityInitialHandler(AuthenticationMode.PRO_ACTIVE,
				identityManager, authenticationMechanismsHandler);
		return securityInitialHandler;
	}

	public static void setupTenantSecurity(final Tenant tenant) {
		Security.generateKeyPair(tenant.getName());
		addPublicPrefixPath("/tenant/" + tenant.getName() + "/files/gui/middleware/pub/server/ui/welcome/",
				Tenant.getTenant("default"));
		List<String> tennatPublicExactPaths = publicExactPathsMap.get(tenant.getName());
		if (tennatPublicExactPaths != null && tennatPublicExactPaths.size() > 0)
			tennatPublicExactPaths.forEach(path -> Security.addPublicExactPath(path, tenant));

		List<String> tennatPublicPrefixPaths = publicPrefixPathsMap.get(tenant.getName());
		if (tennatPublicPrefixPaths != null && tennatPublicPrefixPaths.size() > 0)
			tennatPublicPrefixPaths.forEach(path -> Security.addPublicExactPath(path, tenant));
	}

	public static void addPublicPrefixPath(String resourcePrefixPath, Tenant tenant) {
		List<String> tennatPublicPaths = publicPrefixPathsMap.get(tenant.getName());
		if (tennatPublicPaths == null || tennatPublicPaths.size() == 0) {
			tennatPublicPaths = new ArrayList();
			publicPrefixPathsMap.put(tenant.getName(), tennatPublicPaths);
		}
		if (!tennatPublicPaths.contains(resourcePrefixPath)) {
			tennatPublicPaths.add(resourcePrefixPath);
			path.addPrefixPath(resourcePrefixPath,
					SecurityHandler.build(AuthHandlers.indexHandler(), AuthConfigFactory.getAnonymousClientConfig()));
			path.addPrefixPath("/tenant/" + tenant.getName() + resourcePrefixPath,
					SecurityHandler.build(AuthHandlers.indexHandler(), AuthConfigFactory.getAnonymousClientConfig()));
		}
	}

	public static void addPublicExactPath(String resourceExactPath, Tenant tenant) {
		List<String> tennatPublicPaths = publicExactPathsMap.get(tenant.getName());
		if (tennatPublicPaths == null || tennatPublicPaths.size() == 0) {
			tennatPublicPaths = new ArrayList();
			publicExactPathsMap.put(tenant.getName(), tennatPublicPaths);
		}
		if (!tennatPublicPaths.contains(resourceExactPath)) {
			tennatPublicPaths.add(resourceExactPath);
			path.addExactPath(resourceExactPath,
					SecurityHandler.build(AuthHandlers.indexHandler(), AuthConfigFactory.getAnonymousClientConfig()));
			path.addExactPath("/tenant/" + tenant.getName() + "/" + resourceExactPath,
					SecurityHandler.build(AuthHandlers.indexHandler(), AuthConfigFactory.getAnonymousClientConfig()));
		}
	}

	public static boolean isPublic(String path, String tenantName) {
		List<String> tennatPublicPaths = publicPrefixPathsMap.get(tenantName);
		if (tennatPublicPaths != null) {
			for (String resourcePath : tennatPublicPaths) {
				if (path.startsWith(resourcePath))
					return true;
			}
		}
		if (publicExactPathsMap.get(tenantName) != null && publicExactPathsMap.get(tenantName).contains(path))
			return true;
		return false;
	}

	public static void addDefaultNewUserPath1(String path, String tenantName) {
		defaultNewUserPathMap.put(tenantName, path);
	}

	public static void removeDefaultNewUserPath(String path, String tenantName) {
		defaultNewUserPathMap.remove(tenantName);
	}

	public static String gerDefaultNewUserPath(String tenantName) {
		return defaultNewUserPathMap.get(tenantName);
	}

	public static String addExternalOIDCAuthorizationServer(Map<String, Object> props, String resourcePath,
			DataPipeline dp) throws Exception {
		// props.put("tenant", dp.rp.getTenant().getName());
		String loginPath = resourcePath;
		Config conf = AuthConfigFactory.getOIDCAuthClientConfig(props, dp.rp.getTenant().getName());
		resourcePath = "/tenant/" + dp.rp.getTenant().getName() + resourcePath;
		String basePath = (String) props.get("basePath");
		String reDirectURI = basePath + "/callback" + resourcePath + "?client_name=OidcClient";
		paths.add(resourcePath);
		path.addExactPath(resourcePath, SecurityHandler.build(AuthHandlers.mainHandler, conf));
		paths.add("/callback" + resourcePath);
		path.addExactPath("/callback" + resourcePath, CallbackHandler.build(conf, null));
		return "Login url=" + basePath + loginPath + ", Redirect_uri=" + reDirectURI;
	}

	public static List<String> getPublicPrefixPaths(String tenantName) {
		return publicPrefixPathsMap.get(tenantName);
	}

	public static List<String> getPublicExactPaths(String tenantName) {
		return publicExactPathsMap.get(tenantName);
	}

	public static void removePath(String resourcePath) throws Exception {
		path.removeExactPath(resourcePath);
		path.removePrefixPath(resourcePath);
	}

}
