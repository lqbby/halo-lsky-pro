package me.chenhe.halo.lskypro;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import me.chenhe.halo.lskypro.client.LskyProClient;
import me.chenhe.halo.lskypro.client.LskyProException;
import me.chenhe.halo.lskypro.client.UploadResponse;
import org.pf4j.Extension;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.server.ServerErrorException;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.app.core.attachment.ThumbnailSize;
import run.halo.app.core.extension.attachment.Attachment;
import run.halo.app.core.extension.attachment.Policy;
import run.halo.app.core.extension.attachment.endpoint.AttachmentHandler;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.Metadata;
import run.halo.app.infra.utils.JsonUtils;

@Slf4j
@Extension
public class LskyProAttachmentHandler implements AttachmentHandler {

    public static final String IMAGE_KEY = "lskypro.plugin.halo.chenhe.me/image-key";
    public static final String IMAGE_LINK = "lskypro.plugin.halo.chenhe.me/image-link";
    public static final String INSTANCE_ID = "lskypro.plugin.halo.chenhe.me/instance-id";


    @Override
    public Mono<Attachment> upload(UploadContext uploadContext) {
        return Mono.just(uploadContext)
            .filter(ctx -> shouldHandle(ctx.policy(), ctx.file()))
            .flatMap(ctx -> {
                final var properties = getProperties(ctx.configMap());
                final var instanceId = getInstanceId(properties, ctx.policy());
                return upload(ctx, properties)
                    .subscribeOn(Schedulers.boundedElastic())
                    .onErrorMap(LskyProAttachmentHandler::handleError)
                    .map(resp -> buildAttachment(resp, instanceId));
            });
    }

    @Override
    public Mono<Attachment> delete(DeleteContext deleteContext) {
        return Mono.just(deleteContext)
            .filter((ctx) -> shouldHandle(ctx.policy(), null))
            .flatMap((ctx) -> {
                final var key = getImageKey(ctx.attachment());
                if (key.isEmpty()) {
                    log.warn(
                        "Cannot obtain image key from attachment {}, skip deleting from LskyPro.",
                        ctx.attachment().getMetadata().getName());
                    return Mono.just(ctx);
                }

                final var properties = getProperties(ctx.configMap());
                final var instanceId = getInstanceId(properties, ctx.policy());
                final var imageInstanceId = getInstanceId(ctx.attachment());
                if (imageInstanceId.isEmpty() || !imageInstanceId.get().equals(instanceId)) {
                    log.warn(
                        "Attachment {} instance ID does not match, skip deleting from LskyPro.",
                        ctx.attachment().getMetadata().getName());
                    return Mono.just(ctx);
                }

                return delete(key.get(), properties)
                    .then(Mono.just(ctx))
                    .doOnSuccess(v -> log.debug("Attachment {} deleted from LskyPro.",
                        ctx.attachment().getMetadata().getName()));
            })
            .onErrorMap(LskyProAttachmentHandler::handleError)
            .map(DeleteContext::attachment);
    }

    @Override
    public Mono<URI> getSharedURL(Attachment attachment, Policy policy, ConfigMap configMap,
        Duration ttl) {
        return getPermalink(attachment, policy, configMap);
    }

    @Override
    public Mono<URI> getPermalink(Attachment attachment, Policy policy, ConfigMap configMap) {
        if (!shouldHandle(policy, null)) {
            return Mono.empty();
        }

        return Optional.ofNullable(attachment.getStatus())
            .map(Attachment.AttachmentStatus::getPermalink)
            .or(() -> getImageLink(attachment))
            .map(s -> Mono.just(URI.create(s)))
            .orElseGet(Mono::empty);
    }

    @Override
    public Mono<Map<ThumbnailSize, URI>> getThumbnailLinks(Attachment attachment, Policy policy,
        ConfigMap configMap) {
        if (!shouldHandle(policy, null)) {
            return Mono.just(Map.of());
        }
        // 云处理（多尺寸缩略图）是商业版 v2 的能力：通过 ?w={width} 参数按需生成。
        // 开源版 v1 只有单个预定义缩略图，无法映射 Halo 的 S/M/L/XL 四档，保持空实现。
        final var properties = getProperties(configMap);
        if (!"v2".equals(properties.getApiVersion())) {
            return Mono.just(Map.of());
        }

        final var originalUrl = Optional.ofNullable(attachment.getStatus())
            .map(Attachment.AttachmentStatus::getPermalink)
            .or(() -> getImageLink(attachment));
        if (originalUrl.isEmpty() || !StringUtils.hasText(originalUrl.get())) {
            return Mono.just(Map.of());
        }

        final Map<ThumbnailSize, URI> links = new EnumMap<>(ThumbnailSize.class);
        for (var size : ThumbnailSize.values()) {
            links.put(size, URI.create(appendQuery(originalUrl.get(), "w",
                String.valueOf(size.getWidth()))));
        }
        return Mono.just(links);
    }

    /**
     * Append a query parameter to a URL, tolerating an existing query string.
     */
    static String appendQuery(String url, String key, String value) {
        final String separator = url.contains("?") ? "&" : "?";
        return url + separator + key + "=" + value;
    }

    Mono<Void> delete(String key, LskyProProperties properties) {
        return Mono.defer(() ->
                Mono.just(new LskyProClient(properties.getLskyUrl(), properties.getLskyToken(),
                    properties.getApiVersion()))
            )
            .flatMap((lskyProClient -> lskyProClient.delete(key)));
    }

    Mono<UploadResponse> upload(UploadContext uploadContext, LskyProProperties props) {
        return Mono.defer(() -> {
                final var file = uploadContext.file();
                final long headerLength = file.headers().getContentLength();
                final var client = new LskyProClient(props.getLskyUrl(), props.getLskyToken(),
                    props.getApiVersion());
                final Mono<Optional<Integer>> albumId = resolveAlbumId(client, props);
                // The v2 upload response does not carry a size, so join the content once to
                // measure its real byte length and reuse the joined buffer as the upload body.
                return DataBufferUtils.join(file.content())
                    .flatMap((DataBuffer buffer) -> {
                        final long realSize = buffer.readableByteCount();
                        final long size = realSize > 0 ? realSize
                            : (headerLength > 0 ? headerLength : 0L);
                        return albumId.flatMap(albumIdOpt -> client.upload(Flux.just(buffer),
                            file.filename(), null, props.getLskyStrategy(), albumIdOpt.orElse(null),
                            size, props.getRemoveExif(), props.getPublicImage()));
                    });
            });
    }

    /**
     * Resolve the album id for the upload: explicit album id wins; otherwise, if v2 auto-archive
     * is enabled, reuse or create a dated album (e.g. {@code 2026-09}); otherwise empty.
     */
    private Mono<Optional<Integer>> resolveAlbumId(LskyProClient client, LskyProProperties props) {
        if (props.getLskyAlbumId() != null) {
            return Mono.just(Optional.of(props.getLskyAlbumId()));
        }
        if (!"v2".equals(props.getApiVersion()) || !Boolean.TRUE.equals(props.getAutoAlbum())) {
            return Mono.just(Optional.empty());
        }
        final String albumName = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        return client.getOrCreateAlbum(albumName)
            .map(Optional::of)
            .doOnNext(id -> id.ifPresent(v -> log.info("Auto archive to album '{}' (id={})",
                albumName, v)));
    }

    Optional<String> getImageLink(Attachment attachment) {
        return Optional.ofNullable(attachment.getMetadata().getAnnotations().get(IMAGE_LINK));
    }

    Optional<String> getImageKey(Attachment attachment) {
        return Optional.ofNullable(attachment.getMetadata().getAnnotations().get(IMAGE_KEY));
    }

    Optional<String> getInstanceId(Attachment attachment) {
        return Optional.ofNullable(attachment.getMetadata().getAnnotations().get(INSTANCE_ID));
    }

    private @Nullable MediaType getUploadedImageMediaType(final UploadResponse uploadResponse) {
        // The lsky pro returns media type of the raw uploaded file, which may different from the
        // persistent one after image processing.
        // So, try to infer the real type from file name. e.g. a.png -> image/png

        final String filename = uploadResponse.name();
        final MediaType inferredMediaType = MediaTypeFactory.getMediaType(filename).orElse(null);
        MediaType returnedMediaType = null;
        try {
            returnedMediaType = MediaType.parseMediaType(uploadResponse.mimetype());
        } catch (InvalidMediaTypeException ignored) {
        }

        if (inferredMediaType == null && returnedMediaType == null) {
            log.warn("No media type in API response nor can it be inferred from the name {}",
                filename);
            return null;
        }

        if (inferredMediaType != null && returnedMediaType != null) {
            if (!inferredMediaType.equals(returnedMediaType)) {
                log.debug("Use inferred media type '{}', rather than '{}'", inferredMediaType,
                    returnedMediaType);
            }
            return inferredMediaType;
        }

        return inferredMediaType != null ? inferredMediaType : returnedMediaType;
    }

    Attachment buildAttachment(UploadResponse uploadResponse, @Nonnull String instanceId) {
        Assert.hasText(instanceId, "instanceId cannot be empty");

        // v2 has public_url directly; v1 has links.url
        final String url;
        if (uploadResponse.publicUrl() != null) {
            url = uploadResponse.publicUrl();
        } else {
            Assert.notNull(uploadResponse.links(), "links cannot be null");
            Assert.hasText(uploadResponse.links().url(), "url cannot be empty");
            url = uploadResponse.links().url();
        }

        // v1: origin_name; v2: name or filename
        final var displayName = StringUtils.hasText(uploadResponse.origin_name())
            ? uploadResponse.origin_name()
            : (StringUtils.hasText(uploadResponse.name())
                ? uploadResponse.name()
                : uploadResponse.filename());

        // v1 deletes by key; v2 deletes by numeric id (see UploadResponse#getDeletionKey).
        final String imageKey = uploadResponse.getDeletionKey();
        Assert.hasText(imageKey, "cannot determine the image deletion key from upload response");

        final var mediaType = getUploadedImageMediaType(uploadResponse);

        final var metadata = new Metadata();
        metadata.setGenerateName(UUID.randomUUID().toString());
        metadata.setAnnotations(Map.of(
            IMAGE_KEY, imageKey,
            IMAGE_LINK, url,
            INSTANCE_ID, instanceId
        ));

        var spec = new Attachment.AttachmentSpec();
        spec.setSize(uploadResponse.getSizeBytes());
        spec.setDisplayName(displayName);
        if (mediaType != null) {
            spec.setMediaType(mediaType.toString());
        }

        final var status = new Attachment.AttachmentStatus();
        status.setPermalink(url);
        final var attachment = new Attachment();
        attachment.setMetadata(metadata);
        attachment.setSpec(spec);
        attachment.setStatus(status);

        log.debug("Built attachment {} successfully", imageKey);
        return attachment;
    }

    static Throwable handleError(Throwable t) {
        if (t instanceof LskyProException e) {
            if (e.statusCode.value() == 401) {
                return new ServerWebInputException(
                    "Lsky Pro authentication failed, please check your API token.");
            } else if (e.statusCode.value() == 403) {
                return new ServerWebInputException(
                    "Lsky Pro API may have been disabled (HTTP 403).");
            } else if (e.statusCode.value() == 429) {
                return new ServerWebInputException(
                    "Lsky Pro API error: usage quota exceeded (HTTP 429).");
            }
            return new ServerWebInputException(
                "LskyPro API error (HTTP %d): %s".formatted(e.statusCode.value(), e.getMessage()));
        } else if (t instanceof WebClientRequestException e) {
            return new ServerWebInputException(
                "Failed to request LskyPro API: %s".formatted(e.getMessage()));
        }
        return t;
    }

    /**
     * Whether the current request should be handled by this plugin.
     */
    boolean shouldHandle(Policy policy, @Nullable FilePart filePart) {
        // check policy
        if (policy == null || policy.getSpec() == null ||
            policy.getSpec().getTemplateName() == null) {
            return false;
        }
        String templateName = policy.getSpec().getTemplateName();
        if (!"chenhe-lsky-pro".equals(templateName)) {
            return false;
        }

        // check media type
        if (filePart == null) {
            // not an upload request, no need to check the media type
            return true;
        }
        final var mediaType = MediaTypeFactory.getMediaType(filePart.filename());
        if (mediaType.isEmpty()) {
            log.warn("Ignore attachment request {} due to empty media type", filePart.filename());
            return false;
        }
        if (!"image".equals(mediaType.get().getType())) {
            log.warn("Ignore attachment request {} due to non-image media type: {}",
                filePart.filename(), mediaType.get());
            return false;
        }
        return true;
    }

    /**
     * Each image must be associated with an instance ID. If the user does not specify it, an ID
     * associated with the current policy is automatically generated.
     * <p>
     * Images (attachments) associated with the same instance ID are considered to be managed
     * by current storage policy. This allows the relationship between attachments and LskyPro
     * server to be preserved even after the plug-in is reinstalled or the server address is
     * changed.
     *
     * @return Non-empty instance id of current policy.
     */
    @Nonnull
    String getInstanceId(LskyProProperties properties, Policy policy) {
        if (StringUtils.hasText(properties.getInstanceId())) {
            return properties.getInstanceId();
        }
        try {
            final var url = new URL(properties.getLskyUrl());
            final var idBuilder = new StringBuilder().append(url.getHost()).append(url.getPath());
            if (url.getPort() != -1) {
                idBuilder.append(':').append(url.getPort());
            }
            final var instanceId = idBuilder.toString();
            log.debug("Use default instance id '{}' for policy {}", instanceId,
                policy.getMetadata().getName());
            return instanceId;
        } catch (MalformedURLException e) {
            throw new ServerErrorException("Invalid LskyPro server: " + properties.getLskyUrl(), e);
        }
    }

    LskyProProperties getProperties(ConfigMap configMap) {
        var settingJson = configMap.getData().getOrDefault("default", "{}");
        return JsonUtils.jsonToObject(settingJson, LskyProProperties.class);
    }
}
