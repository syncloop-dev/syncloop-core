package com.eka.middleware.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.InetAddress;
import java.net.URL;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.eka.middleware.auth.Security;
import com.eka.middleware.service.PropertyManager;
import com.eka.middleware.service.RuntimePipeline;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.session.InMemorySessionManager;
import io.undertow.server.session.SessionAttachmentHandler;
import io.undertow.server.session.SessionCookieConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;

import com.eka.middleware.auth.ResourceAuthenticator;
import com.eka.middleware.auth.UserProfileManager;
import com.eka.middleware.service.ServiceUtils;
import com.eka.middleware.template.SystemException;

import io.undertow.Undertow;
import io.undertow.Undertow.Builder;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.AuthenticationMode;
import io.undertow.security.handlers.AuthenticationCallHandler;
import io.undertow.security.handlers.AuthenticationConstraintHandler;
import io.undertow.security.handlers.AuthenticationMechanismsHandler;
import io.undertow.security.handlers.SecurityInitialHandler;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.impl.BasicAuthenticationMechanism;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.BlockingHandler;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

public class MiddlewareServer {
	public static Logger LOGGER = LogManager.getLogger(MiddlewareServer.class);
	private static String local_IP="localhost";
	public static String JWT_MASALA="17a0dab4-991c-47b6-91c6-109e640a817f";
	private static Map<String, Long> lastModifiedMap = new ConcurrentHashMap<String, Long>();

	public static void main(final String[] args) throws SystemException {
		try {
			PropertyManager.initConfig(args);
			local_IP=PropertyManager.getLocal_IP();
			String ports[] = ServiceUtils.getServerProperty("middleware.server.http.ports").split(",");
			String https=ServiceUtils.getServerProperty("middleware.server.https.ports");
			String keyStoreFilePath=ServiceUtils.getServerProperty("middleware.server.keyStore.jks");
			String keyStorePassword=ServiceUtils.getServerProperty("middleware.server.keyStore.jks.password");

			PathHandler pathHandler = Security.init();

			//https="8443";
			String securePorts[] = null;
			if(https!=null)
				securePorts=https.split(",");
			final UserProfileManager identityManager = UserProfileManager.create();
			try {
				String uuid = UUID.randomUUID().toString();
				RuntimePipeline rp = RuntimePipeline.create(uuid, uuid, null, "GET/execute/packages.middleware.pub.server.core.service.main",
						"/execute/packages.middleware.pub.server.core.service.main");
				rp.dataPipeLine.applyAsync("packages.middleware.pub.server.core.service");
			} catch (Exception e) {
				throw new SystemException("EKA_MWS_1008", e);
			}

			try {
				String uuid = UUID.randomUUID().toString();
				RuntimePipeline rp = RuntimePipeline.create(uuid, uuid, null, "GET/execute/packages.middleware.pub.server.core.service.main",
						"/execute/packages.middleware.pub.server.core.service.main");
				rp.dataPipeLine.applyAsync("packages.middleware.pub.server.core.service");
			} catch (Exception e) {
				throw new SystemException("EKA_MWS_1008", e);
			}

			Builder builder = Undertow.builder();
			for (String port : ports) {
				builder.addHttpListener(Integer.parseInt(port), local_IP).setHandler(
						new SessionAttachmentHandler(pathHandler, new InMemorySessionManager("SessionManager"), new SessionCookieConfig()));
			}
			SSLContext context = null;
			if(keyStoreFilePath!=null && new File(keyStoreFilePath).exists())
				context=createSSLContext(keyStoreFilePath,keyStorePassword);

			if(context!=null)
				for (String port : securePorts) {
					builder.addHttpsListener(Integer.parseInt(port), local_IP,context).setHandler(
							new SessionAttachmentHandler(pathHandler, new InMemorySessionManager("SessionManager"), new SessionCookieConfig()));
				}

			configureUndertow(builder);
			Undertow server = builder.build();
			server.start();
		}catch(Exception e) {
			e.printStackTrace();
			throw new SystemException("EKA_MWS_1004", e);
		}
	}

	private static void configureUndertow(Builder builder) {
		builder.setServerOption(io.undertow.UndertowOptions.MAX_PARAMETERS, 10000);
	}

	public static SSLContext createSSLContext(String keyStoreFilePath, String password) throws Exception {
		char[] passphrase=password.toCharArray();
		KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
		FileInputStream fis=new FileInputStream(new File(keyStoreFilePath));
		ks.load(fis, passphrase);
		fis.close();

		TrustManager[] tms = new TrustManager[] { };
		KeyManager[] kms = null;
		KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		kmf.init(ks, passphrase);
		kms = kmf.getKeyManagers();
		SSLContext context = SSLContext.getInstance("TLS");
		context.init(kms, tms, new SecureRandom());
		return context;
	}

	public static String getConfigFolderPath() {
		return PropertyManager.getConfigFolderPath();
	}
}
