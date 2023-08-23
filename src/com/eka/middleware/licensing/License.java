package com.eka.middleware.licensing;

import com.eka.middleware.service.DataPipeline;
import com.eka.middleware.service.PropertyManager;
import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.util.Base64;

public class License {

    private static LicenseFile readLicense(DataPipeline dataPipeline) throws Exception {

        FileInputStream fileInputStream = new FileInputStream(PropertyManager.getPackagePath(dataPipeline.rp.getTenant()) + "builds/LICENSE.BIN");
        String content = IOUtils.toString(fileInputStream);
        fileInputStream.close();

        byte[] obj = Base64.getDecoder().decode(content);

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(obj);
        ObjectInputStream inputStream = new ObjectInputStream(byteArrayInputStream);
        LicenseFile licenseFile = (LicenseFile) inputStream.readObject();
        return licenseFile;
    }

    public static LicenseFile getLicenseFile(DataPipeline dataPipeline) {
        try {
            return readLicense(dataPipeline);
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isLicenseFound(DataPipeline dataPipeline) {
        boolean isExist = new File(PropertyManager.getPackagePath(dataPipeline.rp.getTenant()) + "builds/LICENSE.BIN").exists();
        if (!isExist) {
            return false;
        }

        try {
            readLicense(dataPipeline);
        } catch (Exception e) {
            return false;
        }

        return true;
    }
}
