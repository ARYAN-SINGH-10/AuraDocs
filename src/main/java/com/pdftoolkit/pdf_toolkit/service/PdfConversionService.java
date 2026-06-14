package com.pdftoolkit.pdf_toolkit.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.tools.PDFText2HTML;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class PdfConversionService {

    /**
     * Converts a PDF to plain text.
     */
    public void pdfToText(Path source, Path destination) throws IOException {
        try (PDDocument document = Loader.loadPDF(source.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            Files.writeString(destination, text);
        }
    }

    /**
     * Converts a PDF to a basic HTML string by extracting text with HTML tags.
     */
    public void pdfToHtml(Path source, Path destination) throws IOException {
        try (PDDocument document = Loader.loadPDF(source.toFile())) {
            PDFText2HTML stripper = new PDFText2HTML();
            String html = stripper.getText(document);
            Files.writeString(destination, html);
        }
    }

    /**
     * Extracts text from a PDF and writes it into a basic Word document (.docx).
     * Note: This does not preserve layout, tables, or images.
     */
    public void pdfToWord(Path source, Path destination) throws IOException {
        try (PDDocument document = Loader.loadPDF(source.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            try (XWPFDocument docx = new XWPFDocument()) {
                String[] lines = text.split("\\r?\\n");
                for (String line : lines) {
                    XWPFParagraph paragraph = docx.createParagraph();
                    XWPFRun run = paragraph.createRun();
                    run.setText(line);
                }
                try (FileOutputStream out = new FileOutputStream(destination.toFile())) {
                    docx.write(out);
                }
            }
        }
    }

    /**
     * Extracts text from a PDF and attempts to write it into an Excel sheet (.xlsx).
     * Note: It splits text by lines and spaces, which is a very naive approach to tables.
     */
    public void pdfToExcel(Path source, Path destination) throws IOException {
        try (PDDocument document = Loader.loadPDF(source.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            try (XSSFWorkbook workbook = new XSSFWorkbook()) {
                XSSFSheet sheet = workbook.createSheet("Extracted Data");
                String[] lines = text.split("\\r?\\n");
                
                int rowNum = 0;
                for (String line : lines) {
                    XSSFRow row = sheet.createRow(rowNum++);
                    // Split by 2+ spaces or tabs to guess column boundaries.
                    // If no clear delimiter exists, put the whole line in one cell.
                    String[] cells = line.split("\\s{2,}|\\t");
                    int colNum = 0;
                    for (String cellText : cells) {
                        row.createCell(colNum++).setCellValue(cellText.trim());
                    }
                }
                try (FileOutputStream out = new FileOutputStream(destination.toFile())) {
                    workbook.write(out);
                }
            }
        }
    }
    
    /**
     * Extracts text from a PDF and creates a basic PowerPoint presentation (.pptx).
     * Note: This is a basic conversion that puts chunks of text onto slides.
     */
    public void pdfToPowerPoint(Path source, Path destination) throws IOException {
        try (PDDocument document = Loader.loadPDF(source.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            try (org.apache.poi.xslf.usermodel.XMLSlideShow ppt = new org.apache.poi.xslf.usermodel.XMLSlideShow()) {
                String[] lines = text.split("\\r?\\n");
                StringBuilder slideText = new StringBuilder();
                int lineCount = 0;

                for (String line : lines) {
                    if (line.trim().isEmpty()) continue;
                    slideText.append(line).append("\n");
                    lineCount++;

                    // Create a new slide every 15 lines
                    if (lineCount >= 15) {
                        createSlide(ppt, slideText.toString());
                        slideText.setLength(0);
                        lineCount = 0;
                    }
                }

                // Create a slide for any remaining text
                if (slideText.length() > 0) {
                    createSlide(ppt, slideText.toString());
                }

                try (FileOutputStream out = new FileOutputStream(destination.toFile())) {
                    ppt.write(out);
                }
            }
        }
    }

    private void createSlide(org.apache.poi.xslf.usermodel.XMLSlideShow ppt, String content) {
        org.apache.poi.xslf.usermodel.XSLFSlide slide = ppt.createSlide();
        org.apache.poi.xslf.usermodel.XSLFTextBox shape = slide.createTextBox();
        shape.setAnchor(new java.awt.Rectangle(50, 50, 600, 400));
        
        org.apache.poi.xslf.usermodel.XSLFTextParagraph p = shape.addNewTextParagraph();
        org.apache.poi.xslf.usermodel.XSLFTextRun r = p.addNewTextRun();
        r.setText(content);
        r.setFontSize(14.0);
    }
}
