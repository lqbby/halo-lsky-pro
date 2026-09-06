package me.chenhe.halo.lskypro.client;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
public class LskyProClient {
    protected WebClient client;
    protected final boolean isV2Api;

    public LskyProClient(@NotNull String server, @Nullable String token) {
        this(server, token, "v1");
    }

    public LskyProClient(@NotNull String server, @Nullable String token,
        @NotNull String apiVersion) {
        final String apiPath = "v2".equals(apiVersion) ? "api/v2" : "api/v1";
        this.isV2Api = "v2".equals(apiVersion);
        final String baseUrl = server + (server.endsWith("/") ? "" : "/") + apiPath;

        var builder = WebClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("Accept", "application/json")
            .filter(errorHandler());
        if (StringUtils.hasText(token)) {
            builder = builder.defaultHeader("Authorization", "Bearer " + token);
        }
        client = builder.build();
    }

    protected static ExchangeFilterFunction errorHandler() {
        return ExchangeFilterFunction.ofResponseProcessor(resp -> {
            if (resp.statusCode().is5xxServerError()) {
                return resp.bodyToMono(String.class)
                    .flatMap(errorBody ->
                        Mono.error(new LskyProException(resp.statusCode(), errorBody)));
            } else if (!resp.statusCode().is2xxSuccessful()) {
                return resp.bodyToMono(LskyResponse.class).flatMap(body ->
                    Mono.error(new LskyProException(resp.statusCode(), body.message)));
            }
            // 2xx
            return Mono.just(resp);
        });
    }

    public Mono<UploadResponse> upload(
        @NotNull Flux<DataBuffer> content,
        @Nullable String filename,
        @Nullable MediaType contentType,
        @Nullable Integer strategyId,
        @Nullable Integer albumId,
        long fileSize
    ) {

        if (isV2Api && strategyId == null) {
            return Mono.error(new LskyProException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Lsky Pro v2 商业版上传必须提供储存策略 ID (storage_id)。"
                    + "请在存储策略中填写「储存策略 ID」。"));
        }

        final var bodyBuilder = new MultipartBodyBuilder();
        final var filePartBuilder = bodyBuilder.asyncPart("file", content, DataBuffer.class);
        if (filename != null) {
            filePartBuilder.filename(filename);
        }
        if (contentType != null) {
            filePartBuilder.contentType(contentType);
        } else if (filename != null) {
            final var t = MediaTypeFactory.getMediaType(filename);
            filePartBuilder.contentType(t.orElse(MediaType.APPLICATION_OCTET_STREAM));
        }
        if (strategyId != null) {
            bodyBuilder.part(isV2Api ? "storage_id" : "strategy_id", strategyId);
        }
        if (albumId != null) {
            bodyBuilder.part("album_id", albumId);
        }

        return client.post()
            .uri("/upload")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<LskyResponse<Map<String, Object>>>() {
            })
            .flatMap(this::checkResponse)
            .flatMap((data) -> {
                final var resp = UploadResponse.fromMap(data, fileSize);
                final var hasUrl = (resp.links() != null && StringUtils.hasText(resp.links().url()))
                    || StringUtils.hasText(resp.publicUrl());
                if (!hasUrl) {
                    return Mono.error(
                        new LskyProException(HttpStatus.OK, "links or url is empty"));
                }
                return Mono.just(resp);
            });
    }

    public Mono<Void> delete(@NotNull String key) {
        if (isV2Api) {
            // v2 deletes by numeric image id via DELETE /user/photos (JSON body [id]).
            Integer id;
            try {
                id = Integer.valueOf(key.trim());
            } catch (NumberFormatException e) {
                log.warn(
                    "Skip deleting from LskyPro v2: '{}' is not a numeric image id (legacy "
                        + "pathname?), cannot sync delete.", key);
                return Mono.empty();
            }
            return client.method(HttpMethod.DELETE)
                .uri("/user/photos")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(List.of(id))
                .retrieve()
                .toBodilessEntity()
                .then();
        }
        return client.delete()
            .uri("/images/" + key)
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<LskyResponse<Map<String, Object>>>() {
            })
            .flatMap((this::checkResponse))
            .then();
    }

    /**
     * Verify that the Lsky Pro API response status is {@code true}.
     * Supports v1 boolean {@code true} and v2 string {@code "success"}/{"true"}.
     */
    <T> Mono<T> checkResponse(LskyResponse<T> resp) {
        if (resp.isSuccess()) {
            return Mono.justOrEmpty(resp.data);
        }
        return Mono.error(
            new LskyProException(HttpStatus.OK, "status=" + resp.status + ": " + resp.message));
    }

    public record LskyResponse<T>(Object status, String message, T data) {
        public boolean isSuccess() {
            if (status instanceof Boolean b) {
                return b;
            }
            if (status instanceof String s) {
                return "true".equalsIgnoreCase(s) || "success".equalsIgnoreCase(s);
            }
            return false;
        }
    }

}
