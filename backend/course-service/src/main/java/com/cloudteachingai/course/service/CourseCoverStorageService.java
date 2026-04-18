package com.cloudteachingai.course.service;

import com.cloudteachingai.course.exception.BusinessException;
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
public class CourseCoverStorageService {

    private static final String COVER_URL_PREFIX = "/api/v1/course-covers/";
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp");

    private final Path coverStorageDir;

    public CourseCoverStorageService(@Value("${app.storage-root:/app/storage}") String storageRoot) {
        this.coverStorageDir = Paths.get(storageRoot, "course-covers").toAbsolutePath().normalize();
    }

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("Cover image is required");
        }

        String contentType = file.getContentType();
        if (!StringUtils.hasText(contentType) || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw BusinessException.badRequest("Only image files are supported");
        }

        String extension = resolveExtension(file.getOriginalFilename(), contentType);
        String filename = UUID.randomUUID() + extension;
        Path target = resolveInsideStorage(filename);

        try {
            Files.createDirectories(coverStorageDir);
            file.transferTo(target);
        } catch (Exception ex) {
            throw BusinessException.internal("Failed to store cover image");
        }

        return COVER_URL_PREFIX + filename;
    }

    public Resource loadAsResource(String filename) {
        Path target = resolveInsideStorage(filename);
        try {
            if (!Files.exists(target) || !Files.isReadable(target)) {
                throw BusinessException.notFound("Cover image not found");
            }
            return new UrlResource(target.toUri());
        } catch (IOException ex) {
            throw BusinessException.notFound("Cover image not found");
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

    public void deleteIfManaged(String coverUrl) {
        String filename = extractManagedFilename(coverUrl);
        if (filename == null) {
            return;
        }

        Path target = resolveInsideStorage(filename);
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // Keep business updates stable even if stale files cannot be removed.
        }
    }

    private Path resolveInsideStorage(String filename) {
        if (!StringUtils.hasText(filename)) {
            throw BusinessException.badRequest("Invalid cover image path");
        }

        Path target = coverStorageDir.resolve(filename).normalize();
        if (!target.startsWith(coverStorageDir)) {
            throw BusinessException.badRequest("Invalid cover image path");
        }
        return target;
    }

    private String extractManagedFilename(String coverUrl) {
        if (!StringUtils.hasText(coverUrl) || !coverUrl.startsWith(COVER_URL_PREFIX)) {
            return null;
        }
        return coverUrl.substring(COVER_URL_PREFIX.length());
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
