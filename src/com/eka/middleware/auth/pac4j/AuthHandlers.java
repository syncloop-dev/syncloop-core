package com.eka.middleware.auth.pac4j;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.pac4j.core.client.Client;
import org.pac4j.core.config.Config;
import org.pac4j.core.exception.http.HttpAction;
import org.pac4j.core.profile.UserProfile;
import org.pac4j.core.util.Pac4jConstants;
import org.pac4j.http.client.indirect.FormClient;
import org.pac4j.jwt.config.signature.SecretSignatureConfiguration;
import org.pac4j.jwt.profile.JwtGenerator;
import org.pac4j.undertow.account.Pac4jAccount;
import org.pac4j.undertow.context.UndertowSessionStore;
import org.pac4j.undertow.context.UndertowWebContext;
import org.pac4j.undertow.handler.SecurityHandler;
import org.pac4j.undertow.http.UndertowHttpActionAdapter;

import com.eka.middleware.auth.AuthAccount;
import com.eka.middleware.auth.ResourceAuthenticator;
import com.eka.middleware.auth.Security;
import com.eka.middleware.auth.manager.JWT;
import com.eka.middleware.server.MiddlewareServer;
import com.eka.middleware.server.ThreadManager;
import com.eka.middleware.service.ServiceUtils;

import io.undertow.security.api.SecurityContext;
import io.undertow.security.idm.Account;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.server.handlers.CookieImpl;
import io.undertow.util.HeaderMap;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;

/**
 * A collection of basic handlers printing dynamic html for the demo
 * application.
 * 
 * @author Michael Remond
 * @since 1.0.0
 */
public class AuthHandlers {

	private static final Logger LOGGER = LogManager.getLogger(AuthHandlers.class);

	public static HttpHandler indexHandler() {
		return exchange -> {
			ThreadManager.processRequest(exchange);
		};
	}

	public static HttpHandler defaultHandler() {
		return exchange -> {
			exchange.getResponseHeaders().clear();
			exchange.setStatusCode(StatusCodes.FOUND);
			exchange.getResponseHeaders().put(Headers.LOCATION, Security.defaultWelcomePage);
			Cookie cookie = new CookieImpl("tenant", "default");
			exchange.setResponseCookie(cookie);
			exchange.endExchange();
		};
	}

	private static void sendEnd(final HttpServerExchange exchange, final StringBuilder sb) {
		exchange.getResponseHeaders().add(Headers.CONTENT_TYPE, "text/html; charset=utf-8");
		exchange.getResponseSender().send(sb.toString());
		exchange.endExchange();
	}

	public static HttpHandler mainHandler = new HttpHandler() {
		public void handleRequest(final HttpServerExchange exchange) throws Exception {
			final SecurityContext context = exchange.getSecurityContext();
			List<UserProfile> profiles = getProfiles(exchange);
			String tenantName=ServiceUtils.setupRequestPath(exchange);
			Cookie cookie=ServiceUtils.setupCookie(exchange, tenantName, null);
			
			if (profiles != null) {
				AuthAccount acc = ServiceUtils.getCurrentLoggedInAuthAccount(exchange);
				String token=ServiceUtils.getToken(cookie);
				if(token==null) {
					token=JWT.generate(exchange);
					cookie=ServiceUtils.setupCookie(exchange, (String)acc.getAuthProfile().get("tenant"), token);
				}
				ThreadManager.processRequest(exchange,cookie);
			} else {
				HttpHandler hh = this;
				HeaderMap headers = exchange.getRequestHeaders();
				HeaderValues hv = headers.get(HttpString.tryFromString("Authorization"));
				String authorization = null;
				boolean isBearer = false;
				boolean isBasic = false;

				if (hv != null) {
					authorization = exchange.getRequestHeaders().get(HttpString.tryFromString("Authorization"))
							.getFirst();
					isBearer = authorization.startsWith("Bearer ");
					isBasic = authorization.startsWith("Basic ");
				}
				String token = ServiceUtils.getToken(cookie);
				if (isBearer)
					token = authorization.replace("Bearer ", "");

				if (token == null) {
					Config cfg = Security.loginExactPathsMap.get(tenantName);
					if(cfg==null) {
						exchange.getResponseSender().send("Security has not been initialized for tenant '"+tenantName+"'.");
						exchange.endExchange();
						return;
					}
					hh = SecurityHandler.build(hh, cfg);

				} else {
					try {
						token = ServiceUtils.decrypt(token, AuthConfigFactory.KEY);
					} catch (Exception e) {
						exchange.getResponseSender().send("Invalid Token");
						exchange.endExchange();
						return;
					}

					exchange.getRequestHeaders().put(HttpString.tryFromString("Authorization"), token);
					Config cfg = AuthConfigFactory.getJWTAuthClientConfig();
					hh = SecurityHandler.build(hh, cfg);
				}
				hh.handleRequest(exchange);
			}

		}
	};
	/*
	 * public static HttpHandler oidcIndex = exchange -> { StringBuilder sb = new
	 * StringBuilder(); sb.append("<h1>protected area</h1>");
	 * sb.append("<a href=\"..\">Back</a><br />"); sb.append("<br /><br />");
	 * sb.append("profiles: "); sb.append(getProfiles(exchange));
	 * sb.append("<br />");
	 * 
	 * sendEnd(exchange, sb); }; /*
	 * 
	 * 
	 * exchange -> { final SecurityContext context = exchange.getSecurityContext();
	 * Account authAccount= null; if(context!=null)
	 * authAccount=context.getAuthenticatedAccount(); if(authAccount!=null)
	 * ThreadManager.processRequest(exchange); else { HttpHandler hh=protectedIndex;
	 * } };
	 * 
	 * String authType=null; if(exchange.getQueryParameters()!=null &&
	 * exchange.getQueryParameters().get("AuthType")!=null &&
	 * exchange.getQueryParameters().get("AuthType").size()>0)
	 * authType=exchange.getQueryParameters().get("AuthType").getFirst();
	 * if(ResourceAuthenticator.isPublic(exchange))
	 * ThreadManager.processRequest(exchange); else { HttpHandler hh=this; final
	 * SecurityContext context = exchange.getSecurityContext(); Account authAccount=
	 * null; if(context!=null) authAccount=context.getAuthenticatedAccount();
	 * if(authAccount==null) { Config
	 * cfg=AuthConfigFactory.getBasicDirectAuthConfig(); hh =
	 * SecurityHandler.build(hh, cfg); } hh.handleRequest(exchange); }
	 */
	public static HttpHandler notProtectedIndex = exchange -> {
		String tenantName=ServiceUtils.setupRequestPath(exchange);
		Cookie cookie=ServiceUtils.setupCookie(exchange, tenantName, null);
		ThreadManager.processRequest(exchange,cookie);
	};

	public static HttpHandler authenticatedJsonHandler = exchange -> {
		StringBuilder sb = new StringBuilder();
		sb.append("{\"username\":\"");
		sb.append(getProfile(exchange).getId());
		sb.append("\"}");

		exchange.getResponseHeaders().add(HttpString.tryFromString("Content-Type"), "application/json");
		sendEnd(exchange, sb);
	};

	private static Pac4jAccount getAccount(final HttpServerExchange exchange) {
		final SecurityContext securityContext = exchange.getSecurityContext();
		if (securityContext != null) {
			final Account account = securityContext.getAuthenticatedAccount();
			if (account instanceof Pac4jAccount) {
				return (Pac4jAccount) account;
			}
		}
		return null;
	}

	public static Pac4jAccount getAuthAccount(final HttpServerExchange exchange) {
		final SecurityContext securityContext = exchange.getSecurityContext();
		if (securityContext != null) {
			final Pac4jAccount account = (Pac4jAccount) securityContext.getAuthenticatedAccount();
			return account;
		}
		return null;
	}

	private static List<UserProfile> getProfiles(final HttpServerExchange exchange) {
		final Pac4jAccount account = getAccount(exchange);
		if (account != null) {
			return account.getProfiles();
		}
		return null;
	}

	public static UserProfile getProfile(final HttpServerExchange exchange) {
		final Pac4jAccount account = getAccount(exchange);
		if (account != null) {
			return account.getProfile();
		}
		return null;
	}

	public static HttpHandler loginFormHandler(final Config config) {
		return exchange -> {
			FormClient formClient = (FormClient) config.getClients().findClient("FormClient").get();
			StringBuilder sb = new StringBuilder();

			LOGGER.info("Callback Url ::: {}", formClient.getCallbackUrl());

			sb.append("<html><body>");
			sb.append("<form action=\"").append(formClient.getCallbackUrl())
					.append("?client_name=FormClient\" method=\"GET\">");
			sb.append("<input type=\"text\" name=\"username\" value=\"\" />");
			sb.append("<p />");
			sb.append("<input type=\"password\" name=\"password\" value=\"\" />");
			sb.append("<p />");
			sb.append("<input type=\"submit\" name=\"submit\" value=\"Submit\" />");
			sb.append("</form>");
			sb.append("</body></html>");

			sendEnd(exchange, sb);
		};
	}

	public static HttpHandler forceLoginHandler(final Config config) {
		return exchange -> {
			final UndertowWebContext context = new UndertowWebContext(exchange);
			final UndertowSessionStore sessionStore = new UndertowSessionStore(exchange);
			final String clientName = context.getRequestParameter(Pac4jConstants.DEFAULT_CLIENT_NAME_PARAMETER).get();
			final Client client = config.getClients().findClient(clientName).get();
			HttpAction action;
			try {
				action = client.getRedirectionAction(context, sessionStore).get();
			} catch (final HttpAction e) {
				action = e;
			}
			UndertowHttpActionAdapter.INSTANCE.adapt(action, context);
		};
	}
}
