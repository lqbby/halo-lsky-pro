package me.chenhe.halo.lskypro.client;

import jakarta.annotation.Nullable;
import java.util.Map;

/**
 * Upload response supporting both v1 and v2 Lsky Pro API.
 *
 * <p>Instances are built from a {@link Map} via {@link #fromMap(Map, long)} rather than
 * letting Jackson deserialize the record directly. This keeps the plugin independent of the
 * Jackson major version shipped by the host Halo (2.x vs 3.x): their annotation packages
 * differ ({@code com.fasterxml.jackson} vs {@code tools.jackson}), so compile-time annotations
 * are unreliable at runtime. The map is also probed with both camelCase and snake_case keys to
 * tolerate either naming the Lsky Pro server uses.
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
    /** v2: public URL directly in data (also accepts snake_case public_url) */
    @Nullable String publicUrl,
    /** v2: pathname used as key for management */
    @Nullable String pathname,
    /** v2: numeric id */
    @Nullable Integer id,
    /** Fallback file size in bytes, set from the real upload content-length */
    long fallbackSize
) {

    static UploadResponse fromMap(Map<String, Object> data, long fileSize) {
        final Object sizeObj = data.get("size");
        final Float size = sizeObj instanceof Number n ? n.floatValue() : null;
        final Links links = data.get("links") instanceof Map
            ? Links.fromMap((Map<String, Object>) data.get("links")) : null;
        return new UploadResponse(
            str(data, "key"),
            str(data, "name"),
            str(data, "origin_name"),
            str(data, "filename"),
            str(data, "extension"),
            str(data, "sha1"),
            size,
            str(data, "mimetype"),
            links,
            str(data, "publicUrl", "public_url"),
            str(data, "pathname"),
            intOrNull(data, "id"),
            fileSize
        );
    }

    static String str(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            final Object v = map.get(key);
            if (v != null) {
                return v.toString();
            }
        }
        return null;
    }

    static Integer intOrNull(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            final Object v = map.get(key);
            if (v instanceof Number n) {
                return n.intValue();
            }
        }
        return null;
    }

    /** Returns size in bytes, using fallback if v2 API didn't provide it */
    public long getSizeBytes() {
        if (size != null) {
            return (long) (size * 1024L);
        }
        return fallbackSize;
    }
}
