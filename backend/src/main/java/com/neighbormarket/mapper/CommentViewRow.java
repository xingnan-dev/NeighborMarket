package com.neighbormarket.mapper;

import com.neighbormarket.domain.ItemComment;

public class CommentViewRow extends ItemComment {
    private String nickname;

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
}
