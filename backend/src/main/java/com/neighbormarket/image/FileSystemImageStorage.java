package com.neighbormarket.image;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class FileSystemImageStorage implements ImageStorage {
    private static final Pattern MANAGED_PATH = Pattern.compile(
            "^/media/items/([0-9a-f]{2})/([0-9a-f]{32}\\.(jpg|png))$");
    private final Path root;
    private final SecureRandom random = new SecureRandom();

    public FileSystemImageStorage(@Value("${app.image.filesystem-root:${java.io.tmpdir}/neighbormarket-images}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    @Override
    public StoredImage store(Path source, String extension, String mediaType) {
        byte[] id = new byte[16];
        random.nextBytes(id);
        String name = HexFormat.of().formatHex(id) + "." + extension;
        String shard = name.substring(0, 2);
        Path directory = root.resolve(shard);
        Path temporary = directory.resolve("." + name + ".tmp");
        Path destination = directory.resolve(name);
        try {
            Files.createDirectories(directory);
            Files.copy(source, temporary);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, destination);
            }
            return new StoredImage("/media/items/" + shard + "/" + name,
                    mediaType, Files.size(destination));
        } catch (IOException ex) {
            tryDelete(temporary);
            throw new ImageStorageException("图片存储暂时不可用", ex);
        }
    }

    @Override
    public Optional<StoredResource> open(String publicPath) {
        Path path = resolveManaged(publicPath);
        if (path == null || !Files.isRegularFile(path)) return Optional.empty();
        try {
            String mediaType = publicPath.endsWith(".png") ? "image/png" : "image/jpeg";
            return Optional.of(new StoredResource(new FileSystemResource(path), mediaType, Files.size(path)));
        } catch (IOException ex) {
            throw new ImageStorageException("图片读取暂时不可用", ex);
        }
    }

    @Override
    public void delete(String publicPath) {
        Path path = resolveManaged(publicPath);
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            throw new ImageStorageException("旧图片清理失败", ex);
        }
    }

    @Override
    public boolean isManaged(String publicPath) {
        return publicPath != null && MANAGED_PATH.matcher(publicPath).matches();
    }

    private Path resolveManaged(String publicPath) {
        if (publicPath == null) return null;
        Matcher matcher = MANAGED_PATH.matcher(publicPath);
        if (!matcher.matches()) return null;
        Path resolved = root.resolve(matcher.group(1)).resolve(matcher.group(2)).normalize();
        return resolved.startsWith(root) ? resolved : null;
    }

    private void tryDelete(Path path) {
        try { Files.deleteIfExists(path); } catch (IOException ignored) { }
    }
}
