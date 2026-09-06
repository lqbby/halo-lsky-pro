package me.chenhe.halo.lskypro;

import jakarta.annotation.Nullable;
import lombok.Data;
import org.springframework.util.StringUtils;

/**
 * The properties of storage policy that uses this plugin as backend.
 * <p>
 * This data class is bound to {@code policy-template-lskypro.yaml}.
 */
@Data
public class LskyProProperties {

    /**
     * Including protocol, without trailing {@code /} or api path.
     */
    private String lskyUrl;

    /**
     * Without leading {@code Bearer}.
     */
    private String lskyToken;

    private Integer lskyStrategy;

    private Integer lskyAlbumId;

    /**
     * v2 only: whether to strip EXIF metadata from the uploaded image.
     */
    private Boolean removeExif;

    /**
     * v2 only: whether the uploaded image is public.
     */
    private Boolean publicImage;

    /**
     * v2 only: when enabled and no explicit album id is set, auto-create (or reuse) a dated
     * album (e.g. {@code 2026-09}) for the upload.
     */
    private Boolean autoAlbum;

    /**
     * API version. "v1" for open-source edition, "v2" for commercial edition.
     */
    private String apiVersion = "v1";

    /**
     * User-specified instance ID.
     */
    private @Nullable String instanceId;

    @SuppressWarnings("unused")
    public void setLskyUrl(String lskyUrl) {
        final var fileSeparator = "/";
        final var apiV1Suffix = "/api/v1";
        final var apiV2Suffix = "/api/v2";
        if (!StringUtils.hasText(lskyUrl)) {
            this.lskyUrl = null;
            return;
        }
        if (lskyUrl.endsWith(fileSeparator)) {
            lskyUrl = lskyUrl.substring(0, lskyUrl.length() - 1);
        }
        if (lskyUrl.endsWith(apiV2Suffix)) {
            lskyUrl = lskyUrl.substring(0, lskyUrl.length() - apiV2Suffix.length());
        } else if (lskyUrl.endsWith(apiV1Suffix)) {
            lskyUrl = lskyUrl.substring(0, lskyUrl.length() - apiV1Suffix.length());
        }
        this.lskyUrl = lskyUrl;
    }

    @SuppressWarnings("unused")
    public void setLskyToken(@Nullable String lskyToken) {
        if (!StringUtils.hasText(lskyToken)) {
            this.lskyToken = null;
            return;
        }
        lskyToken = lskyToken.trim();
        final String prefix = "Bearer";
        if (lskyToken.startsWith(prefix)) {
            lskyToken = lskyToken.substring(prefix.length());
        }
        this.lskyToken = StringUtils.hasText(lskyToken) ? lskyToken : null;
    }

    @SuppressWarnings("unused")
    public void setInstanceId(String instanceId) {
        if (StringUtils.hasText(instanceId)) {
            this.instanceId = instanceId;
        } else {
            this.instanceId = null;
        }
    }
}
