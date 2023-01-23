package com.eka.middleware.server;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.eka.middleware.auth.AuthAccount;
import com.eka.middleware.auth.Security;
import com.eka.middleware.auth.UserProfileManager;
import com.eka.middleware.service.PropertyManager;
import com.eka.middleware.service.ServiceUtils;
import com.eka.middleware.template.SystemException;
import com.eka.middleware.template.Tenant;

import io.undertow.Undertow;
import io.undertow.Undertow.Builder;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.session.InMemorySessionManager;
import io.undertow.server.session.SessionAttachmentHandler;
import io.undertow.server.session.SessionCookieConfig;

public class MiddlewareServer {
	public static Logger LOGGER = LogManager.getLogger(MiddlewareServer.class);
	private static String local_IP="localhost";
	//public static String JWT_MASALA="17a0dab4-991c-47b6-91c6-109e640a817f";
	private static Map<String, Long> lastModifiedMap = new ConcurrentHashMap<String, Long>();

	public static void main(final String[] args) throws SystemException {

		if (Boolean.parseBoolean(System.getProperty("CONTAINER_DEPLOYMENT"))) {
			try {
				BootBuild.bootBuild();
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else {
			LOGGER.info("No Container Deployment");
		}

		try {
			PropertyManager.initConfig(args);
			local_IP=PropertyManager.getLocal_IP();
			String ports[] = ServiceUtils.getServerProperty("middleware.server.http.ports").split(",");
			String https=ServiceUtils.getServerProperty("middleware.server.https.ports");
			String keyStoreFilePath=ServiceUtils.getServerProperty("middleware.server.keyStore.jks");
			String keyStorePassword=ServiceUtils.getServerProperty("middleware.server.keyStore.jks.password");

			//https="8443";
			String securePorts[] = null;
			if(https!=null)
				securePorts=https.split(",");
			final UserProfileManager identityManager = UserProfileManager.create();
			LOGGER.trace("Users loaded..................");
			List<String> tenants = UserProfileManager.getTenants();
			LOGGER.trace("Tenants loaded: "+tenants);
			try {
				Tenant defaultTenant=Tenant.getTempTenant("default");
				String dirPath=PropertyManager.getPackagePath(defaultTenant)+"packages";
				File dir=new File(dirPath);
				
				if(!dir.exists()) {
					AuthAccount authAcc=new AuthAccount("admin");
					Map<String, Object> profile=UserProfileManager.createDefaultProfile(null,"default");
					List<String> groups=new ArrayList<String>();
					groups.add(AuthAccount.STATIC_ADMIN_GROUP);
					groups.add(AuthAccount.STATIC_DEVELOPER_GROUP);
					profile.put("groups",groups);
					profile.put("forceCreateUser",true);
					authAcc.setProfile(profile);
					//UserProfileManager.addUser(authAcc);
					ServiceUtils.initNewTenant("default", authAcc);
				}else {
					LOGGER.info("Starting default tenant......................");
					defaultTenant.logDebug(null, "Starting default tenant......................");
					ServiceUtils.startTenantServices("default");
				}
				
				Thread.sleep(500);
				for (String tenant: tenants) {
					Tenant tent=Tenant.getTenant(tenant);
					dirPath=PropertyManager.getPackagePath(tent)+"packages";
					dir=new File(dirPath);
					LOGGER.trace("Tenant directory: "+dirPath);
					//tent.logInfo("", dirPath);
					if(!dir.exists()) {
						LOGGER.trace("Tenant directory not found: "+dirPath);
						continue;
					}
					if(!"default".equalsIgnoreCase(tenant)) {
						LOGGER.info("Starting "+tenant+" tenant......................");
						tent.logDebug(null, "Starting "+tenant+" tenant......................");
						ServiceUtils.startTenantServices(tenant);
						Thread.sleep(500);
					}
				}
			} catch (Exception e) {
				throw new SystemException("EKA_MWS_1008", e);
			}
			LOGGER.trace("Creating server builder ........");
			PathHandler pathHandler = Security.init();
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

	private static class BootBuild {
		/**
		 *
		 */
		private static void bootBuild() throws Exception {

			String distributionName = "eka-distribution-v1.4.zip";
			if (Boolean.parseBoolean(System.getProperty("COMMUNITY_DEPLOYMENT"))) {
				distributionName = "eka-distribution-community-v1.4.zip";
			}

			File eka = new File("./eka/version");
			if (eka.exists()) {
				LOGGER.info("Build already existed.");
			} else {
				if (new File("./" + distributionName).exists()) {
					LOGGER.info("Found Build.");
				} else {
					LOGGER.info("Build Downloading...");
					InputStream in = new URL("https://eka-distribution.s3.us-west-1.amazonaws.com/" + distributionName).openStream();
					Files.copy(in, Paths.get(distributionName), StandardCopyOption.REPLACE_EXISTING);
					LOGGER.info("Build Download Completed.");
				}

				LOGGER.info("Unzipping build...");
				byte[] buffer = new byte[1024];
				ZipInputStream zis = new ZipInputStream(new FileInputStream("./" + distributionName));
				ZipEntry zipEntry = zis.getNextEntry();
				while (zipEntry != null) {
					File newFile = newFile(new File(""), zipEntry);
					if (zipEntry.isDirectory()) {
						if (!newFile.isDirectory() && !newFile.mkdirs()) {
							throw new IOException("Failed to create directory " + newFile);
						}
					} else {
						// fix for Windows-created archives
						File parent = newFile.getParentFile();
						if (!parent.isDirectory() && !parent.mkdirs()) {
							throw new IOException("Failed to create directory " + parent);
						}

						// write file content
						FileOutputStream fos = new FileOutputStream(newFile);
						int len;
						while ((len = zis.read(buffer)) > 0) {
							fos.write(buffer, 0, len);
						}
						fos.close();
					}
					zipEntry = zis.getNextEntry();
				}
				zis.closeEntry();
				zis.close();
			}
		}

		/**
		 * @param destinationDir
		 * @param zipEntry
		 * @return
		 * @throws IOException
		 */
		private static File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
			File destFile = new File(destinationDir, zipEntry.getName());

			String destDirPath = destinationDir.getCanonicalPath();
			String destFilePath = destFile.getCanonicalPath();

			//if (!destFilePath.startsWith("/" + destDirPath + File.separator)) {
				//System.out.println(destFilePath);
				//throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
			//}

			return destFile;
		}
	}
}
