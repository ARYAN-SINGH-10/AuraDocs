package com.pdftoolkit.pdf_toolkit.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdfwriter.ContentStreamWriter;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class PdfService {

    /**
     * Merges multiple PDF files into one using PDFMergerUtility (PDFBox 3.x).
     */
    public void merge(List<Path> sources, Path destination) throws IOException {
        PDFMergerUtility merger = new PDFMergerUtility();
        merger.setDestinationFileName(destination.toAbsolutePath().toString());
        for (Path source : sources) {
            merger.addSource(source.toFile());
        }
        merger.mergeDocuments(null);
    }

    /**
     * Splits a PDF into multiple PDFs.
     * Mode "interval": split every `splitParam` pages.
     * Mode "at": split exactly into two parts at `splitParam`.
     * Default: split every 1 page.
     */
    public List<Path> splitDocument(Path source, Path tempDir, String splitMode, Integer splitParam) throws IOException {
        List<Path> splitFiles = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(source.toFile())) {
            int totalPages = document.getNumberOfPages();

            if ("at".equalsIgnoreCase(splitMode) && splitParam != null && splitParam > 0 && splitParam < totalPages) {
                // Split exactly into two PDFs
                Splitter splitter = new Splitter();
                List<PDDocument> allPages = splitter.split(document);
                
                try (PDDocument doc1 = new PDDocument(); PDDocument doc2 = new PDDocument()) {
                    for (int i = 0; i < splitParam; i++) doc1.importPage(allPages.get(i).getPage(0));
                    for (int i = splitParam; i < totalPages; i++) doc2.importPage(allPages.get(i).getPage(0));
                    
                    Path part1 = tempDir.resolve(UUID.randomUUID() + "_part1.pdf");
                    Path part2 = tempDir.resolve(UUID.randomUUID() + "_part2.pdf");
                    doc1.save(part1.toFile());
                    doc2.save(part2.toFile());
                    splitFiles.add(part1);
                    splitFiles.add(part2);
                }
                for (PDDocument p : allPages) { try { p.close(); } catch (IOException ignored) {} }
            } else {
                int interval = ("interval".equalsIgnoreCase(splitMode) && splitParam != null && splitParam > 0) ? splitParam : 1;
                Splitter splitter = new Splitter();
                splitter.setSplitAtPage(interval);
                List<PDDocument> parts = splitter.split(document);
                
                for (int i = 0; i < parts.size(); i++) {
                    Path partFile = tempDir.resolve(UUID.randomUUID() + "_split_" + (i + 1) + ".pdf");
                    parts.get(i).save(partFile.toFile());
                    parts.get(i).close();
                    splitFiles.add(partFile);
                }
            }
        }
        return splitFiles;
    }

    /**
     * Extracts specific pages into a new single PDF.
     */
    public void extractPages(Path source, Path destination, String pagesToExtract) throws IOException {
        try (PDDocument original = Loader.loadPDF(source.toFile());
             PDDocument result = new PDDocument()) {

            int totalPages = original.getNumberOfPages();
            Set<Integer> toExtract = parsePages(pagesToExtract, totalPages);

            if (toExtract.isEmpty()) {
                throw new IllegalArgumentException("No valid pages selected to extract.");
            }

            for (int i = 0; i < totalPages; i++) {
                if (toExtract.contains(i + 1)) {
                    result.importPage(original.getPage(i));
                }
            }

            result.save(destination.toFile());
        }
    }

    /**
     * Rotates all pages in a PDF by the given degrees (90, 180, 270).
     */
    public void rotate(Path source, Path destination, int degrees) throws IOException {
        try (PDDocument document = Loader.loadPDF(source.toFile())) {
            for (PDPage page : document.getPages()) {
                int current = page.getRotation();
                page.setRotation((current + degrees) % 360);
            }
            document.save(destination.toFile());
        }
    }

    /**
     * Compresses a PDF by re-saving it (applies PDFBox's internal optimizations).
     */
    public void compress(Path source, Path destination) throws IOException {
        try (PDDocument document = Loader.loadPDF(source.toFile())) {
            document.save(destination.toFile());
        }
    }

    /**
     * Password-protects a PDF with 128-bit AES encryption.
     */
    public void protect(Path source, Path destination, String userPassword,
                        String ownerPassword) throws IOException {
        try (PDDocument document = Loader.loadPDF(source.toFile())) {
            AccessPermission ap = new AccessPermission();
            StandardProtectionPolicy policy =
                    new StandardProtectionPolicy(ownerPassword, userPassword, ap);
            policy.setEncryptionKeyLength(128);
            document.protect(policy);
            document.save(destination.toFile());
        }
    }

    /**
     * Adds a diagonal text watermark to every page of a PDF.
     */
    public void addWatermark(Path source, Path destination, String watermarkText) throws IOException {
        try (PDDocument document = Loader.loadPDF(source.toFile())) {
            PDType1Font font = new PDType1Font(FontName.HELVETICA_BOLD);

            for (PDPage page : document.getPages()) {
                PDRectangle box = page.getMediaBox();
                float pageWidth  = box.getWidth();
                float pageHeight = box.getHeight();
                float fontSize   = 48;

                try (PDPageContentStream cs = new PDPageContentStream(
                        document, page, AppendMode.APPEND, true, true)) {
                    cs.setFont(font, fontSize);
                    cs.setNonStrokingColor(0.7f, 0.7f, 0.7f); // light gray
                    cs.setGraphicsStateParameters(buildTransparency(document, 0.35f));

                    // Rotate text 45° and center it
                    cs.beginText();
                    cs.setTextMatrix(
                            org.apache.pdfbox.util.Matrix.getRotateInstance(
                                    Math.toRadians(45),
                                    pageWidth / 2 - 80,
                                    pageHeight / 2 - 20));
                    cs.showText(watermarkText);
                    cs.endText();
                }
            }
            document.save(destination.toFile());
        }
    }

    /**
     * Adds page numbers (centered at the bottom) to every page of a PDF.
     */
    public void addPageNumbers(Path source, Path destination) throws IOException {
        try (PDDocument document = Loader.loadPDF(source.toFile())) {
            PDType1Font font = new PDType1Font(FontName.HELVETICA);
            float fontSize   = 10;
            int totalPages   = document.getNumberOfPages();
            int pageIndex    = 0;

            for (PDPage page : document.getPages()) {
                pageIndex++;
                PDRectangle box = page.getMediaBox();
                String text     = "Page " + pageIndex + " of " + totalPages;

                try (PDPageContentStream cs = new PDPageContentStream(
                        document, page, AppendMode.APPEND, true, true)) {
                    cs.setFont(font, fontSize);
                    cs.setNonStrokingColor(0.2f, 0.2f, 0.2f);
                    cs.beginText();
                    float textWidth = font.getStringWidth(text) / 1000 * fontSize;
                    cs.newLineAtOffset((box.getWidth() - textWidth) / 2, 20);
                    cs.showText(text);
                    cs.endText();
                }
            }
            document.save(destination.toFile());
        }
    }

    /**
     * Converts one or more image files (JPG/PNG) into a single PDF.
     */
    public void imagesToPdf(List<Path> imagePaths, Path destination) throws IOException {
        try (PDDocument document = new PDDocument()) {
            for (Path imgPath : imagePaths) {
                PDImageXObject image = PDImageXObject.createFromFile(
                        imgPath.toAbsolutePath().toString(), document);
                // Scale to A4 if image is too large, else keep natural size
                float imgW = image.getWidth();
                float imgH = image.getHeight();
                float maxW = PDRectangle.A4.getWidth();
                float maxH = PDRectangle.A4.getHeight();
                float scale = Math.min(maxW / imgW, maxH / imgH);
                if (scale > 1) scale = 1; // never upscale
                float w = imgW * scale;
                float h = imgH * scale;

                PDPage page = new PDPage(new PDRectangle(w, h));
                document.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                    cs.drawImage(image, 0, 0, w, h);
                }
            }
            document.save(destination.toFile());
        }
    }

    /**
     * Renders each page of a PDF as a JPG image and returns paths to the images.
     */
    public List<Path> pdfToImages(Path source, Path tempDir) throws IOException {
        List<Path> imagePaths = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(source.toFile())) {
            PDFRenderer renderer = new PDFRenderer(document);
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, 150, ImageType.RGB);
                Path imgPath = tempDir.resolve(
                        UUID.randomUUID() + "_page_" + (i + 1) + ".jpg");
                ImageIO.write(image, "JPEG", imgPath.toFile());
                imagePaths.add(imgPath);  // ← was missing!
            }
        }
        return imagePaths;
    }


    /**
     * Removes watermark by stripping the last appended content stream from every page.
     * Since addWatermark() uses AppendMode.APPEND, the watermark is always the
     * last stream in each page's content array.
     */
    public void removeWatermark(Path source, Path destination) throws IOException {
        try (PDDocument document = Loader.loadPDF(source.toFile())) {
            for (PDPage page : document.getPages()) {
                COSBase contents = page.getCOSObject().getDictionaryObject(COSName.CONTENTS);
                if (contents instanceof COSArray) {
                    COSArray arr = (COSArray) contents;
                    if (arr.size() > 1) {
                        // Remove the last stream — that is our appended watermark
                        arr.remove(arr.size() - 1);
                    }
                }
                // If contents is a single stream (COSStream), the page was never
                // watermarked with our tool — leave it untouched.
            }
            document.save(destination.toFile());
        }
    }

    // -------------------------------------------------------------------------
    // Helper: create a graphics state with the given opacity
    // -------------------------------------------------------------------------
    private org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
    buildTransparency(PDDocument doc, float opacity) {
        org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState gs =
                new org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState();
        gs.setNonStrokingAlphaConstant(opacity);
        gs.setStrokingAlphaConstant(opacity);
        gs.setAlphaSourceFlag(true);
        return gs;
    }

    /**
     * Deletes specific pages from a PDF and returns a single merged PDF with those pages removed.
     * pagesToDelete is a comma-separated string e.g. "1, 3, 5-7"
     */
    public void deletePages(Path source, Path destination, String pagesToDelete) throws IOException {
        try (PDDocument original = Loader.loadPDF(source.toFile());
             PDDocument result = new PDDocument()) {

            int totalPages = original.getNumberOfPages();
            Set<Integer> toDelete = parsePages(pagesToDelete, totalPages);

            if (toDelete.isEmpty()) {
                throw new IllegalArgumentException("No pages specified. Please enter page numbers to delete (e.g. 1, 3, 5-7).");
            }

            if (toDelete.size() >= totalPages) {
                throw new IllegalArgumentException("Cannot delete all pages. At least one page must remain.");
            }

            for (int i = 0; i < totalPages; i++) {
                if (!toDelete.contains(i + 1)) { // pages are 1-indexed
                    result.importPage(original.getPage(i));
                }
            }

            result.save(destination.toFile());
        }
    }

    private Set<Integer> parsePages(String input, int maxPage) {
        Set<Integer> pages = new java.util.HashSet<>();
        if (input == null || input.trim().isEmpty()) return pages;
        for (String part : input.split(",")) {
            part = part.trim();
            if (part.contains("-")) {
                String[] range = part.split("-", 2);
                try {
                    int start = Integer.parseInt(range[0].trim());
                    int end   = Integer.parseInt(range[1].trim());
                    for (int i = Math.min(start, end); i <= Math.max(start, end); i++) {
                        if (i >= 1 && i <= maxPage) pages.add(i);
                    }
                } catch (NumberFormatException ignored) {}
            } else {
                try {
                    int p = Integer.parseInt(part);
                    if (p >= 1 && p <= maxPage) pages.add(p);
                } catch (NumberFormatException ignored) {}
            }
        }
        return pages;
    }

    /**
     * Automatically sorts the pages of a PDF based on page numbers extracted from the text.
     */
    public void autoSortPages(Path source, Path destination) throws IOException {
        try (PDDocument original = Loader.loadPDF(source.toFile());
             PDDocument sorted = new PDDocument()) {
            
            int totalPages = original.getNumberOfPages();
            org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
            
            class PageEntry implements Comparable<PageEntry> {
                PDPage page;
                int logicalNumber;
                int originalIndex;
                
                PageEntry(PDPage page, int logicalNumber, int originalIndex) {
                    this.page = page;
                    this.logicalNumber = logicalNumber;
                    this.originalIndex = originalIndex;
                }
                
                @Override
                public int compareTo(PageEntry o) {
                    if (this.logicalNumber != o.logicalNumber) {
                        return Integer.compare(this.logicalNumber, o.logicalNumber);
                    }
                    return Integer.compare(this.originalIndex, o.originalIndex);
                }
            }
            
            List<PageEntry> entries = new ArrayList<>();
            
            for (int i = 0; i < totalPages; i++) {
                PDPage page = original.getPage(i);
                
                // Extract text for this specific page
                stripper.setStartPage(i + 1);
                stripper.setEndPage(i + 1);
                String text = stripper.getText(original);
                
                int extractedNumber = extractPageNumber(text, i + 1, totalPages);
                entries.add(new PageEntry(page, extractedNumber, i));
            }
            
            java.util.Collections.sort(entries);
            
            for (PageEntry entry : entries) {
                sorted.importPage(entry.page);
            }
            
            sorted.save(destination.toFile());
        }
    }

    private int extractPageNumber(String text, int defaultIndex, int totalPages) {
        if (text == null || text.trim().isEmpty()) {
            return defaultIndex + 10000; // Put unnumbered pages at the end
        }
        
        String[] lines = text.split("\\r?\\n");
        List<String> nonEmptyLines = new ArrayList<>();
        for (String line : lines) {
            String t = line.trim();
            if (!t.isEmpty()) nonEmptyLines.add(t);
        }
        
        if (nonEmptyLines.isEmpty()) {
            return defaultIndex + 10000;
        }
        
        // 1. Look for explicit "Page X" or "X / Y" anywhere
        java.util.regex.Pattern p1 = java.util.regex.Pattern.compile("(?i)^page\\s+(\\d+)");
        java.util.regex.Pattern p2 = java.util.regex.Pattern.compile("^(\\d+)\\s*/\\s*\\d+$");
        
        for (String line : nonEmptyLines) {
            java.util.regex.Matcher m1 = p1.matcher(line);
            if (m1.find()) return Integer.parseInt(m1.group(1));
            
            java.util.regex.Matcher m2 = p2.matcher(line);
            if (m2.find()) return Integer.parseInt(m2.group(1));
        }
        
        // 2. Look for standalone numbers in the last 3 lines
        for (int i = Math.max(0, nonEmptyLines.size() - 3); i < nonEmptyLines.size(); i++) {
            String line = nonEmptyLines.get(i);
            if (line.matches("^\\d+$")) {
                int num = Integer.parseInt(line);
                if (num > 0 && num <= totalPages + 100) return num;
            }
        }
        
        // 3. Look for standalone numbers in the first 3 lines
        for (int i = 0; i < Math.min(3, nonEmptyLines.size()); i++) {
            String line = nonEmptyLines.get(i);
            if (line.matches("^\\d+$")) {
                int num = Integer.parseInt(line);
                if (num > 0 && num <= totalPages + 100) return num;
            }
        }
        
        // Default to keeping at the end if not found
        return defaultIndex + 10000;
    }
}
