package com.neighbormarket.web;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.neighbormarket.domain.ItemStatus;
import com.neighbormarket.domain.TradeType;
import com.neighbormarket.service.ItemService;
import com.neighbormarket.web.dto.ItemRequests;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/items")
public class ItemController {
    private final ItemService service;

    public ItemController(ItemService service) {
        this.service = service;
    }

    @GetMapping
    public IPage<ItemService.ItemView> market(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "12") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) TradeType tradeType,
            @RequestParam(required = false) ItemStatus status) {
        if (page < 1 || size < 1 || size > 50) {
            throw new IllegalArgumentException("分页参数不正确");
        }
        return service.market(new Page<>(page, size), keyword, category, tradeType, status);
    }

    @GetMapping("/detail/{id}")
    public ItemService.ItemView detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @PostMapping
    public ItemService.ItemView create(
            @Valid @RequestBody ItemRequests.CreateItem body,
            Authentication authentication) {
        return service.create(body.toCommand(), authentication.getName());
    }

    @PutMapping("/{id}")
    public ItemService.ItemView update(
            @PathVariable Long id,
            @Valid @RequestBody ItemRequests.UpdateItem body,
            Authentication authentication) {
        return service.update(id, body.toCommand(), authentication.getName());
    }

    @PostMapping("/{id}/confirm-available")
    public ItemService.ItemView confirm(@PathVariable Long id, Authentication authentication) {
        return service.confirmAvailable(id, authentication.getName());
    }

    @GetMapping("/mine")
    public IPage<ItemService.ItemView> mine(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "50") long size,
            Authentication authentication) {
        return service.mine(new Page<>(page, Math.min(Math.max(size, 1), 50)), authentication.getName());
    }
}
