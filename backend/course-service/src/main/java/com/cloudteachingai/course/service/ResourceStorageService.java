package com.cloudteachingai.course.service;

import com.cloudteachingai.course.entity.enums.ResourceType;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ResourceStorageService {

    private static final String MANAGED_PREFIX = "managed://";

    private static final Map<ResourceType, Set<String>> ALLOWED_EXTENSIONS = Map.of(
            ResourceType.VIDEO, Set.of(".mp4", ".mov", ".m4v", ".webm"),
            ResourceType.DOCUMENT, Set.of(".pdf", ".doc", ".docx"),
            ResourceType.SLIDE, Set.of(".pdf", ".ppt", ".pptx")
    );

    private final Path resourceStorageDir;

    public ResourceStorageService(@Value("${app.storage-root:/app/storage}") String storageRoot) {
        this.resourceStorageDir = Paths.get(storageRoot, "course-resources").toAbsolutePath().normalize();
    }

    public String store(MultipartFile file, ResourceType resourceType) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("Resource file is required");
        }

        String extension = resolveExtension(file.getOriginalFilename(), resourceType);
        String filename = UUID.randomUUID() + extension;
        Path target = resolveInsideStorage(filename);

        try {
            Files.createDirectories(resourceStorageDir);
            file.transferTo(target);
        } catch (Exception ex) {
            throw BusinessException.internal("Failed to store resource file", ex);
        }

        return MANAGED_PREFIX + filename;
    }

    public Resource loadAsResource(String storageKey) {
        Path target = resolveManagedPath(storageKey);
        try {
            if (!Files.exists(target) || !Files.isReadable(target)) {
                throw BusinessException.notFound("Resource file not found");
            }
            return new UrlResource(target.toUri());
        } catch (IOException ex) {
            throw BusinessException.notFound("Resource file not found");
        }
    }

    public MediaType resolveMediaType(String storageKey) {
        Path target = resolveManagedPath(storageKey);
        try {
            String contentType = Files.probeContentType(target);
            if (StringUtils.hasText(contentType)) {
                return MediaType.parseMediaType(contentType);
            }
        } catch (IOException ignored) {
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    public void deleteIfManaged(String storageKey) {
        if (!isManagedStorageKey(storageKey)) {
            return;
        }

        Path target = resolveManagedPath(storageKey);
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // Keep course operations stable even if stale files cannot be removed.
        }
    }

    public boolean isManagedStorageKey(String storageKey) {
        return StringUtils.hasText(storageKey) && storageKey.startsWith(MANAGED_PREFIX);
    }

    public Path resolveManagedFilePath(String storageKey) {
        return resolveManagedPath(storageKey);
    }

    private Path resolveManagedPath(String storageKey) {
        if (!isManagedStorageKey(storageKey)) {
            throw BusinessException.badRequest("Invalid managed resource file path");
        }
        String filename = storageKey.substring(MANAGED_PREFIX.length());
        return resolveInsideStorage(filename);
    }

    private Path resolveInsideStorage(String filename) {
        if (!StringUtils.hasText(filename)) {
            throw BusinessException.badRequest("Invalid resource file path");
        }

        Path target = resourceStorageDir.resolve(filename).normalize();
        if (!target.startsWith(resourceStorageDir)) {
            throw BusinessException.badRequest("Invalid resource file path");
        }
        return target;
    }

    private String resolveExtension(String originalFilename, ResourceType resourceType) {
        String extension = extractExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.getOrDefault(resourceType, Set.of()).contains(extension)) {
            throw BusinessException.badRequest("Unsupported file type for resource");
        }
        return extension;
    }

    private String extractExtension(String originalFilename) {
        if (!StringUtils.hasText(originalFilename) || !originalFilename.contains(".")) {
            return "";
        }
        return originalFilename.substring(originalFilename.lastIndexOf('.')).toLowerCase(Locale.ROOT);
    }
}
