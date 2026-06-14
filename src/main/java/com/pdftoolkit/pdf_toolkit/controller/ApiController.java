package com.pdftoolkit.pdf_toolkit.controller;

import com.pdftoolkit.pdf_toolkit.model.FileUploadResponse;
import com.pdftoolkit.pdf_toolkit.service.NativeDocumentService;
import com.pdftoolkit.pdf_toolkit.service.OfficeConversionService;
import com.pdftoolkit.pdf_toolkit.service.PdfConversionService;
import com.pdftoolkit.pdf_toolkit.service.PdfService;
import com.pdftoolkit.pdf_toolkit.service.StorageService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/pdf")
public class ApiController {

    private final StorageService storageService;
    private final PdfService pdfService;
    private final OfficeConversionService officeConversionService;
    private final PdfConversionService pdfConversionService;
    private final NativeDocumentService nativeDocumentService;

    public ApiController(StorageService storageService,
                         PdfService pdfService,
                         OfficeConversionService officeConversionService,
                         PdfConversionService pdfConversionService,
                         NativeDocumentService nativeDocumentService) {
        this.storageService = storageService;
        this.pdfService = pdfService;
        this.officeConversionService = officeConversionService;
        this.pdfConversionService = pdfConversionService;
        this.nativeDocumentService = nativeDocumentService;
    }

    // =========================================================================
    // MERGE (Universal: PDF, DOCX, XLSX, HTML, TXT)
    // =========================================================================
    @PostMapping("/merge")
    public ResponseEntity<FileUploadResponse> mergeDocuments(
            @RequestParam("files") List<MultipartFile> files) {

        if (files == null || files.isEmpty()) {
            return badRequest("No files uploaded");
        }

        List<Path> tempFiles = new ArrayList<>();
        try {
            for (MultipartFile file : files) {
                tempFiles.add(storageService.store(file));
            }
            // Determine output extension from the first uploaded file
            String originalName = files.get(0).getOriginalFilename();
            String ext = (originalName != null && originalName.contains("."))
                    ? originalName.substring(originalName.lastIndexOf('.')).toLowerCase()
                    : ".pdf";
            String outName = UUID.randomUUID() + "_merged" + ext;
            Path outPath = storageService.getRootLocation().resolve(outName).normalize().toAbsolutePath();
            nativeDocumentService.merge(tempFiles, outPath);
            return ok("Files merged successfully", outName);
        } catch (Exception e) {
            return serverError("Error merging documents: " + e.getMessage());
        } finally {
            deletePaths(tempFiles);
        }
    }

    // =========================================================================
    // SPLIT
    // =========================================================================
    @PostMapping("/split")
    public ResponseEntity<FileUploadResponse> splitDocument(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "splitMode", defaultValue = "interval") String splitMode,
            @RequestParam(value = "splitParam", required = false) Integer splitParam) {

        if (files == null || files.isEmpty()) {
            return badRequest("No files uploaded");
        }

        Path tempFile = null;
        List<Path> splitFiles = new ArrayList<>();
        try {
            tempFile = storageService.store(files.get(0));
            splitFiles = nativeDocumentService.splitDocument(tempFile, storageService.getRootLocation(), splitMode, splitParam);

            if (splitFiles.isEmpty()) {
                return badRequest("No documents generated from split");
            }

            if (splitFiles.size() == 1) {
                String fn = splitFiles.get(0).getFileName().toString();
                return ok("Document split successfully", fn);
            } else {
                Path zipFile = storageService.zipFiles(splitFiles, "split_documents.zip");
                return ok("Document split successfully", zipFile.getFileName().toString());
            }
        } catch (UnsupportedOperationException e) {
            return badRequest(e.getMessage());
        } catch (Exception e) {
            return serverError("Error splitting document: " + e.getMessage());
        } finally {
            deletePath(tempFile);
            if (splitFiles.size() > 1) deletePaths(splitFiles);
        }
    }

    // =========================================================================
    // EXTRACT PAGES
    // =========================================================================
    @PostMapping("/extract")
    public ResponseEntity<FileUploadResponse> extractPages(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "pagesToExtract", defaultValue = "") String pagesToExtract) {

        if (files == null || files.isEmpty()) {
            return badRequest("No files uploaded");
        }

        Path tempFile = null;
        try {
            tempFile = storageService.store(files.get(0));
            String originalName = files.get(0).getOriginalFilename();
            String ext = (originalName != null && originalName.contains("."))
                    ? originalName.substring(originalName.lastIndexOf('.')).toLowerCase()
                    : ".pdf";
            String finalExt = (ext.equals(".xlsx") || ext.equals(".xls")) ? ext : ".pdf";
            String outName = UUID.randomUUID() + "_extracted" + finalExt;
            Path outPath = storageService.getRootLocation().resolve(outName).normalize().toAbsolutePath();
            
            nativeDocumentService.extractPages(tempFile, outPath, pagesToExtract);
            return ok("Pages extracted successfully", outName);
        } catch (IllegalArgumentException | UnsupportedOperationException e) {
            return badRequest(e.getMessage());
        } catch (Exception e) {
            return serverError("Error extracting pages: " + e.getMessage());
        } finally {
            deletePath(tempFile);
        }
    }

    // =========================================================================
    // REORDER
    // =========================================================================
    @PostMapping("/reorder")
    public ResponseEntity<FileUploadResponse> reorderPdf(
            @RequestParam("files") List<MultipartFile> files) {

        if (files == null || files.isEmpty()) {
            return badRequest("No files uploaded");
        }

        Path tempFile = null;
        try {
            tempFile = storageService.store(files.get(0));
            String outName = UUID.randomUUID() + "_reordered.pdf";
            Path outPath = storageService.getRootLocation().resolve(outName).normalize().toAbsolutePath();
            nativeDocumentService.reorderPages(tempFile, outPath);
            return ok("PDF pages reordered successfully", outName);
        } catch (Exception e) {
            return serverError("Error reordering PDF pages: " + e.getMessage());
        } finally {
            deletePath(tempFile);
        }
    }

    // =========================================================================
    // DELETE PAGES
    // =========================================================================
    @PostMapping("/delete-pages")
    public ResponseEntity<FileUploadResponse> deletePages(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "pagesToDelete", defaultValue = "") String pagesToDelete) {

        if (files == null || files.isEmpty()) {
            return badRequest("No files uploaded");
        }

        Path tempFile = null;
        try {
            tempFile = storageService.store(files.get(0));
            String outName = UUID.randomUUID() + "_deleted.pdf";
            Path outPath = storageService.getRootLocation().resolve(outName).normalize().toAbsolutePath();
            nativeDocumentService.deletePages(tempFile, outPath, pagesToDelete);
            return ok("Pages deleted successfully", outName);
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        } catch (Exception e) {
            return serverError("Error deleting pages: " + e.getMessage());
        } finally {
            deletePath(tempFile);
        }
    }

    // =========================================================================
    // ROTATE
    // =========================================================================
    @PostMapping("/rotate")
    public ResponseEntity<FileUploadResponse> rotatePdf(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "degrees", defaultValue = "90") int degrees) {

        if (files == null || files.isEmpty()) {
            return badRequest("No files uploaded");
        }

        Path tempFile = null;
        try {
            tempFile = storageService.store(files.get(0));
            String outName = UUID.randomUUID() + "_rotated.pdf";
            Path outPath = storageService.getRootLocation().resolve(outName).normalize().toAbsolutePath();
            nativeDocumentService.rotate(tempFile, outPath, degrees);
            return ok("PDF rotated successfully", outName);
        } catch (Exception e) {
            return serverError("Error rotating PDF: " + e.getMessage());
        } finally {
            deletePath(tempFile);
        }
    }

    // =========================================================================
    // COMPRESS
    // =========================================================================
    @PostMapping("/compress")
    public ResponseEntity<FileUploadResponse> compressPdf(
            @RequestParam("files") List<MultipartFile> files) {

        if (files == null || files.isEmpty()) {
            return badRequest("No files uploaded");
        }

        Path tempFile = null;
        try {
            tempFile = storageService.store(files.get(0));
            String outName = UUID.randomUUID() + "_compressed.pdf";
            Path outPath = storageService.getRootLocation().resolve(outName).normalize().toAbsolutePath();
            nativeDocumentService.compress(tempFile, outPath);
            return ok("PDF compressed successfully", outName);
        } catch (Exception e) {
            return serverError("Error compressing PDF: " + e.getMessage());
        } finally {
            deletePath(tempFile);
        }
    }

    // =========================================================================
    // PROTECT (Universal: PDF, DOCX, XLSX)
    // =========================================================================
    @PostMapping("/protect")
    public ResponseEntity<FileUploadResponse> protectDocument(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "password", defaultValue = "password123") String password) {

        if (files == null || files.isEmpty()) {
            return badRequest("No files uploaded");
        }

        Path tempFile = null;
        try {
            tempFile = storageService.store(files.get(0));
            String originalName = files.get(0).getOriginalFilename();
            String ext = (originalName != null && originalName.contains("."))
                    ? originalName.substring(originalName.lastIndexOf('.')).toLowerCase()
                    : ".pdf";
            String outName = UUID.randomUUID() + "_protected" + ext;
            Path outPath = storageService.getRootLocation().resolve(outName).normalize().toAbsolutePath();
            nativeDocumentService.protect(tempFile, outPath, password);
            return ok("Document password protected successfully", outName);
        } catch (Exception e) {
            return serverError("Error protecting document: " + e.getMessage());
        } finally {
            deletePath(tempFile);
        }
    }

    // =========================================================================
    // WATERMARK (Universal: PDF, DOCX, XLSX, HTML, TXT)
    // =========================================================================
    @PostMapping("/watermark")
    public ResponseEntity<FileUploadResponse> watermarkDocument(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "text", defaultValue = "CONFIDENTIAL") String text) {

        if (files == null || files.isEmpty()) {
            return badRequest("No files uploaded");
        }

        Path tempFile = null;
        try {
            tempFile = storageService.store(files.get(0));
            String originalName = files.get(0).getOriginalFilename();
            String ext = (originalName != null && originalName.contains("."))
                    ? originalName.substring(originalName.lastIndexOf('.')).toLowerCase()
                    : ".pdf";
            String outName = UUID.randomUUID() + "_watermarked" + ext;
            Path outPath = storageService.getRootLocation().resolve(outName).normalize().toAbsolutePath();
            nativeDocumentService.addWatermark(tempFile, outPath, text);
            return ok("Watermark added successfully", outName);
        } catch (Exception e) {
            return serverError("Error adding watermark: " + e.getMessage());
        } finally {
            deletePath(tempFile);
        }
    }

    // =========================================================================
    // REMOVE WATERMARK
    // =========================================================================
    @PostMapping("/remove-watermark")
    public ResponseEntity<FileUploadResponse> removeWatermarkPdf(
            @RequestParam("files") List<MultipartFile> files) {

        if (files == null || files.isEmpty()) {
            return badRequest("No files uploaded");
        }

        Path tempFile = null;
        try {
            tempFile = storageService.store(files.get(0));
            String originalName = files.get(0).getOriginalFilename();
            String ext = (originalName != null && originalName.contains("."))
                    ? originalName.substring(originalName.lastIndexOf('.')).toLowerCase()
                    : ".pdf";
            String outName = UUID.randomUUID() + "_clean" + ext;
            Path outPath = storageService.getRootLocation().resolve(outName).normalize().toAbsolutePath();
            nativeDocumentService.removeWatermark(tempFile, outPath);
            return ok("Watermark removed successfully", outName);
        } catch (Exception e) {
            return serverError("Error removing watermark: " + e.getMessage());
        } finally {
            deletePath(tempFile);
        }
    }



    // =========================================================================
    // PAGE NUMBERS
    // =========================================================================
    @PostMapping("/page-numbers")
    public ResponseEntity<FileUploadResponse> addPageNumbers(
            @RequestParam("files") List<MultipartFile> files) {

        if (files == null || files.isEmpty()) {
            return badRequest("No files uploaded");
        }

        Path tempFile = null;
        try {
            tempFile = storageService.store(files.get(0));
            String outName = UUID.randomUUID() + "_numbered.pdf";
            Path outPath = storageService.getRootLocation().resolve(outName).normalize().toAbsolutePath();
            nativeDocumentService.addPageNumbers(tempFile, outPath);
            return ok("Page numbers added successfully", outName);
        } catch (Exception e) {
            return serverError("Error adding page numbers: " + e.getMessage());
        } finally {
            deletePath(tempFile);
        }
    }

    // =========================================================================
    // JPG / PNG → PDF
    // =========================================================================
    @PostMapping("/jpg-to-pdf")
    public ResponseEntity<FileUploadResponse> jpgToPdf(
            @RequestParam("files") List<MultipartFile> files) {

        if (files == null || files.isEmpty()) {
            return badRequest("No files uploaded");
        }

        List<Path> tempFiles = new ArrayList<>();
        try {
            for (MultipartFile file : files) {
                tempFiles.add(storageService.store(file));
            }
            String outName = UUID.randomUUID() + "_images.pdf";
            Path outPath = storageService.getRootLocation().resolve(outName).normalize().toAbsolutePath();
            pdfService.imagesToPdf(tempFiles, outPath);
            return ok("Images converted to PDF successfully", outName);
        } catch (Exception e) {
            return serverError("Error converting images to PDF: " + e.getMessage());
        } finally {
            deletePaths(tempFiles);
        }
    }

    // =========================================================================
    // PDF → JPG
    // =========================================================================
    @PostMapping("/pdf-to-jpg")
    public ResponseEntity<FileUploadResponse> pdfToJpg(
            @RequestParam("files") List<MultipartFile> files) {

        if (files == null || files.isEmpty()) {
            return badRequest("No files uploaded");
        }

        Path tempFile = null;
        List<Path> imageFiles = new ArrayList<>();
        try {
            tempFile = storageService.store(files.get(0));
            imageFiles = pdfService.pdfToImages(tempFile, storageService.getRootLocation());

            if (imageFiles.isEmpty()) {
                return badRequest("No pages found in PDF");
            }
            if (imageFiles.size() == 1) {
                String fn = imageFiles.get(0).getFileName().toString();
                return ok("PDF converted to JPG successfully", fn);
            } else {
                Path zipFile = storageService.zipFiles(imageFiles, "pdf_pages.zip");
                return ok("PDF converted to JPG successfully", zipFile.getFileName().toString());
            }
        } catch (Exception e) {
            return serverError("Error converting PDF to JPG: " + e.getMessage());
        } finally {
            deletePath(tempFile);
            if (imageFiles.size() > 1) deletePaths(imageFiles);
        }
    }

    // =========================================================================
    // WORD → PDF  (LibreOffice)
    // =========================================================================
    @PostMapping("/word-to-pdf")
    public ResponseEntity<FileUploadResponse> wordToPdf(
            @RequestParam("files") List<MultipartFile> files) {
        return officeConvert(files, "word-to-pdf");
    }


    // =========================================================================
    // POWERPOINT → PDF  (LibreOffice)
    // =========================================================================
    @PostMapping("/powerpoint-to-pdf")
    public ResponseEntity<FileUploadResponse> powerpointToPdf(
            @RequestParam("files") List<MultipartFile> files) {
        return officeConvert(files, "powerpoint-to-pdf");
    }

    // =========================================================================
    // HTML → PDF  (LibreOffice)
    // =========================================================================
    @PostMapping("/html-to-pdf")
    public ResponseEntity<FileUploadResponse> htmlToPdf(
            @RequestParam("files") List<MultipartFile> files) {
        return officeConvert(files, "html-to-pdf");
    }

    // =========================================================================
    // PDF TO ANY FORMAT (Text, HTML, Word, Excel)
    // =========================================================================
    
    @PostMapping("/pdf-to-text")
    public ResponseEntity<FileUploadResponse> pdfToText(
            @RequestParam("files") List<MultipartFile> files) {
        return handlePdfConversion(files, ".txt", "text");
    }

    @PostMapping("/pdf-to-html")
    public ResponseEntity<FileUploadResponse> pdfToHtml(
            @RequestParam("files") List<MultipartFile> files) {
        return handlePdfConversion(files, ".html", "HTML");
    }

    @PostMapping("/pdf-to-word")
    public ResponseEntity<FileUploadResponse> pdfToWord(
            @RequestParam("files") List<MultipartFile> files) {
        return handlePdfConversion(files, ".docx", "Word");
    }

    @PostMapping("/pdf-to-excel")
    public ResponseEntity<FileUploadResponse> pdfToExcel(
            @RequestParam("files") List<MultipartFile> files) {
        return handlePdfConversion(files, ".xlsx", "Excel");
    }

    @PostMapping("/pdf-to-powerpoint")
    public ResponseEntity<FileUploadResponse> pdfToPowerPoint(
            @RequestParam("files") List<MultipartFile> files) {
        return handlePdfConversion(files, ".pptx", "PowerPoint");
    }

    @PostMapping("/text-to-pdf")
    public ResponseEntity<FileUploadResponse> textToPdf(
            @RequestParam("files") List<MultipartFile> files) {
        return officeConvert(files, "text-to-pdf");
    }

    private ResponseEntity<FileUploadResponse> handlePdfConversion(List<MultipartFile> files, String ext, String formatName) {
        if (files == null || files.isEmpty()) {
            return badRequest("No files uploaded");
        }
        Path tempFile = null;
        try {
            tempFile = storageService.store(files.get(0));
            String outName = UUID.randomUUID() + "_converted" + ext;
            Path outPath = storageService.getRootLocation().resolve(outName).normalize().toAbsolutePath();
            
            if (ext.equals(".txt")) {
                pdfConversionService.pdfToText(tempFile, outPath);
            } else if (ext.equals(".html")) {
                pdfConversionService.pdfToHtml(tempFile, outPath);
            } else if (ext.equals(".docx")) {
                pdfConversionService.pdfToWord(tempFile, outPath);
            } else if (ext.equals(".xlsx")) {
                pdfConversionService.pdfToExcel(tempFile, outPath);
            } else if (ext.equals(".pptx")) {
                pdfConversionService.pdfToPowerPoint(tempFile, outPath);
            }

            return ok("PDF converted to " + formatName + " successfully", outName);
        } catch (Exception e) {
            return serverError("Error converting PDF to " + formatName + ": " + e.getMessage());
        } finally {
            deletePath(tempFile);
        }
    }

    // =========================================================================
    // DOWNLOAD
    // =========================================================================
    @GetMapping("/download/{filename}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String filename) {
        try {
            Path filePath = storageService.getRootLocation()
                    .resolve(filename).normalize().toAbsolutePath();

            // Path traversal guard
            if (!filePath.startsWith(storageService.getRootLocation().toAbsolutePath())) {
                return ResponseEntity.status(403).build();
            }

            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }

            MediaType mediaType;
            String lc = filename.toLowerCase();
            if (lc.endsWith(".zip"))       mediaType = MediaType.APPLICATION_OCTET_STREAM;
            else if (lc.endsWith(".jpg") || lc.endsWith(".jpeg")) mediaType = MediaType.IMAGE_JPEG;
            else if (lc.endsWith(".png"))  mediaType = MediaType.IMAGE_PNG;
            else if (lc.endsWith(".txt"))  mediaType = MediaType.TEXT_PLAIN;
            else if (lc.endsWith(".html")) mediaType = MediaType.TEXT_HTML;
            else if (lc.endsWith(".docx")) mediaType = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            else if (lc.endsWith(".xlsx")) mediaType = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            else                           mediaType = MediaType.APPLICATION_PDF;

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + filename + "\"")
                    .contentType(mediaType)
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // =========================================================================
    // Shared Office conversion helper
    // =========================================================================
    private ResponseEntity<FileUploadResponse> officeConvert(
            List<MultipartFile> files, String toolName) {

        if (files == null || files.isEmpty()) {
            return badRequest("No files uploaded");
        }

        Path tempFile = null;
        try {
            tempFile = storageService.store(files.get(0));
            Path pdfResult = officeConversionService.convertToPdf(
                    tempFile, storageService.getRootLocation());
            return ok("Document converted to PDF successfully",
                    pdfResult.getFileName().toString());
        } catch (Exception e) {
            return serverError("Error during " + toolName + " conversion: " + e.getMessage());
        } finally {
            deletePath(tempFile);
        }
    }

    // =========================================================================
    // Response helpers
    // =========================================================================
    private ResponseEntity<FileUploadResponse> ok(String message, String filename) {
        return ResponseEntity.ok(
                new FileUploadResponse(true, message, "/api/pdf/download/" + filename));
    }

    private ResponseEntity<FileUploadResponse> badRequest(String message) {
        return ResponseEntity.badRequest()
                .body(new FileUploadResponse(false, message, null));
    }

    private ResponseEntity<FileUploadResponse> serverError(String message) {
        return ResponseEntity.internalServerError()
                .body(new FileUploadResponse(false, message, null));
    }

    private void deletePath(Path path) {
        if (path != null) {
            try { Files.deleteIfExists(path); } catch (IOException ignored) {}
        }
    }

    private void deletePaths(List<Path> paths) {
        for (Path p : paths) deletePath(p);
    }
}
