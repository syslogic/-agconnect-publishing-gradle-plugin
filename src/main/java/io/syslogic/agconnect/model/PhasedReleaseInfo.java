package io.syslogic.agconnect.model;

import com.google.gson.annotations.SerializedName;

/**
 * Abstract Model: PhasedReleaseInfo
 *
 * @author Martin Zeitler
 */
@SuppressWarnings("unused")
public class PhasedReleaseInfo {

    /**
     * Status of the release by phase.
     * SUSPEND: suspended
     * RELEASE: released
     * CANCEL: canceled
     * DRAFT: draft
     */
    @SerializedName("state")
    private String state;

    /**
     * Start time of phased release, in UTC format.
     * The format is yyyy-MM-dd'T'HH:mm:ssZZ.
     * Example: 2015-01-01T01:01:01+0800
     */
    @SerializedName("phasedReleaseStartTime")
    private String phasedReleaseStartTime;

    /**
     * End time of phased release, in UTC format.
     * The format is yyyy-MM-dd'T'HH:mm:ssZZ.
     * Example: 2015-01-01T01:01:01+0800
     */
    @SerializedName("phasedReleaseEndTime")
    private String phasedReleaseEndTime;

    /**
     * Phased release percentage.
     * The value ranges from 0.00 to 100.00, accurate to two decimal places.
     * Do not include a percent sign (%) in the value.
     */
    @SerializedName("phasedReleasePercent")
    private String phasedReleasePercent;

    /** Phased release description. */
    @SerializedName("phasedReleaseDescription")
    private String phasedReleaseDescription;

    public String getState() {
        return this.state;
    }

    public String getPhasedReleaseStartTime() {
        return this.phasedReleaseStartTime;
    }

    public String getPhasedReleaseEndTime() {
        return this.phasedReleaseEndTime;
    }

    public String getPhasedReleasePercent() {
        return this.phasedReleasePercent;
    }

    public String getPhasedReleaseDescription() {
        return this.phasedReleaseDescription;
    }

}
