package me.chenhe.halo.lskypro.client;

import tools.jackson.annotation.JsonProperty;

public record Links(
    String url,
    @JsonProperty("thumbnail_url")
    String thumbnailUrl
) {
}
