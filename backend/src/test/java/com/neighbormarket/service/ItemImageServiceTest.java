package com.neighbormarket.service;

import com.neighbormarket.domain.AppUser;
import com.neighbormarket.domain.Item;
import com.neighbormarket.domain.ItemStatus;
import com.neighbormarket.image.ImageNormalizer;
import com.neighbormarket.image.ImageStorage;
import com.neighbormarket.image.StoredImage;
import com.neighbormarket.mapper.AppUserMapper;
import com.neighbormarket.mapper.ItemMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ItemImageServiceTest {
    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void staleVersionIsRejectedBeforeDecoding() {
        Fixture fixture = fixture(3L);
        assertThrows(ResponseStatusException.class,
                () -> fixture.service.replace(7L, 2L, file(), "owner"));
        verify(fixture.normalizer, never()).normalize(any());
    }

    @Test
    void successfulReplaceUsesConditionalUpdateAndDeletesOldImageAfterCommit() throws Exception {
        Fixture fixture = fixture(2L);
        var temp = Files.createTempFile("normalized-", ".png");
        when(fixture.normalizer.normalize(any())).thenReturn(new ImageNormalizer.NormalizedImage(temp, "png", "image/png"));
        when(fixture.storage.store(temp, "png", "image/png"))
                .thenReturn(new StoredImage("/media/items/ab/ab000000000000000000000000000000.png", "image/png", 3));
        when(fixture.storage.isManaged("/media/items/aa/aa000000000000000000000000000000.png")).thenReturn(true);
        when(fixture.items.updateImageUrl(7L, 1L,
                "/media/items/ab/ab000000000000000000000000000000.png", 2L)).thenReturn(1);
        TransactionSynchronizationManager.initSynchronization();

        ItemImageService.ImageView result = fixture.service.replace(7L, 2L, file(), "owner");

        assertEquals(3L, result.version());
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations())
            synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        verify(fixture.storage).delete("/media/items/aa/aa000000000000000000000000000000.png");
    }

    private static Fixture fixture(long version) {
        ItemMapper items = mock(ItemMapper.class);
        AppUserMapper users = mock(AppUserMapper.class);
        PickupOptionService pickup = mock(PickupOptionService.class);
        ImageNormalizer normalizer = mock(ImageNormalizer.class);
        ImageStorage storage = mock(ImageStorage.class);
        AppUser owner = new AppUser();
        owner.setId(1L);
        owner.setUsername("owner");
        Item item = new Item();
        item.setId(7L);
        item.setPublisherId(1L);
        item.setStatus(ItemStatus.AVAILABLE);
        item.setVersion(version);
        item.setImageUrl("/media/items/aa/aa000000000000000000000000000000.png");
        when(users.selectOne(any())).thenReturn(owner);
        when(items.selectById(7L)).thenReturn(item);
        when(items.selectByIdForUpdate(7L)).thenReturn(item);
        return new Fixture(new ItemImageService(items, users, pickup, normalizer, storage),
                items, normalizer, storage);
    }

    private static MockMultipartFile file() {
        return new MockMultipartFile("file", "item.png", "image/png", new byte[]{1});
    }

    private record Fixture(ItemImageService service, ItemMapper items,
                           ImageNormalizer normalizer, ImageStorage storage) {}
}
