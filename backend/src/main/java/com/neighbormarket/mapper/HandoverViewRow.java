package com.neighbormarket.mapper;

import com.neighbormarket.domain.Handover;

public class HandoverViewRow extends Handover {
    private String itemTitle;
    private String publisherName;
    private String recipientName;

    public String getItemTitle() { return itemTitle; }
    public void setItemTitle(String itemTitle) { this.itemTitle = itemTitle; }
    public String getPublisherName() { return publisherName; }
    public void setPublisherName(String publisherName) { this.publisherName = publisherName; }
    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }
}
