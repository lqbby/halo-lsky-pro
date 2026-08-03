package me.chenhe.halo.lskypro.client;

import jakarta.annotation.Nullable;

/**
 * @param size in KB, may be null in v2 API if size info not available
 */
public record UploadResponse(
    String key,
    String name,
    String origin_name,
    String extension,
    String sha1,
    @Nullable Float size,
    String mimetype,
    Links links
) {
}