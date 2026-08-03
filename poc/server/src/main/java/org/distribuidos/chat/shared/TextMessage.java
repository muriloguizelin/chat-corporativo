package org.distribuidos.chat.shared;

public class TextMessage {
    private String sender;
    private String recipient;
    private String content;
    private long lamportClock;
    private long timestamp;

    public TextMessage() {}

    public TextMessage(String sender, String recipient, String content, long lamportClock) {
        this.sender = sender;
        this.recipient = recipient;
        this.content = content;
        this.lamportClock = lamportClock;
        this.timestamp = System.currentTimeMillis();
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public long getLamportClock() {
        return lamportClock;
    }

    public void setLamportClock(long lamportClock) {
        this.lamportClock = lamportClock;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
