package com.eka.middleware.service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;

import com.eka.middleware.server.MiddlewareServer;
import com.eka.middleware.server.ServiceManager;
import com.eka.middleware.template.SnippetException;
import com.eka.middleware.template.SystemException;
import com.eka.middleware.template.Tenant;

public class PropertyManager {
    private static final Map<String, Properties> propertiesMap = new ConcurrentHashMap<String, Properties>();
    public static Logger LOGGER = LogManager.getLogger(PropertyManager.class);
    private static Map<String, Long> lastModifiedMap = new ConcurrentHashMap<String, Long>();
    private static String configFolderPath;
    private static String local_IP="localhost";
    
    public static Properties getProperties(String key) {
    	return propertiesMap.get(key);
    }
    
    public static String getPackagePath(Tenant tenant) {
    	String tenantName=tenant.getName();
    	if(tenantName==null || tenantName.trim().length()==0)
    		return ServiceUtils.getServerProperty("middleware.server.home.dir");
    	String tenantDir=tenantName;
    	//if(tenantName.equals("dev"))
    		//tenantDir="";
    	return ServiceUtils.getServerProperty("middleware.server.home.dir")+"tenants/"+tenantDir+"/";
    }
    
     public static final Properties getProperties(DataPipeline dataPipeLine, String fileName) throws SnippetException {
        String packageName = dataPipeLine.getCurrentResource();
        String packagePath=PropertyManager.getPackagePath(dataPipeLine.rp.getTenant());
        if (fileName.startsWith("global"))
            packageName = "global";
        else {
            fileName = fileName.replace(".main", "");
        }
        //String fileName=thisPackageName+".properties";
        String key = fileName;
        String pkg[] = packageName.split(Pattern.quote("."));
        if (pkg.length == 1)
            packageName = pkg[0];
        else
            packageName = pkg[1];
        String path = packageName + "/dependency/config/" + fileName;
        Properties props = propertiesMap.get(key);
        try {
            File uriFile = new File(packagePath + "/" + path);
            if (!uriFile.exists()) {
                return new Properties();
            }
            byte[] bytes = readConfigurationAbsFile(uriFile);
            if (bytes != null) {
                ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
                if (props == null) {
                    props = new Properties();
                    propertiesMap.put(path, props);
                }
                loadServerProperties(bais, props);
                bais.close();
            } else if (props == null) {
                bytes = ServiceUtils.readAllBytes(uriFile);
                ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
                props = new Properties();
                loadServerProperties(bais, props);
                propertiesMap.put(path, props);
            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            throw new SnippetException(dataPipeLine, "Error while getting property file '" + fileName + "'", e);
        }
        return props;
    }
    
    public static final Properties getServerProperties(String filePath) throws SystemException {
        String absoluteFilePath = getConfigFolderPath() + filePath;
        File file=new File(absoluteFilePath);
        absoluteFilePath=file.getAbsolutePath();
        Properties props = propertiesMap.get(absoluteFilePath);
        try {
            byte[] bytes = readConfigurationAbsFile(file);
            if (bytes != null) {
                ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
                if (props == null) {
                    props = new Properties();
                    propertiesMap.put(absoluteFilePath, props);
                }
                loadServerProperties(bais, props);
                bais.close();
            } else if (props == null) {
                bytes = ServiceUtils.readAllBytes(new File(absoluteFilePath));
                ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
                props = new Properties();
                loadServerProperties(bais, props);
                propertiesMap.put(absoluteFilePath, props);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new SystemException("EKA_MWS_1006", e);
        }
        return props;
    }
    
    private static final boolean loadServerProperties(ByteArrayInputStream bais, Properties properties)
			throws SystemException {
		try {
			properties.clear();
			properties.load(bais);
		} catch (Exception e) {
			throw new SystemException("EKA_MWS_1005", e);
		}
		return true;
	}
    
    public static boolean hasfileChanged(String filePath) {
    	String absoluteFilePath = getConfigFolderPath() + filePath;
    	File file = new File(absoluteFilePath);
    	Long lastModified = lastModifiedMap.get(file.getAbsoluteFile());
    	if (lastModified == null || lastModified < file.lastModified())
    		return true;
    	return false;
    }
    
    public static boolean hasTenantFileChanged(String filePath) {
    	//String absoluteFilePath = getConfigFolderPath() + filePath;
    	File file = new File(filePath);
    	Long lastModified = lastModifiedMap.get(file.getAbsoluteFile());
    	if (lastModified == null || lastModified < file.lastModified())
    		return true;
    	return false;
    }

    public static final byte[] readConfigurationFile(String filePath) throws SystemException {
    	String absoluteFilePath = getConfigFolderPath() + filePath;
    	File file = new File(absoluteFilePath);
        return readConfigurationAbsFile(file);
    }
    
    public static final byte[] readConfigurationAbsFile(File file) throws SystemException {
    	try {
        	String absoluteFilePath=file.getAbsolutePath();
            if (!file.exists()) {
            	throw new FileNotFoundException(file.getAbsolutePath());
            }
            Long lastModified = lastModifiedMap.get(absoluteFilePath);
            //File file = new File(uri);
            if (lastModified == null || lastModified < file.lastModified()) {
                byte[] bytes = null;
                synchronized (lastModifiedMap) {
                    lastModified = lastModifiedMap.get(absoluteFilePath);
                    if (lastModified == null || lastModified < file.lastModified()) {
                        bytes = ServiceUtils.readAllBytes(file);
                        lastModifiedMap.put(absoluteFilePath, file.lastModified());
                    }
                }
                return bytes;
            }
        } catch (Exception e) {
            ServiceUtils.printException("EKA_MWS_1000 : " + file.getAbsolutePath(), e);
            throw new SystemException("EKA_MWS_1000", e);
        }
        return null;
    }
    
    

    public static final void initConfig(String[] args) throws Exception {
		InetAddress localhost = InetAddress.getLocalHost();
		local_IP=localhost.getHostAddress().trim();
		if (args == null || args.length == 0 || args[0].trim().length() == 0) {
			configFolderPath=MiddlewareServer.class.getResource("/").toString()+"resources/config/";
			configureLog4j();
			LOGGER.info("Please provide the path config folder(format:- D:\\server\\config\\)");
		}else { // */
			configFolderPath = args[0];
			File file=new File(configFolderPath);
			configFolderPath=file.getAbsolutePath();
			configureLog4j();
		}
		String ip=ServiceUtils.getServerProperty("middleware.server.remote_ip");
		if(ip!=null) {
			local_IP=ip;
		}
		//System.out.println(configFolderPath);
	}
    
    private static void configureLog4j() throws Exception {
		LoggerContext context = (org.apache.logging.log4j.core.LoggerContext) LogManager.getContext(false);
		URL url = new File(getConfigFolderPath()+"log4j2.xml").toURL();
		// this will force a reconfiguration
		context.setConfigLocation(url.toURI());
	}
    
    public static final void writeConfigurationFile(String fileName,byte data[]) throws SystemException {
		try {
			String fullFilePath = getConfigFolderPath() + fileName;
			//URL url = new URL(profileFilePath);
			File file = new File(fullFilePath);
			FileOutputStream fos=new FileOutputStream(file);
			fos.write(data);
			fos.flush();
			fos.close();
		} catch (Exception e) {
			throw new SystemException("EKA_MWS_1007", e);
		}
	}

	public static String getConfigFolderPath() {
		return configFolderPath+"/";
	}

	public static String getLocal_IP() {
		return local_IP;
	}

}
