package com.neighbormarket.service;

import com.neighbormarket.domain.HandoverStatus;
import com.neighbormarket.domain.InterestStatus;
import com.neighbormarket.domain.Item;
import com.neighbormarket.mapper.AppUserMapper;
import com.neighbormarket.mapper.HandoverMapper;
import com.neighbormarket.mapper.ItemCommentMapper;
import com.neighbormarket.mapper.ItemInterestMapper;
import com.neighbormarket.mapper.ItemMapper;
import com.neighbormarket.mapper.PickupSlotMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Backward-compatible facade for the exchange module.
 *
 * <p>The public API and response records remain centralized here so controllers and callers do
 * not need to change. Business rules and transaction boundaries live in focused domain services.
 */
@Service
public class ExchangeService {
    private final PickupOptionService pickupOptions;
    private final InterestWorkflowService interests;
    private final HandoverWorkflowService handovers;
    private final CommentWorkflowService comments;
    private final PersonalExchangeService personalExchange;

    @Autowired
    public ExchangeService(
            PickupOptionService pickupOptions,
            InterestWorkflowService interests,
            HandoverWorkflowService handovers,
            CommentWorkflowService comments,
            PersonalExchangeService personalExchange) {
        this.pickupOptions = pickupOptions;
        this.interests = interests;
        this.handovers = handovers;
        this.comments = comments;
        this.personalExchange = personalExchange;
    }

    /**
     * Compatibility constructor retained for isolated unit tests and existing direct callers.
     * Spring uses the explicitly autowired domain-service constructor above.
     */
    public ExchangeService(
            ItemMapper itemMapper,
            AppUserMapper userMapper,
            PickupSlotMapper slotMapper,
            ItemInterestMapper interestMapper,
            HandoverMapper handoverMapper,
            ItemCommentMapper commentMapper) {
        this(legacyServices(itemMapper, userMapper, slotMapper, interestMapper, handoverMapper, commentMapper));
    }

    private ExchangeService(LegacyServices services) {
        this(services.pickupOptions(), services.interests(), services.handovers(),
                services.comments(), services.personalExchange());
    }

    private static LegacyServices legacyServices(
            ItemMapper itemMapper,
            AppUserMapper userMapper,
            PickupSlotMapper slotMapper,
            ItemInterestMapper interestMapper,
            HandoverMapper handoverMapper,
            ItemCommentMapper commentMapper) {
        ExchangeSupport support = new ExchangeSupport(itemMapper, userMapper);
        HandoverWorkflowService handovers =
                new HandoverWorkflowService(support, slotMapper, interestMapper, handoverMapper);
        return new LegacyServices(
                new PickupOptionService(support, slotMapper, interestMapper),
                new InterestWorkflowService(support, slotMapper, interestMapper),
                handovers,
                new CommentWorkflowService(support, commentMapper),
                new PersonalExchangeService(support, interestMapper, handovers));
    }

    public List<SlotView> validSlots(Long itemId) {
        return pickupOptions.validSlots(itemId);
    }

    public List<SlotView> configure(Long itemId, String location, List<SlotInput> input, String username) {
        return pickupOptions.configure(itemId, location, input, username);
    }

    public void addInitialSlots(Item item, List<SlotInput> input) {
        pickupOptions.addInitialSlots(item, input);
    }

    public InterestView want(Long itemId, Long slotId, String username) {
        return interests.want(itemId, slotId, username);
    }

    public InterestView cancelInterest(Long itemId, String username) {
        return interests.cancel(itemId, username);
    }

    public List<InterestView> itemInterests(Long itemId, String username) {
        return interests.itemInterests(itemId, username);
    }

    public InterestView myInterest(Long itemId, String username) {
        return interests.mine(itemId, username);
    }

    public HandoverView confirm(Long interestId, String username) {
        return handovers.confirm(interestId, username);
    }

    public List<HandoverView> myHandovers(String username) {
        return handovers.mine(username);
    }

    public HandoverView handover(Long id, String username) {
        return handovers.get(id, username);
    }

    public HandoverView cancelHandover(Long id, String username) {
        return handovers.cancel(id, username);
    }

    public HandoverView complete(Long id, String username) {
        return handovers.complete(id, username);
    }

    public List<CommentView> comments(Long itemId) {
        return comments.comments(itemId);
    }

    public CommentView comment(Long itemId, String content, String username) {
        return comments.comment(itemId, content, username);
    }

    public CommentView reply(Long itemId, Long parentId, String content, String username) {
        return comments.reply(itemId, parentId, content, username);
    }

    public Dashboard dashboard(String username) {
        return personalExchange.dashboard(username);
    }

    private record LegacyServices(
            PickupOptionService pickupOptions,
            InterestWorkflowService interests,
            HandoverWorkflowService handovers,
            CommentWorkflowService comments,
            PersonalExchangeService personalExchange) {
    }

    public record SlotInput(LocalDateTime startAt, LocalDateTime endAt) {
    }

    public record SlotView(Long id, Long itemId, LocalDateTime startAt, LocalDateTime endAt) {
    }

    public record InterestView(
            Long id, Long itemId, String itemTitle, Long userId, String nickname, Long slotId,
            LocalDateTime startAt, LocalDateTime endAt, InterestStatus status, LocalDateTime updatedAt) {
    }

    public record HandoverView(
            Long id, Long itemId, String itemTitle, Long publisherId, String publisherName,
            Long recipientId, String recipientName, LocalDateTime startAt, LocalDateTime endAt,
            String location, HandoverStatus status, LocalDateTime createdAt,
            LocalDateTime cancelledAt, LocalDateTime completedAt) {
    }

    public record CommentView(
            Long id, Long itemId, Long userId, String nickname, Long parentId,
            String content, LocalDateTime createdAt) {
    }

    public record Dashboard(List<InterestView> interests, List<HandoverView> handovers) {
    }
}
