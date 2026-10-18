package com.neighbormarket.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.neighbormarket.domain.AppUser;
import com.neighbormarket.domain.Item;
import com.neighbormarket.domain.ItemStatus;
import com.neighbormarket.image.ImageNormalizer;
import com.neighbormarket.image.ImageStorage;
import com.neighbormarket.image.StoredImage;
import com.neighbormarket.mapper.AppUserMapper;
import com.neighbormarket.mapper.ItemMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ItemImageService {
    private static final Logger log = LoggerFactory.getLogger(ItemImageService.class);
    private final ItemMapper items;
    private final AppUserMapper users;
    private final PickupOptionService pickupOptions;
    private final ImageNormalizer normalizer;
    private final ImageStorage storage;

    public ItemImageService(ItemMapper items, AppUserMapper users, PickupOptionService pickupOptions,
                            ImageNormalizer normalizer, ImageStorage storage) {
        this.items = items;
        this.users = users;
        this.pickupOptions = pickupOptions;
        this.normalizer = normalizer;
        this.storage = storage;
    }

    @Transactional
    public ImageView replace(Long itemId, Long expectedVersion, MultipartFile file, String username) {
        requireVersion(expectedVersion);
        AppUser user = user(username);
        authorizeAndValidate(items.selectById(itemId), user, expectedVersion);

        StoredImage stored;
        try (ImageNormalizer.NormalizedImage image = normalizer.normalize(file)) {
            stored = storage.store(image.path(), image.extension(), image.mediaType());
        }

        try {
            Item locked = items.selectByIdForUpdate(itemId);
            authorizeAndValidate(locked, user, expectedVersion);
            if (pickupOptions.hasWaitingInterest(itemId)) conflict("已有等待意向，不能修改图片");
            if (items.updateImageUrl(itemId, user.getId(), stored.publicPath(), expectedVersion) != 1)
                conflict("物品已被修改，请刷新后重试");
            registerCleanup(stored.publicPath(), locked.getImageUrl());
            return new ImageView(stored.publicPath(), expectedVersion + 1);
        } catch (RuntimeException ex) {
            safeDelete(stored.publicPath());
            throw ex;
        }
    }

    @Transactional
    public ImageView remove(Long itemId, Long expectedVersion, String username) {
        requireVersion(expectedVersion);
        AppUser user = user(username);
        Item locked = items.selectByIdForUpdate(itemId);
        authorizeAndValidate(locked, user, expectedVersion);
        if (pickupOptions.hasWaitingInterest(itemId)) conflict("已有等待意向，不能删除图片");
        if (items.updateImageUrl(itemId, user.getId(), ItemService.PLACEHOLDER_IMAGE, expectedVersion) != 1)
            conflict("物品已被修改，请刷新后重试");
        registerOldImageCleanup(locked.getImageUrl());
        return new ImageView(ItemService.PLACEHOLDER_IMAGE, expectedVersion + 1);
    }

    private AppUser user(String username) {
        AppUser user = users.selectOne(Wrappers.lambdaQuery(AppUser.class).eq(AppUser::getUsername, username));
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户不存在");
        return user;
    }

    private void authorize(Item item, AppUser user) {
        if (item == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "物品不存在");
        if (!item.getPublisherId().equals(user.getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有发布者可以修改图片");
        if (item.getStatus() != ItemStatus.AVAILABLE) conflict("待交接或已送出物品不能修改图片");
    }

    private void authorizeAndValidate(Item item, AppUser user, Long expectedVersion) {
        authorize(item, user);
        if (!expectedVersion.equals(item.getVersion())) conflict("物品已被修改，请刷新后重试");
    }

    private void registerCleanup(String newPath, String oldPath) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) safeDelete(oldPath);
                else safeDelete(newPath);
            }
        });
    }

    private void registerOldImageCleanup(String oldPath) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { safeDelete(oldPath); }
        });
    }

    private void safeDelete(String path) {
        if (!storage.isManaged(path)) return;
        try { storage.delete(path); }
        catch (RuntimeException ex) { log.warn("Failed to clean managed item image; path={}", path); }
    }

    private void requireVersion(Long version) {
        if (version == null || version < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "expectedVersion不正确");
    }

    private void conflict(String message) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    public record ImageView(String imageUrl, Long version) {}
}
