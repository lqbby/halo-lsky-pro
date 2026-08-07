package me.chenhe.halo.lskypro.client;

import java.util.Map;

/**
 * v1 links object. Built from a {@link Map} so it does not depend on Jackson annotation packages
 * that differ between Halo 2.x and 3.x runtimes.
 */
public record Links(
    String url,
    String thumbnailUrl
) {

    static Links fromMap(Map<String, Object> map) {
        return new Links(
            str(map, "url"),
            str(map, "thumbnailUrl", "thumbnail_url")
        );
    }

    private static String str(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            final Object v = map.get(key);
            if (v != null) {
                return v.toString();
            }
        }
        return null;
    }
}
