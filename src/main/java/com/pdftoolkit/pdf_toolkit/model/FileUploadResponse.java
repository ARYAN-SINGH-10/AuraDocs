package com.pdftoolkit.pdf_toolkit.model;

public class FileUploadResponse {
    private boolean success;
    private String message;
    private String downloadUrl;

    public FileUploadResponse() {}

    public FileUploadResponse(boolean success, String message, String downloadUrl) {
        this.success = success;
        this.message = message;
        this.downloadUrl = downloadUrl;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }
}
