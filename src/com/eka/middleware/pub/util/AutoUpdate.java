package com.eka.middleware.pub.util;

import com.eka.middleware.service.DataPipeline;
import com.eka.middleware.service.PropertyManager;
import com.eka.middleware.service.ServiceUtils;
import com.eka.middleware.template.SnippetException;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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


    public static void downloadFile(String url, DataPipeline dataPipeline) throws Exception {
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
    }

}
