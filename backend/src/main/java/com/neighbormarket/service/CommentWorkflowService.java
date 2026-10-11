package com.neighbormarket.service;

import com.neighbormarket.domain.AppUser;
import com.neighbormarket.domain.Item;
import com.neighbormarket.domain.ItemComment;
import com.neighbormarket.domain.ItemStatus;
import com.neighbormarket.mapper.ItemCommentMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
class CommentWorkflowService {
    private final ExchangeSupport support;
    private final ItemCommentMapper comments;

    CommentWorkflowService(ExchangeSupport support, ItemCommentMapper comments) {
        this.support = support;
        this.comments = comments;
    }

    List<ExchangeService.CommentView> comments(Long itemId) {
        support.item(itemId);
        return comments.selectViewsByItem(itemId).stream().map(support::commentView).toList();
    }

    @Transactional
    ExchangeService.CommentView comment(Long itemId, String content, String username) {
        AppUser user = support.user(username);
        Item item = support.item(itemId);
        ensureWritable(item);
        ItemComment comment = new ItemComment();
        comment.setItemId(itemId);
        comment.setUserId(user.getId());
        comment.setParentId(null);
        comment.setContent(validatedContent(content));
        comment.setCreatedAt(LocalDateTime.now());
        comments.insert(comment);
        return support.commentView(comment);
    }

    @Transactional
    ExchangeService.CommentView reply(Long itemId, Long parentId, String content,
                                      String username) {
        AppUser user = support.user(username);
        Item item = support.item(itemId);
        ensureWritable(item);
        if (!item.getPublisherId().equals(user.getId()))
            throw support.error(HttpStatus.FORBIDDEN, "只有物品发布者可以回复");
        ItemComment parent = comments.selectById(parentId);
        if (parent == null) throw support.error(HttpStatus.NOT_FOUND, "留言不存在");
        if (!parent.getItemId().equals(itemId))
            throw support.error(HttpStatus.BAD_REQUEST, "留言不属于该物品");
        if (parent.getParentId() != null)
            throw support.error(HttpStatus.BAD_REQUEST, "只能回复顶层留言");
        ItemComment reply = new ItemComment();
        reply.setItemId(itemId);
        reply.setUserId(user.getId());
        reply.setParentId(parentId);
        reply.setContent(validatedContent(content));
        reply.setCreatedAt(LocalDateTime.now());
        comments.insert(reply);
        return support.commentView(reply);
    }

    private void ensureWritable(Item item) {
        if (item.getStatus() == ItemStatus.COMPLETED)
            throw support.error(HttpStatus.CONFLICT, "已送出物品的留言只读");
    }

    private String validatedContent(String content) {
        String text = content == null ? "" : content.trim();
        if (text.isEmpty() || text.length() > 500)
            throw support.error(HttpStatus.BAD_REQUEST, "留言须为1至500字");
        return text;
    }
}
