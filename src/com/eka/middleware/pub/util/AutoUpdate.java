package com.eka.middleware.pub.util;

import com.eka.middleware.service.DataPipeline;
import com.eka.middleware.service.PropertyManager;
import com.eka.middleware.service.ServiceUtils;
import com.eka.middleware.template.SnippetException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;


public class AutoUpdate {

    public static final void main(DataPipeline dataPipeline) throws SnippetException {
        try {
            String url = dataPipeline.getAsString("url");
            downloadFile(url, dataPipeline);



        } catch (
                Exception e) {
            dataPipeline.clear();
            dataPipeline.put("error", e.getMessage());
            dataPipeline.setResponseStatus(500);
            dataPipeline.put("status", "Not Modified");
            new SnippetException(dataPipeline, "Failed while saving file", new Exception(e));
        }

    }
    private static boolean importURLAliases(String UrlAliasFilepath, DataPipeline dp) throws Exception{
        Boolean importSuccessful=true;
        Properties prop=new Properties();
        File file=new File(UrlAliasFilepath);
        if(!file.exists()){
            dp.put("msg","Alias file not found. Path: "+UrlAliasFilepath);
            return true;
        }
        FileInputStream aliasFIS=new FileInputStream(file);
        prop.load(aliasFIS);
        prop.forEach((k,v)->{
            if(dp.getString("error")==null){
                String key=(String)k;
                String value=(String)v;
                dp.put("fqn",value);
                dp.put("alias",key);
                try{
                    dp.apply("packages.middleware.pub.server.browse.registerURLAlias");
                    String msg=dp.getString("msg");
                    if(!"Saved".equals(msg))
                        dp.put("error",msg);

                }catch(Exception e){
                    dp.put("error",e.getMessage());
                }
                dp.drop("fqn");
                dp.drop("alias");
            }
        });
        aliasFIS.close();
        if(dp.getString("error")!=null)
            importSuccessful=false;
        file.delete();
        return importSuccessful;
    }

    private static void unzip(String zipFilePath, String destDir,DataPipeline dp) throws Exception{
        File dir = new File(destDir);
        String unZippedFolderPath=null;
        // create output directory if it doesn't exist
        if(!dir.exists()) dir.mkdirs();
        FileInputStream fis;
        //buffer for read and write data to file

        fis = new FileInputStream(zipFilePath);
        ZipInputStream zis = new ZipInputStream(fis);
        ZipEntry ze = zis.getNextEntry();
        while(ze != null){
            String fileName = ze.getName();
            if(unZippedFolderPath==null)
                unZippedFolderPath=fileName;
            else{
                dp.log("Zipped entry "+fileName);
                fileName=("#$"+fileName).replace("#$"+unZippedFolderPath,"");
                File newFile = new File(destDir + File.separator + fileName);
                new File(newFile.getParent()).mkdirs();
                byte[] buffer = new byte[1024];
                int len = zis.read(buffer);
                if(len>0){
                    FileOutputStream fos = new FileOutputStream(newFile);
                    while (len > 0) {
                        fos.write(buffer, 0, len);
                        //buffer = new byte[1024]
                        len = zis.read(buffer);
                    }
                    fos.flush();
                    fos.close();
                }
            }
            //close this ZipEntry
            zis.closeEntry();
            ze = zis.getNextEntry();
        }
        //close last ZipEntry
        zis.closeEntry();
        zis.close();
        fis.close();
    }

    private static void createRestorePoint(String buildName, DataPipeline dataPipeline) throws Exception{
        String packagePath=PropertyManager.getPackagePath(dataPipeline.rp.getTenant());
        String bkpDirPath=packagePath+"builds/backup/";
        File dir=new File(bkpDirPath);
        String timeStmp=System.currentTimeMillis()+"";
        if(!dir.exists())
            dir.mkdirs();
        FileOutputStream fos = new FileOutputStream(bkpDirPath+timeStmp+"_packages_"+buildName);
        ZipOutputStream zipOut = new ZipOutputStream(fos);
        File fileToZip = new File(packagePath+"packages/");
        ServiceUtils.zipFile(fileToZip, fileToZip.getName(), zipOut);
        zipOut.flush();
        zipOut.close();
        fos.flush();
        fos.close();
    }

    private static String getFileNameFromUrl(String url) {
        String[] parts = url.split("/");
        return parts[parts.length - 1];
    }


    public static String downloadFile(String url, DataPipeline dataPipeline) throws Exception {
        URL fileUrl = new URL(url);
        String fileName = getFileNameFromUrl(url);

        String downloadLocation = PropertyManager.getPackagePath(dataPipeline.rp.getTenant())+"builds/import/";


        System.out.println("package path: " + PropertyManager.getPackagePath(dataPipeline.rp.getTenant()));
        System.err.println("downloadLocation: " + downloadLocation);


        File downloadedFile = new File(downloadLocation + fileName);
        if (downloadedFile.exists()) {
            System.out.println("File already exists: " + fileName);
        } else {
            try (InputStream in = fileUrl.openStream()) {
                Files.copy(in, downloadedFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                System.out.println("File downloaded successfully: " + fileName);
            }
        }

        // Call the other methods in the class
        String packagePath = PropertyManager.getPackagePath(dataPipeline.rp.getTenant());
        String buildsDirPath = packagePath + "builds/import/";

        // Unzip and deploy
        String location = buildsDirPath + fileName;
        unzip(location, packagePath, dataPipeline);

        // Import URL aliases
        String urlAliasFilePath = packagePath + (("URLAlias_" + fileName + "#").replace(".zip#", ".properties"));
        boolean importSuccessful = importURLAliases(urlAliasFilePath, dataPipeline);

        // Create restore point
        createRestorePoint(fileName, dataPipeline);

        dataPipeline.put("status", "Saved");
        return PropertyManager.getPackagePath(dataPipeline.rp.getTenant()) + "builds/import/" + fileName;

    }

    public static String readJsonFromUrl(String urlString) throws IOException {
        StringBuilder jsonContent = new StringBuilder();

        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        connection.setRequestMethod("GET");

        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        try { String line;
            while ((line = reader.readLine()) != null) {
                jsonContent.append(line);
            }
            reader.close(); } catch (Throwable throwable) { try { reader.close(); }
        catch (Throwable throwable1) { throwable.addSuppressed(throwable1); }
            throw throwable; }
        if (jsonContent.toString().trim().isEmpty()) {
            throw new IOException("Empty JSON content");
        }

        return jsonContent.toString();
    }


    private static String readJsonFromFile(String filePath) throws IOException {
        return new String(Files.readAllBytes(Paths.get(filePath, new String[0])));
    }


    public static String jsonValueFetch(String location, String variableName) throws IOException {
        String jsonString;
        if (isUrl(location)) {
            jsonString = readJsonFromUrl(location);
        } else {
            jsonString = readJsonFromFile(location);
        }

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode root = objectMapper.readTree(jsonString);
        return findValueByVariableName(root, variableName);
    }

    private static String findValueByVariableName(JsonNode node, String variableName) {
        String[] searchKeys = variableName.split("\\.");

        JsonNode currentNode = node;
        for (String key : searchKeys) {
            currentNode = currentNode.get(key);
            if (currentNode == null) {
                return null;
            }
        }

        return currentNode.asText();
    }


    private static boolean isUrl(String location) {
        try {
            (new URL(location)).toURI();
            return true;
        } catch (Exception e) {
            return false;
        }
    }


    public static String checkAndUpdateURL(String filePath, String url) throws IOException {
        String filePathVersion = jsonValueFetch(filePath, "latest.version_number");
        String urlVersion = jsonValueFetch(url, "latest.version_number");
        boolean forceUpdate = Boolean.parseBoolean(jsonValueFetch(url, "updatation.force_update"));
        String downloadableUrl = null;


        if (urlVersion != null && filePathVersion != null && compareVersions(urlVersion, filePathVersion) > 0.0F) {
            String newVersionNumber = jsonValueFetch(url, "latest.version");
            if (newVersionNumber != null) {
                downloadableUrl = updateVersionInURL(newVersionNumber);
            }

            if (forceUpdate) {
                downloadableUrl = updateVersionInURL(newVersionNumber);
            }
        }

        return downloadableUrl;
    }


    private static float compareVersions(String version1, String version2) throws IOException {
        float v1 = Float.parseFloat(version1);
        float v2 = Float.parseFloat(version2);

        if (v1 < v2)
            return -1.0F;
        if (v1 > v2) {
            return 1.0F;
        }
        return 0.0F;
    }


    private static String updateVersionInURL(String newVersion) {
        return String.format("https://eka-distribution.s3.us-west-1.amazonaws.com/eka-distribution-tenant-v%s.zip", newVersion);
    }

    private static String calculateFileChecksum(String filePath) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");

        try (InputStream inputStream = new FileInputStream(filePath)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
        }

        byte[] digest = md.digest();

        StringBuilder checksumBuilder = new StringBuilder();
        for (byte b : digest) {
            checksumBuilder.append(String.format("%02x", b));
        }

        return checksumBuilder.toString();
    }


    private static String getDigestFromUrl(String url) throws IOException {
        return jsonValueFetch(url, "latest.digest");
    }


    public static Boolean compareDigest(String filePath, String url) throws Exception {
        String urlDigest = getDigestFromUrl(url);
        String fileDigest = calculateFileChecksum(filePath);
        return Objects.equals(urlDigest, fileDigest);
    }

}
