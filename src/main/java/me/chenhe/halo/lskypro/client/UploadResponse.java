package me.chenhe.halo.lskypro.client;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;

/**
 * Upload response supporting both v1 and v2 Lsky Pro API.
 */
public record UploadResponse(
    /** v1: image key; v2: not present */
    @Nullable String key,
    /** v1/v2: image name */
    @Nullable String name,
    /** v1: original name; v2: not present */
    @Nullable String origin_name,
    /** v2: filename */
    @Nullable String filename,
    /** v1/v2: file extension */
    @Nullable String extension,
    /** v1/v2: SHA1 hash */
    @Nullable String sha1,
    /** v1: file size in KB; v2: not present */
    @Nullable Float size,
    /** v1/v2: MIME type */
    @Nullable String mimetype,
    /** v1: links object with url */
    @Nullable Links links,
    /** v2: public URL directly in data */
    @Nullable @JsonProperty("public_url") String publicUrl,
    /** v2: pathname used as key for management */
    @Nullable String pathname,
    /** v2: numeric id */
    @Nullable Integer id,
    /** Fallback file size in bytes, set by client before building attachment */
    @JsonIgnore long fallbackSize
) {
    public UploadResponse withFallbackSize(long fallbackSizeBytes) {
        return new UploadResponse(key, name, origin_name, filename, extension, sha1,
            size, mimetype, links, publicUrl, pathname, id, fallbackSizeBytes);
    }

    /** Returns size in KB, using fallback if v2 API didn't provide it */
    @JsonIgnore
    public long getSizeBytes() {
        if (size != null) {
            return (long) (size * 1024L);
        }
        return fallbackSize;
    }
}