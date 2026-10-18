package com.neighbormarket.image;

import org.springframework.core.io.Resource;

import java.nio.file.Path;
import java.util.Optional;

public interface ImageStorage {
    StoredImage store(Path source, String extension, String mediaType);
    Optional<StoredResource> open(String publicPath);
    void delete(String publicPath);
    boolean isManaged(String publicPath);

    record StoredResource(Resource resource, String mediaType, long size) {}
}
