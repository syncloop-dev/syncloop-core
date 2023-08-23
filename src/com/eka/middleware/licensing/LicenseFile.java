package com.eka.middleware.licensing;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Getter @Setter
public class LicenseFile implements Serializable {

    private String licenseName;

    private Date expiry;

    public long daysLeftInExpiring() {
        LocalDate today = LocalDate.now();
        LocalDate licenseIssuedDate = expiry.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        return ChronoUnit.DAYS.between(today, licenseIssuedDate);
    }
}
