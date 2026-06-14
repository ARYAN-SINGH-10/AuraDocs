package com.pdftoolkit.pdf_toolkit.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;

@Service
public class StorageService {

    private final Path rootLocation = Paths.get("temp-uploads");

    public StorageService() {
        try {
            Files.createDirectories(rootLocation);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize storage folder", e);
        }
    }

    public Path store(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Failed to store empty file.");
        }
        
        String filename = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
        Path destinationFile = this.rootLocation.resolve(Paths.get(filename))
                .normalize().toAbsolutePath();
        
        if (!destinationFile.getParent().equals(this.rootLocation.toAbsolutePath())) {
            throw new SecurityException("Cannot store file outside current directory.");
        }
        
        Files.copy(file.getInputStream(), destinationFile);
        return destinationFile;
    }

    public Path getRootLocation() {
        return this.rootLocation;
    }

    public Path zipFiles(List<Path> files, String zipFilename) throws IOException {
        Path zipFile = this.rootLocation.resolve(UUID.randomUUID().toString() + "_" + zipFilename)
                .normalize().toAbsolutePath();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(Files.newOutputStream(zipFile))) {
            for (Path file : files) {
                java.util.zip.ZipEntry zipEntry = new java.util.zip.ZipEntry(file.getFileName().toString());
                zos.putNextEntry(zipEntry);
                Files.copy(file, zos);
                zos.closeEntry();
            }
        }
        return zipFile;
    }

    // Schedule cleanup to run every 10 minutes
    @Scheduled(fixedRate = 600000)
    public void cleanupTempFiles() {
        try {
            Instant threshold = Instant.now().minus(15, ChronoUnit.MINUTES);
            Files.list(rootLocation).forEach(path -> {
                try {
                    Instant lastModified = Files.getLastModifiedTime(path).toInstant();
                    if (lastModified.isBefore(threshold)) {
                        Files.delete(path);
                    }
                } catch (IOException e) {
                    // Log failure to delete
                }
            });
        } catch (IOException e) {
            // Log failure to list
        }
    }
}
