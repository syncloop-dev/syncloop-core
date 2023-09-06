package com.eka.middleware.licensing;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;

public class LicenseFile implements Serializable {

    @Getter @Setter
    private String licenseName;

    @Getter @Setter
    private Date expiry;

    @Setter
    String instanceUUID;

    @Setter
    String instanceClusterUUID;

    @Setter @Getter
    private String tenant;

    public long daysLeftInExpiring() {
        LocalDate today = LocalDate.now();
        LocalDate licenseIssuedDate = expiry.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        return ChronoUnit.DAYS.between(today, licenseIssuedDate);
    }
}
