package com.pdftoolkit.pdf_toolkit.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class PageController {

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("title", "Every PDF Tool You Need in One Place");
        return "pages/index";
    }

    /**
     * Single dynamic route handles all tool pages.
     * The regex lists every valid tool slug so Spring doesn't match
     * other routes (like /error or /actuator).
     */
    @GetMapping("/{toolName:merge-pdf|split-pdf|rotate-pdf|extract-pages|delete-pages|" +
                 "reorder-pages|word-to-pdf|powerpoint-to-pdf|text-to-pdf|html-to-pdf|" +
                 "jpg-to-pdf|pdf-to-jpg|add-watermark|remove-watermark|add-page-numbers|compress-pdf|protect-pdf|" +
                 "pdf-to-word|pdf-to-excel|pdf-to-powerpoint|pdf-to-html|pdf-to-text}")
    public String loadTool(@PathVariable String toolName, Model model) {

        String  title       = "";
        String  name        = "";
        String  description = "";
        String  icon        = "file";
        String  actionUrl   = "/api/pdf/mock";
        boolean multiple    = false;
        String  accept      = ".pdf";

        switch (toolName) {

            /* ── Organize ──────────────────────────────────── */
            case "merge-pdf":
                title       = "Merge Documents Online";
                name        = "Merge Documents";
                description = "Combine multiple documents into one. Supports PDF, Word, Excel, HTML, and Text files.";
                icon        = "git-merge";
                actionUrl   = "/api/pdf/merge";
                multiple    = true;
                accept      = ".pdf,.doc,.docx,.xls,.xlsx,.html,.htm,.txt";
                break;

            case "split-pdf":
                title       = "Split Document Online";
                name        = "Split Document";
                description = "Break your PDF or Excel files into multiple files.";
                icon        = "scissors";
                actionUrl   = "/api/pdf/split";
                accept      = ".pdf,.doc,.docx,.xls,.xlsx,.html,.htm,.txt";
                break;

            case "rotate-pdf":
                title       = "Rotate Document Pages Online";
                name        = "Rotate Document";
                description = "Rotate all pages to the desired orientation in seconds. Supports PDF, Word, Excel, HTML, and Text.";
                icon        = "rotate-cw";
                actionUrl   = "/api/pdf/rotate";
                accept      = ".pdf,.doc,.docx,.xls,.xlsx,.html,.htm,.txt";
                break;

            case "extract-pages":
                title       = "Extract Pages/Sheets Online";
                name        = "Extract Pages";
                description = "Extract specific pages from your PDF or sheets from Excel into a new document.";
                icon        = "file-output";
                actionUrl   = "/api/pdf/extract";
                accept      = ".pdf,.doc,.docx,.xls,.xlsx,.html,.htm,.txt";
                break;

            case "delete-pages":
                title       = "Delete Pages Online";
                name        = "Delete Pages";
                description = "Remove unwanted pages from your document online. Supports PDF, Word, Excel, HTML, and Text.";
                icon        = "trash-2";
                actionUrl   = "/api/pdf/delete-pages";
                accept      = ".pdf,.doc,.docx,.xls,.xlsx,.html,.htm,.txt";
                break;

            case "reorder-pages":
                title       = "Reorder Document Pages Online";
                name        = "Reorder Pages";
                description = "Rearrange the page sequence. Supports PDF, Word, Excel, HTML, and Text.";
                icon        = "list-ordered";
                actionUrl   = "/api/pdf/reorder";
                accept      = ".pdf,.doc,.docx,.xls,.xlsx,.html,.htm,.txt";
                break;

            /* ── Convert ───────────────────────────────────── */
            case "word-to-pdf":
                title       = "Word to PDF Converter";
                name        = "Word to PDF";
                description = "Convert DOC and DOCX documents to clean PDF format.";
                icon        = "file-type";
                actionUrl   = "/api/pdf/word-to-pdf";
                accept      = ".doc,.docx";
                break;


            case "powerpoint-to-pdf":
                title       = "PowerPoint to PDF Converter";
                name        = "PowerPoint to PDF";
                description = "Convert PPT and PPTX presentations to clean PDF format.";
                icon        = "presentation";
                actionUrl   = "/api/pdf/powerpoint-to-pdf";
                accept      = ".ppt,.pptx";
                break;

            case "html-to-pdf":
                title       = "HTML to PDF Converter";
                name        = "HTML to PDF";
                description = "Convert HTML files to high-quality PDF documents.";
                icon        = "code";
                actionUrl   = "/api/pdf/html-to-pdf";
                accept      = ".html,.htm";
                break;

            case "jpg-to-pdf":
                title       = "JPG / PNG to PDF";
                name        = "JPG to PDF";
                description = "Convert one or multiple images into a single PDF document.";
                icon        = "image";
                actionUrl   = "/api/pdf/jpg-to-pdf";
                multiple    = true;
                accept      = ".jpg,.jpeg,.png,.bmp,.gif,.webp";
                break;

            case "pdf-to-jpg":
                title       = "PDF to JPG Converter";
                name        = "PDF to JPG";
                description = "Convert each PDF page into a high-quality JPG image.";
                icon        = "images";
                actionUrl   = "/api/pdf/pdf-to-jpg";
                break;

            case "pdf-to-word":
                title       = "PDF to Word Converter";
                name        = "PDF to Word";
                description = "Extract text from PDF and save it as a Word document.";
                icon        = "file-type";
                actionUrl   = "/api/pdf/pdf-to-word";
                break;

            case "pdf-to-excel":
                title       = "PDF to Excel Converter";
                name        = "PDF to Excel";
                description = "Extract text and tables from PDF and save as an Excel sheet.";
                icon        = "file-spreadsheet";
                actionUrl   = "/api/pdf/pdf-to-excel";
                break;

            case "pdf-to-html":
                title       = "PDF to HTML Converter";
                name        = "PDF to HTML";
                description = "Convert PDF into an HTML webpage.";
                icon        = "code";
                actionUrl   = "/api/pdf/pdf-to-html";
                break;

            case "pdf-to-text":
                title       = "PDF to Text Converter";
                name        = "PDF to Text";
                description = "Extract plain text from your PDF document.";
                icon        = "file-text";
                actionUrl   = "/api/pdf/pdf-to-text";
                break;

            case "pdf-to-powerpoint":
                title       = "PDF to PowerPoint Converter";
                name        = "PDF to PowerPoint";
                description = "Convert PDF documents into PowerPoint presentations.";
                icon        = "presentation";
                actionUrl   = "/api/pdf/pdf-to-powerpoint";
                break;

            case "text-to-pdf":
                title       = "Text to PDF Converter";
                name        = "Text to PDF";
                description = "Convert plain text files into professional PDF documents.";
                icon        = "file-type-2";
                actionUrl   = "/api/pdf/text-to-pdf";
                accept      = ".txt";
                break;

            /* ── Enhance / Security ────────────────────────── */
            case "add-watermark":
                title       = "Add Watermark to Document";
                name        = "Add Watermark";
                description = "Stamp a text watermark onto your document. Supports PDF, Word, Excel, HTML & Text.";
                icon        = "stamp";
                actionUrl   = "/api/pdf/watermark";
                accept      = ".pdf,.doc,.docx,.xls,.xlsx,.html,.htm,.txt";
                break;

            case "remove-watermark":
                title       = "Remove Watermark";
                name        = "Remove Watermark";
                description = "Remove a text watermark from your document. Supports PDF, Word, Excel, HTML, and Text.";
                icon        = "eraser";
                actionUrl   = "/api/pdf/remove-watermark";
                accept      = ".pdf,.doc,.docx,.xls,.xlsx,.html,.htm,.txt";
                break;

            case "add-page-numbers":
                title       = "Add Page Numbers";
                name        = "Add Page Numbers";
                description = "Add clean, centered page numbers. Supports PDF, Word, Excel, HTML, and Text.";
                icon        = "hash";
                actionUrl   = "/api/pdf/page-numbers";
                accept      = ".pdf,.doc,.docx,.xls,.xlsx,.html,.htm,.txt";
                break;

            case "compress-pdf":
                title       = "Compress Document Online";
                name        = "Compress Document";
                description = "Reduce file size while preserving document quality. Supports PDF, Word, Excel, HTML, and Text.";
                icon        = "file-archive";
                actionUrl   = "/api/pdf/compress";
                accept      = ".pdf,.doc,.docx,.xls,.xlsx,.html,.htm,.txt";
                break;

            case "protect-pdf":
                title       = "Protect Document with Password";
                name        = "Protect Document";
                description = "Secure your PDF, Word, or Excel document with strong password encryption.";
                icon        = "lock";
                actionUrl   = "/api/pdf/protect";
                accept      = ".pdf,.doc,.docx,.xls,.xlsx,.html,.htm,.txt";
                break;
        }

        model.addAttribute("title",       title);
        model.addAttribute("toolName",    name);
        model.addAttribute("description", description);
        model.addAttribute("icon",        icon);
        model.addAttribute("actionUrl",   actionUrl);
        model.addAttribute("multiple",    multiple);
        model.addAttribute("accept",      accept);

        return "pages/tool-base";
    }
}
