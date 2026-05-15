package com.cloudteachingai.user.service;

import com.cloudteachingai.user.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class AvatarStorageService {

    private static final String AVATAR_URL_PREFIX = "/api/v1/users/avatars/";
    private static final long MAX_AVATAR_BYTES = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp");

    private final Path avatarStorageDir;

    public AvatarStorageService(@Value("${app.storage-root:/app/storage}") String storageRoot) {
        this.avatarStorageDir = Paths.get(storageRoot, "avatars").toAbsolutePath().normalize();
    }

    public String store(MultipartFile file, Long userId) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("Avatar image is required");
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw BusinessException.badRequest("Avatar image must be no larger than 5 MB");
        }

        String contentType = file.getContentType();
        if (!StringUtils.hasText(contentType) || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw BusinessException.badRequest("Only image files are supported");
        }

        String extension = resolveExtension(file.getOriginalFilename(), contentType);
        String filename = userId + "-" + UUID.randomUUID() + extension;
        Path target = resolveInsideStorage(filename);

        try {
            Files.createDirectories(avatarStorageDir);
            file.transferTo(target);
        } catch (IOException ex) {
            throw BusinessException.internalError("Failed to store avatar image");
        }

        return AVATAR_URL_PREFIX + filename;
    }

    public Resource loadAsResource(String filename) {
        Path target = resolveInsideStorage(filename);
        try {
            if (!Files.exists(target) || !Files.isReadable(target)) {
                throw BusinessException.notFound("Avatar image not found");
            }
            return new UrlResource(target.toUri());
        } catch (IOException ex) {
            throw BusinessException.notFound("Avatar image not found");
        }
    }

    public MediaType resolveMediaType(String filename) {
        Path target = resolveInsideStorage(filename);
        try {
            String contentType = Files.probeContentType(target);
            if (StringUtils.hasText(contentType)) {
                return MediaType.parseMediaType(contentType);
            }
        } catch (IOException ignored) {
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    public void deleteIfManaged(String avatarUrl) {
        if (!StringUtils.hasText(avatarUrl) || !avatarUrl.startsWith(AVATAR_URL_PREFIX)) {
            return;
        }

        String filename = avatarUrl.substring(AVATAR_URL_PREFIX.length());
        Path target = resolveInsideStorage(filename);
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // Avatar cleanup should not block profile updates or account deletion.
        }
    }

    private Path resolveInsideStorage(String filename) {
        if (!StringUtils.hasText(filename)) {
            throw BusinessException.badRequest("Invalid avatar image path");
        }

        Path target = avatarStorageDir.resolve(filename).normalize();
        if (!target.startsWith(avatarStorageDir)) {
            throw BusinessException.badRequest("Invalid avatar image path");
        }
        return target;
    }

    private String resolveExtension(String originalFilename, String contentType) {
        String extension = extractExtension(originalFilename);
        if (ALLOWED_EXTENSIONS.contains(extension)) {
            return extension;
        }

        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            default -> throw BusinessException.badRequest("Unsupported image format");
        };
    }

    private String extractExtension(String originalFilename) {
        if (!StringUtils.hasText(originalFilename) || !originalFilename.contains(".")) {
            return "";
        }
        return originalFilename.substring(originalFilename.lastIndexOf('.')).toLowerCase(Locale.ROOT);
    }
}
