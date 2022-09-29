package com.eka.middleware.auth.pac4j;

import java.util.Optional;
import java.util.Properties;
import java.util.Set;

import org.pac4j.core.authorization.authorizer.RequireAnyRoleAuthorizer;
import org.pac4j.core.client.Client;
import org.pac4j.core.client.Clients;
import org.pac4j.core.client.direct.AnonymousClient;
import org.pac4j.core.config.Config;
import org.pac4j.core.config.ConfigFactory;
import org.pac4j.http.client.direct.DirectBasicAuthClient;
import org.pac4j.http.client.direct.ParameterClient;
import org.pac4j.http.client.indirect.IndirectBasicAuthClient;
import org.pac4j.http.credentials.authenticator.test.SimpleTestUsernamePasswordAuthenticator;
import org.pac4j.jwt.config.signature.SecretSignatureConfiguration;
import org.pac4j.jwt.credentials.authenticator.JwtAuthenticator;
import org.pac4j.oidc.client.OidcClient;
import org.pac4j.oidc.config.OidcConfiguration;

import com.eka.middleware.auth.manager.BasicAuthenticator;
import com.eka.middleware.server.MiddlewareServer;
import com.eka.middleware.service.PropertyManager;
import com.eka.middleware.service.ServiceUtils;
import com.eka.middleware.template.SystemException;
import com.nimbusds.jose.JWSAlgorithm;

public class AuthConfigFactory implements ConfigFactory {

	private static Config newConfig(Client clnt) {
		final Config config = new Config(clnt);
		config.addAuthorizer("admin", new RequireAnyRoleAuthorizer("ROLE_ADMIN"));
		config.addAuthorizer("custom", new CustomAuthorizer());
		return config;
	}

	private static Config newConfig(Clients clnts) {
		final Config config = new Config(clnts);
		config.addAuthorizer("admin", new RequireAnyRoleAuthorizer("ROLE_ADMIN"));
		config.addAuthorizer("custom", new CustomAuthorizer());
		return config;
	}

	public Config build(Object... parameters) {
		final AnonymousClient anonymousClient = new AnonymousClient();
		final Clients clients = new Clients("http://localhost:8080/callback", anonymousClient);
		return newConfig(clients);
	}

	private static Config DirectBasicAuthClientConfig;

	public static Config getBasicDirectAuthConfig() {
		if (DirectBasicAuthClientConfig == null) {
			final DirectBasicAuthClient client = new DirectBasicAuthClient(new BasicAuthenticator());
			DirectBasicAuthClientConfig = newConfig(client);
		}
		return DirectBasicAuthClientConfig;
	}

	private static Config JWTAuthClientConfig;

	public static Config getJWTAuthClientConfig() {
		if (JWTAuthClientConfig == null) {
			final ParameterClient client = new ParameterClient("Authorization_BearerToken",
					new JwtAuthenticator(new SecretSignatureConfiguration(MiddlewareServer.JWT_MASALA)));
			client.setSupportGetRequest(true);
			client.setSupportPostRequest(false);
			JWTAuthClientConfig = newConfig(client);
		}
		return JWTAuthClientConfig;
	}

	private static Config OIDCAuthClientConfig;

	public static Config getOIDCAuthClientConfig() throws SystemException {
		boolean reload = PropertyManager.hasfileChanged("auth/oidc.properties");
		OidcConfiguration oidcConfiguration = null;
		if (OIDCAuthClientConfig == null || reload)
			oidcConfiguration = new OidcConfiguration();
		if (oidcConfiguration != null)
			synchronized (oidcConfiguration) {
				if (reload) {
					reload = false;
					Properties props = PropertyManager.getServerProperties("auth/oidc.properties");
					oidcConfiguration.setClientId(props.getProperty("clientId"));
					oidcConfiguration.setSecret(props.getProperty("secret"));
					oidcConfiguration.setDiscoveryURI(props.getProperty("discoveryURI"));
					oidcConfiguration.setUseNonce(true);
					String preferedJwsAlgo = props.getProperty("preferredJwsAlgorithm");
					if (preferedJwsAlgo != null)
						try {
							JWSAlgorithm jwsAlgo = new JWSAlgorithm(preferedJwsAlgo);
							oidcConfiguration.setPreferredJwsAlgorithm(jwsAlgo);
							oidcConfiguration.setProviderMetadata(
									ServiceUtils.fetchMetadata(props.getProperty("discoveryURI"), jwsAlgo));
						} catch (Exception e) {
							System.err.println("Failed to validate Bearer token: " + e.getMessage());
							e.printStackTrace();
						}

					Set<Object> keys = props.keySet();
					for (Object keyStr : keys) {
						String key = keyStr.toString();
						if (key.startsWith("customParam")) {
							String customKey = key.replace("customParam.", "");
							String val = props.getProperty(key);
							oidcConfiguration.addCustomParam(customKey, val);
						}
					}
					final OidcClient oidcClient = new OidcClient(oidcConfiguration);
					oidcClient.setAuthorizationGenerator((ctx, session, profile) -> {
						profile.addRole("ROLE_ADMIN");
						return Optional.of(profile);

					});
					final Clients clients = new Clients("http://localhost:8080/callback", oidcClient);// ,headerClient);
					OIDCAuthClientConfig = newConfig(clients);
				}
			}
		return OIDCAuthClientConfig;
	}

	public static Config getOIDCAuthClientConfig(Properties props) throws SystemException {
		OidcConfiguration oidcConfiguration = null;
		oidcConfiguration = new OidcConfiguration();
		oidcConfiguration.setClientId(props.getProperty("clientId"));
		oidcConfiguration.setSecret(props.getProperty("secret"));
		oidcConfiguration.setDiscoveryURI(props.getProperty("discoveryURI"));
		oidcConfiguration.setUseNonce(true);
		String preferedJwsAlgo = props.getProperty("preferredJwsAlgorithm");
		if (preferedJwsAlgo != null)
			try {
				JWSAlgorithm jwsAlgo = new JWSAlgorithm(preferedJwsAlgo);
				oidcConfiguration.setPreferredJwsAlgorithm(jwsAlgo);
				oidcConfiguration
						.setProviderMetadata(ServiceUtils.fetchMetadata(props.getProperty("discoveryURI"), jwsAlgo));
			} catch (Exception e) {
				System.err.println("Failed to validate Bearer token: " + e.getMessage());
				e.printStackTrace();
			}

		Set<Object> keys = props.keySet();
		for (Object keyStr : keys) {
			String key = keyStr.toString();
			if (key.startsWith("customParam")) {
				String customKey = key.replace("customParam.", "");
				String val = props.getProperty(key);
				oidcConfiguration.addCustomParam(customKey, val);
			}
		}
		final OidcClient oidcClient = new OidcClient(oidcConfiguration);
		oidcClient.setAuthorizationGenerator((ctx, session, profile) -> {
			profile.addRole("ROLE_ADMIN");
			return Optional.of(profile);

		});
		
		final Clients clients = new Clients("http://localhost:8080/callback", oidcClient);// ,headerClient);
		Config OIDCAuthClientConfig = newConfig(clients);
		return OIDCAuthClientConfig;
	}

	private static Config AnonymousConfig;

	public static Config getAnonymousClientConfig() {
		if (AnonymousConfig == null) {
			final AnonymousClient anonymousClient = new AnonymousClient();
			AnonymousConfig = newConfig(anonymousClient);
		}
		return AnonymousConfig;
	}

	private static Config IndirectBasicAuthClientConfig;

	public static Config getIndirectBasicAuthClientConfig() {
		if (IndirectBasicAuthClientConfig == null) {
			final IndirectBasicAuthClient indirectBasicAuthClient = new IndirectBasicAuthClient(
					new SimpleTestUsernamePasswordAuthenticator());
			IndirectBasicAuthClientConfig = newConfig(indirectBasicAuthClient);
		}
		return IndirectBasicAuthClientConfig;
	}

	// final IndirectBasicAuthClient indirectBasicAuthClient = new
	// IndirectBasicAuthClient(new SimpleTestUsernamePasswordAuthenticator());

	/*
	 * private static Config OIDCAuthClientConfig;
	 * 
	 * public static Config getOIDCAuthClientConfig() {
	 * if(OIDCAuthClientConfig==null) { final OIDCAuthClientConfig =
	 * newConfig(client); } return OIDCAuthClientConfig; }
	 */

}
