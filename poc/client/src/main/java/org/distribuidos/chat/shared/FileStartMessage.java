package org.distribuidos.chat.shared;

public class FileStartMessage {
    private String transferId;
    private String sender;
    private String recipient;
    private String fileName;
    private long fileSize;
    private long lamportClock;

    public FileStartMessage() {}

    public FileStartMessage(String transferId, String sender, String recipient, String fileName, long fileSize, long lamportClock) {
        this.transferId = transferId;
        this.sender = sender;
        this.recipient = recipient;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.lamportClock = lamportClock;
    }

    public String getTransferId() {
        return transferId;
    }

    public void setTransferId(String transferId) {
        this.transferId = transferId;
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

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public long getLamportClock() {
        return lamportClock;
    }

    public void setLamportClock(long lamportClock) {
        this.lamportClock = lamportClock;
    }
}
