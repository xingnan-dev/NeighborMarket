package com.neighbormarket.web;

import com.neighbormarket.image.ImageStorage;
import com.neighbormarket.service.ItemImageService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.TimeUnit;

@RestController
public class ItemImageController {
    private final ItemImageService service;
    private final ImageStorage storage;

    public ItemImageController(ItemImageService service, ImageStorage storage) {
        this.service = service;
        this.storage = storage;
    }

    @PutMapping(path = "/api/items/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ItemImageService.ImageView replace(@PathVariable Long id,
                                              @RequestParam Long expectedVersion,
                                              @RequestPart("file") MultipartFile file,
                                              Authentication authentication) {
        return service.replace(id, expectedVersion, file, authentication.getName());
    }

    @DeleteMapping("/api/items/{id}/image")
    public ItemImageService.ImageView remove(@PathVariable Long id,
                                             @RequestParam Long expectedVersion,
                                             Authentication authentication) {
        return service.remove(id, expectedVersion, authentication.getName());
    }

    @GetMapping("/media/items/{shard}/{filename:.+}")
    public ResponseEntity<?> read(@PathVariable String shard, @PathVariable String filename) {
        return storage.open("/media/items/" + shard + "/" + filename)
                .<ResponseEntity<?>>map(stored -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(stored.mediaType()))
                        .contentLength(stored.size())
                        .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                        .header("X-Content-Type-Options", "nosniff")
                        .body(stored.resource()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
