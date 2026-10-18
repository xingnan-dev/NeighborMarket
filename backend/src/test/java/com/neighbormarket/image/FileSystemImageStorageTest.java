package com.neighbormarket.image;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileSystemImageStorageTest {
    @TempDir Path directory;

    @Test
    void storesOpensAndDeletesOnlyManagedPaths() throws Exception {
        FileSystemImageStorage storage = new FileSystemImageStorage(directory.toString());
        Path source = Files.createTempFile(directory, "source-", ".png");
        Files.write(source, new byte[]{1, 2, 3});

        StoredImage stored = storage.store(source, "png", "image/png");

        assertTrue(storage.isManaged(stored.publicPath()));
        assertTrue(storage.open(stored.publicPath()).isPresent());
        assertFalse(storage.isManaged("/media/items/aa/../../secret.png"));
        assertTrue(storage.open("/media/items/aa/../../secret.png").isEmpty());
        storage.delete(stored.publicPath());
        assertTrue(storage.open(stored.publicPath()).isEmpty());
    }
}
