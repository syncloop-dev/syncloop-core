package com.eka.middleware.licensing;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;

public class LicenseFile implements Serializable {

    @Getter @Setter
    private String licenseName;

    @Getter @Setter
    private Date expiry;

    @Getter @Setter
    private Date issuedOn;

    @Setter
    String instanceUUID;

    @Setter
    String instanceClusterUUID;

    @Setter @Getter
    private String tenant;

    @Setter @Getter
    private LicenseValidityType licenseValidityType;

    @Setter @Getter
    private long totalCredits;

    private long currentCredits;

    @Setter @Getter
    private long perHourCreditSpend;

    public long daysLeftInExpiring() {
        LocalDate today = LocalDate.now();
        LocalDate licenseIssuedDate = expiry.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        return ChronoUnit.DAYS.between(today, licenseIssuedDate);
    }

    private long daysFromIssued() {
        LocalDateTime today = issuedOn.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        LocalDateTime licenseIssuedDate = LocalDateTime.now();
        return ChronoUnit.HOURS.between(today, licenseIssuedDate);
    }

    public long getCurrentCredits() {

        long hours = daysFromIssued();
        long totalCreditShouldSpend = hours * perHourCreditSpend;
        currentCredits = totalCredits - totalCreditShouldSpend;
        return currentCredits < 0 ? 0 : currentCredits;
    }

    public static enum LicenseValidityType {
        DATE, CREDIT, NO_LIMIT
    }

    public static enum LicenseFeatures {

    }


}
