package com.neighbormarket.web;

import com.neighbormarket.service.ExchangeService;
import com.neighbormarket.web.dto.ExchangeRequests;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ExchangeController {
    private final ExchangeService service;

    public ExchangeController(ExchangeService service) {
        this.service = service;
    }

    @GetMapping("/items/{id}/slots")
    public List<ExchangeService.SlotView> slots(@PathVariable Long id) {
        return service.validSlots(id);
    }

    @PostMapping("/items/{id}/pickup-options")
    public List<ExchangeService.SlotView> configure(
            @PathVariable Long id,
            @Valid @RequestBody ExchangeRequests.PickupOptions body,
            Authentication authentication) {
        return service.configure(id, body.location(), body.serviceSlots(), authentication.getName());
    }

    @PostMapping("/items/{id}/interests")
    public ExchangeService.InterestView want(
            @PathVariable Long id,
            @Valid @RequestBody ExchangeRequests.WantItem body,
            Authentication authentication) {
        return service.want(id, body.slotId(), authentication.getName());
    }

    @DeleteMapping("/items/{id}/interests/mine")
    public ExchangeService.InterestView cancelInterest(
            @PathVariable Long id, Authentication authentication) {
        return service.cancelInterest(id, authentication.getName());
    }

    @GetMapping("/items/{id}/interests/mine")
    public ExchangeService.InterestView myInterest(
            @PathVariable Long id, Authentication authentication) {
        return service.myInterest(id, authentication.getName());
    }

    @GetMapping("/items/{id}/interests")
    public List<ExchangeService.InterestView> interests(
            @PathVariable Long id, Authentication authentication) {
        return service.itemInterests(id, authentication.getName());
    }

    @PostMapping("/interests/{id}/confirm")
    public ExchangeService.HandoverView confirm(
            @PathVariable Long id, Authentication authentication) {
        return service.confirm(id, authentication.getName());
    }

    @GetMapping("/handovers/mine")
    public List<ExchangeService.HandoverView> handovers(Authentication authentication) {
        return service.myHandovers(authentication.getName());
    }

    @GetMapping("/handovers/{id}")
    public ExchangeService.HandoverView handover(
            @PathVariable Long id, Authentication authentication) {
        return service.handover(id, authentication.getName());
    }

    @PostMapping("/handovers/{id}/cancel")
    public ExchangeService.HandoverView cancel(
            @PathVariable Long id, Authentication authentication) {
        return service.cancelHandover(id, authentication.getName());
    }

    @PostMapping("/handovers/{id}/complete")
    public ExchangeService.HandoverView complete(
            @PathVariable Long id, Authentication authentication) {
        return service.complete(id, authentication.getName());
    }

    @GetMapping("/items/{id}/comments")
    public List<ExchangeService.CommentView> comments(@PathVariable Long id) {
        return service.comments(id);
    }

    @PostMapping("/items/{id}/comments")
    public ExchangeService.CommentView comment(
            @PathVariable Long id,
            @Valid @RequestBody ExchangeRequests.Comment body,
            Authentication authentication) {
        return service.comment(id, body.content(), authentication.getName());
    }

    @PostMapping("/items/{itemId}/comments/{commentId}/replies")
    public ExchangeService.CommentView reply(
            @PathVariable Long itemId,
            @PathVariable Long commentId,
            @Valid @RequestBody ExchangeRequests.Comment body,
            Authentication authentication) {
        return service.reply(itemId, commentId, body.content(), authentication.getName());
    }

    @GetMapping("/dashboard/mine")
    public ExchangeService.Dashboard dashboard(Authentication authentication) {
        return service.dashboard(authentication.getName());
    }
}
