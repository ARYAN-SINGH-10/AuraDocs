package com.pdftoolkit.pdf_toolkit.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Converts Office documents (Word, Excel, PowerPoint) to PDF
 * using LibreOffice headless mode.
 */
@Service
public class OfficeConversionService {

    @Value("${libreoffice.path:C:/Program Files/LibreOffice/program/soffice.exe}")
    private String libreOfficePath;

    /**
     * Converts a document file (docx, xlsx, pptx, etc.) to PDF.
     *
     * @param sourceFile  Path to the source document
     * @param outputDir   Directory where the converted PDF will be saved
     * @return            Path to the resulting PDF file
     * @throws IOException if conversion fails
     */
    public Path convertToPdf(Path sourceFile, Path outputDir) throws IOException {
        Path userProfile = Files.createTempDirectory("libreoffice_profile_");

        List<String> command = new ArrayList<>();
        command.add(libreOfficePath);
        command.add("-env:UserInstallation=file:///" + userProfile.toAbsolutePath().toString().replace('\\', '/'));
        command.add("--headless");
        command.add("--norestore");
        command.add("--convert-to");
        command.add("pdf");
        command.add("--outdir");
        command.add(outputDir.toAbsolutePath().toString());
        command.add(sourceFile.toAbsolutePath().toString());

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        // Capture output for debugging
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        boolean finished = false;
        try {
            finished = process.waitFor(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("LibreOffice conversion was interrupted.", e);
        } finally {
            // Clean up temporary profile
            deleteDirectory(userProfile);
        }

        if (!finished || process.exitValue() != 0) {
            throw new IOException(
                    "LibreOffice conversion failed (exit code: " +
                    (finished ? process.exitValue() : "timeout") + "). Output: " + output);
        }

        // LibreOffice renames the file with .pdf extension in the output directory
        String sourceFilename  = sourceFile.getFileName().toString();
        String pdfFilename     = sourceFilename.substring(0, sourceFilename.lastIndexOf('.')) + ".pdf";
        Path   resultPath      = outputDir.resolve(pdfFilename);

        if (!Files.exists(resultPath)) {
            throw new IOException(
                    "LibreOffice ran but output file not found: " + resultPath);
        }
        return resultPath;
    }

    private void deleteDirectory(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.walk(path)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        // Ignore cleanup errors
                    }
                });
        }
    }
}
