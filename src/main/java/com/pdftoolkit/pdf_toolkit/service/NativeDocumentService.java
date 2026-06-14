package com.pdftoolkit.pdf_toolkit.service;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.BodyElementType;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.poifs.crypt.HashAlgorithm;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.poifs.crypt.EncryptionInfo;
import org.apache.poi.poifs.crypt.EncryptionMode;
import org.apache.poi.poifs.crypt.Encryptor;
import org.apache.poi.openxml4j.opc.OPCPackage;
import java.io.OutputStream;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
public class NativeDocumentService {

    private final PdfService pdfService;
    private final OfficeConversionService officeConversionService;

    public NativeDocumentService(PdfService pdfService, OfficeConversionService officeConversionService) {
        this.pdfService = pdfService;
        this.officeConversionService = officeConversionService;
    }

    public void merge(List<Path> sources, Path destination) throws IOException {
        if (sources == null || sources.isEmpty()) return;

        String ext = getExtension(sources.get(0).getFileName().toString()).toLowerCase();

        switch (ext) {
            case "pdf":
                pdfService.merge(sources, destination);
                break;
            case "docx":
            case "doc":
                mergeWord(sources, destination);
                break;
            case "xlsx":
            case "xls":
                mergeExcel(sources, destination);
                break;
            case "html":
            case "htm":
                mergeHtml(sources, destination);
                break;
            case "txt":
                mergeTxt(sources, destination);
                break;
            default:
                throw new IllegalArgumentException("Unsupported file type for merge: " + ext);
        }
    }

    public void protect(Path source, Path destination, String password) throws Exception {
        String ext = getExtension(source.getFileName().toString()).toLowerCase();

        switch (ext) {
            case "pdf":
                pdfService.protect(source, destination, password, "owner_" + password);
                break;
            case "docx":
            case "xlsx":
                protectOOXML(source, destination, password);
                break;
            case "doc":
            case "xls":
            case "html":
            case "htm":
            case "txt":
                Path tempPdf = officeConversionService.convertToPdf(source, source.getParent());
                pdfService.protect(tempPdf, destination, password, "owner_" + password);
                Files.deleteIfExists(tempPdf);
                break;
            default:
                throw new IllegalArgumentException("Unsupported file type for protect: " + ext);
        }
    }

    private void mergeWord(List<Path> sources, Path destination) throws IOException {
        try (XWPFDocument destDoc = new XWPFDocument()) {
            for (Path source : sources) {
                try (FileInputStream fis = new FileInputStream(source.toFile());
                     XWPFDocument srcDoc = new XWPFDocument(fis)) {
                    
                    for (IBodyElement element : srcDoc.getBodyElements()) {
                        if (element.getElementType() == BodyElementType.PARAGRAPH) {
                            XWPFParagraph srcP = (XWPFParagraph) element;
                            XWPFParagraph destP = destDoc.createParagraph();
                            destP.setAlignment(srcP.getAlignment());
                            for (org.apache.poi.xwpf.usermodel.XWPFRun srcRun : srcP.getRuns()) {
                                org.apache.poi.xwpf.usermodel.XWPFRun destRun = destP.createRun();
                                destRun.setText(srcRun.text());
                                destRun.setBold(srcRun.isBold());
                                destRun.setItalic(srcRun.isItalic());
                            }
                        }
                    }
                }
            }
            try (FileOutputStream fos = new FileOutputStream(destination.toFile())) {
                destDoc.write(fos);
            }
        }
    }

    private void mergeExcel(List<Path> sources, Path destination) throws IOException {
        try (XSSFWorkbook destWorkbook = new XSSFWorkbook()) {
            int sheetIndex = 1;
            for (Path source : sources) {
                try (FileInputStream fis = new FileInputStream(source.toFile());
                     XSSFWorkbook srcWorkbook = new XSSFWorkbook(fis)) {
                    
                    for (int i = 0; i < srcWorkbook.getNumberOfSheets(); i++) {
                        XSSFSheet srcSheet = srcWorkbook.getSheetAt(i);
                        XSSFSheet destSheet = destWorkbook.createSheet(srcSheet.getSheetName() + "_" + sheetIndex++);
                        for (org.apache.poi.ss.usermodel.Row srcRow : srcSheet) {
                            org.apache.poi.ss.usermodel.Row destRow = destSheet.createRow(srcRow.getRowNum());
                            for (org.apache.poi.ss.usermodel.Cell srcCell : srcRow) {
                                org.apache.poi.ss.usermodel.Cell destCell = destRow.createCell(srcCell.getColumnIndex());
                                switch (srcCell.getCellType()) {
                                    case STRING: destCell.setCellValue(srcCell.getStringCellValue()); break;
                                    case NUMERIC: destCell.setCellValue(srcCell.getNumericCellValue()); break;
                                    case BOOLEAN: destCell.setCellValue(srcCell.getBooleanCellValue()); break;
                                    case FORMULA: destCell.setCellFormula(srcCell.getCellFormula()); break;
                                    default: break;
                                }
                            }
                        }
                    }
                }
            }
            try (FileOutputStream fos = new FileOutputStream(destination.toFile())) {
                destWorkbook.write(fos);
            }
        }
    }

    private void mergeHtml(List<Path> sources, Path destination) throws IOException {
        StringBuilder combinedHtml = new StringBuilder();
        combinedHtml.append("<!DOCTYPE html><html><head><title>Merged HTML</title></head><body>");
        for (Path source : sources) {
            String content = Files.readString(source);
            int bodyStart = content.toLowerCase().indexOf("<body>");
            int bodyEnd = content.toLowerCase().lastIndexOf("</body>");
            
            if (bodyStart != -1 && bodyEnd != -1 && bodyStart + 6 < bodyEnd) {
                combinedHtml.append(content.substring(bodyStart + 6, bodyEnd));
            } else {
                combinedHtml.append(content);
            }
            combinedHtml.append("<hr/>");
        }
        combinedHtml.append("</body></html>");
        Files.writeString(destination, combinedHtml.toString());
    }

    private void mergeTxt(List<Path> sources, Path destination) throws IOException {
        StringBuilder combinedText = new StringBuilder();
        for (Path source : sources) {
            combinedText.append(Files.readString(source));
            combinedText.append("\n\n---\n\n");
        }
        Files.writeString(destination, combinedText.toString());
    }

    private void protectOOXML(Path source, Path destination, String password) throws Exception {
        try (POIFSFileSystem fs = new POIFSFileSystem()) {
            EncryptionInfo info = new EncryptionInfo(EncryptionMode.agile);
            Encryptor enc = info.getEncryptor();
            enc.confirmPassword(password);
            
            try (OPCPackage opc = OPCPackage.open(source.toFile(), org.apache.poi.openxml4j.opc.PackageAccess.READ_WRITE);
                 OutputStream os = enc.getDataStream(fs)) {
                opc.save(os);
            }
            
            try (FileOutputStream fos = new FileOutputStream(destination.toFile())) {
                fs.writeFilesystem(fos);
            }
        }
    }

    public void addWatermark(Path source, Path destination, String watermarkText) throws Exception {
        String ext = getExtension(source.getFileName().toString()).toLowerCase();

        switch (ext) {
            case "pdf":
                pdfService.addWatermark(source, destination, watermarkText);
                break;
            case "docx":
            case "doc":
                watermarkWord(source, destination, watermarkText);
                break;
            case "xlsx":
            case "xls":
                watermarkExcel(source, destination, watermarkText);
                break;
            case "html":
            case "htm":
                watermarkHtml(source, destination, watermarkText);
                break;
            case "txt":
                watermarkTxt(source, destination, watermarkText);
                break;
            default:
                throw new IllegalArgumentException("Unsupported file type for watermark: " + ext);
        }
    }

    private void watermarkWord(Path source, Path destination, String watermarkText) throws Exception {
        try (FileInputStream fis = new FileInputStream(source.toFile());
             XWPFDocument doc = new XWPFDocument(fis)) {

            // Insert watermark at the end
            XWPFParagraph watermarkPara = doc.createParagraph();

            watermarkPara.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
            org.apache.poi.xwpf.usermodel.XWPFRun run = watermarkPara.createRun();
            run.setText("[ " + watermarkText + " ]");
            run.setBold(true);
            run.setColor("AAAAAA");
            run.setFontSize(14);

            try (FileOutputStream fos = new FileOutputStream(destination.toFile())) {
                doc.write(fos);
            }
        }
    }

    private void watermarkExcel(Path source, Path destination, String watermarkText) throws Exception {
        try (FileInputStream fis = new FileInputStream(source.toFile());
             XSSFWorkbook workbook = new XSSFWorkbook(fis)) {

            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                org.apache.poi.xssf.usermodel.XSSFSheet sheet = workbook.getSheetAt(i);
                // Write watermark text into the header of every sheet
                org.apache.poi.ss.usermodel.Header header = sheet.getHeader();
                header.setCenter("[ " + watermarkText + " ]");
            }

            try (FileOutputStream fos = new FileOutputStream(destination.toFile())) {
                workbook.write(fos);
            }
        }
    }

    private void watermarkHtml(Path source, Path destination, String watermarkText) throws IOException {
        String content = Files.readString(source);
        String watermarkStyle = "<style>.doc-watermark{position:fixed;top:50%;left:50%;transform:translate(-50%,-50%) rotate(-45deg);font-size:72px;color:rgba(0,0,0,0.08);font-weight:900;pointer-events:none;z-index:9999;white-space:nowrap;user-select:none;}</style>";
        String watermarkDiv = "<div class=\"doc-watermark\">" + watermarkText + "</div>";

        // Inject before </body>
        int bodyClose = content.toLowerCase().lastIndexOf("</body>");
        String result;
        if (bodyClose != -1) {
            result = watermarkStyle + content.substring(0, bodyClose) + watermarkDiv + content.substring(bodyClose);
        } else {
            result = watermarkStyle + content + watermarkDiv;
        }
        Files.writeString(destination, result);
    }

    private void watermarkTxt(Path source, Path destination, String watermarkText) throws IOException {
        String content = Files.readString(source);
        String banner = "*** " + watermarkText.toUpperCase() + " ***\n\n";
        Files.writeString(destination, banner + content + "\n\n" + banner);
    }

    public void extractPages(Path source, Path destination, String pagesToExtract) throws Exception {
        String ext = getExtension(source.getFileName().toString()).toLowerCase();
        switch (ext) {
            case "pdf":
                pdfService.extractPages(source, destination, pagesToExtract);
                break;
            case "xlsx":
            case "xls":
                extractExcel(source, destination, pagesToExtract);
                break;
            case "doc":
            case "docx":
            case "html":
            case "htm":
            case "txt":
                Path tempPdf = officeConversionService.convertToPdf(source, source.getParent());
                pdfService.extractPages(tempPdf, destination, pagesToExtract);
                Files.deleteIfExists(tempPdf);
                break;
            default:
                throw new UnsupportedOperationException("Extract Pages is not natively supported for " + ext + " format as it does not have physical pages/sheets.");
        }
    }

    public List<Path> splitDocument(Path source, Path tempDir, String splitMode, Integer splitParam) throws Exception {
        String ext = getExtension(source.getFileName().toString()).toLowerCase();
        switch (ext) {
            case "pdf":
                return pdfService.splitDocument(source, tempDir, splitMode, splitParam);
            case "xlsx":
            case "xls":
                return splitExcel(source, tempDir, splitMode, splitParam);
            case "doc":
            case "docx":
            case "html":
            case "htm":
            case "txt":
                Path tempPdf = officeConversionService.convertToPdf(source, tempDir);
                List<Path> splitFiles = pdfService.splitDocument(tempPdf, tempDir, splitMode, splitParam);
                Files.deleteIfExists(tempPdf);
                return splitFiles;
            default:
                throw new UnsupportedOperationException("Split Document is not natively supported for " + ext + " format as it does not have physical pages/sheets.");
        }
    }

    public void compress(Path source, Path destination) throws Exception {
        String ext = getExtension(source.getFileName().toString()).toLowerCase();
        if (ext.equals("pdf")) {
            pdfService.compress(source, destination);
        } else {
            Path tempPdf = officeConversionService.convertToPdf(source, source.getParent());
            pdfService.compress(tempPdf, destination);
            Files.deleteIfExists(tempPdf);
        }
    }

    public void rotate(Path source, Path destination, int degrees) throws Exception {
        String ext = getExtension(source.getFileName().toString()).toLowerCase();
        if (ext.equals("pdf")) {
            pdfService.rotate(source, destination, degrees);
        } else {
            Path tempPdf = officeConversionService.convertToPdf(source, source.getParent());
            pdfService.rotate(tempPdf, destination, degrees);
            Files.deleteIfExists(tempPdf);
        }
    }

    public void deletePages(Path source, Path destination, String pagesToDelete) throws Exception {
        String ext = getExtension(source.getFileName().toString()).toLowerCase();
        if (ext.equals("pdf")) {
            pdfService.deletePages(source, destination, pagesToDelete);
        } else {
            Path tempPdf = officeConversionService.convertToPdf(source, source.getParent());
            pdfService.deletePages(tempPdf, destination, pagesToDelete);
            Files.deleteIfExists(tempPdf);
        }
    }

    public void reorderPages(Path source, Path destination) throws Exception {
        String ext = getExtension(source.getFileName().toString()).toLowerCase();
        if (ext.equals("pdf")) {
            pdfService.autoSortPages(source, destination);
        } else {
            Path tempPdf = officeConversionService.convertToPdf(source, source.getParent());
            pdfService.autoSortPages(tempPdf, destination);
            Files.deleteIfExists(tempPdf);
        }
    }

    public void removeWatermark(Path source, Path destination) throws Exception {
        String ext = getExtension(source.getFileName().toString()).toLowerCase();
        switch (ext) {
            case "pdf":
                pdfService.removeWatermark(source, destination);
                break;
            case "doc":
            case "docx":
                removeWatermarkWord(source, destination);
                break;
            default:
                Path tempPdf = officeConversionService.convertToPdf(source, source.getParent());
                pdfService.removeWatermark(tempPdf, destination);
                Files.deleteIfExists(tempPdf);
                break;
        }
    }

    private void removeWatermarkWord(Path source, Path destination) throws Exception {
        try (java.io.FileInputStream fis = new java.io.FileInputStream(source.toFile());
             org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument(fis)) {

             java.util.List<org.apache.poi.xwpf.usermodel.XWPFParagraph> paragraphs = doc.getParagraphs();
             for (int i = paragraphs.size() - 1; i >= 0; i--) {
                 org.apache.poi.xwpf.usermodel.XWPFParagraph para = paragraphs.get(i);
                 String text = para.getText();
                 if (text != null && text.trim().startsWith("[") && text.trim().endsWith("]")) {
                     // Verify it's our watermark by checking alignment
                     if (para.getAlignment() == org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER) {
                         doc.removeBodyElement(doc.getPosOfParagraph(para));
                     }
                 }
             }

             try (java.io.FileOutputStream fos = new java.io.FileOutputStream(destination.toFile())) {
                 doc.write(fos);
             }
        }
    }

    public void addPageNumbers(Path source, Path destination) throws Exception {
        String ext = getExtension(source.getFileName().toString()).toLowerCase();
        if (ext.equals("pdf")) {
            pdfService.addPageNumbers(source, destination);
        } else {
            Path tempPdf = officeConversionService.convertToPdf(source, source.getParent());
            pdfService.addPageNumbers(tempPdf, destination);
            Files.deleteIfExists(tempPdf);
        }
    }

    private void extractExcel(Path source, Path destination, String pagesToExtract) throws Exception {
        try (FileInputStream fis = new FileInputStream(source.toFile());
             XSSFWorkbook workbook = new XSSFWorkbook(fis)) {

            int totalSheets = workbook.getNumberOfSheets();
            java.util.Set<Integer> toExtract = parsePages(pagesToExtract, totalSheets);

            if (toExtract.isEmpty()) {
                throw new IllegalArgumentException("No valid sheets selected to extract.");
            }

            for (int i = totalSheets - 1; i >= 0; i--) {
                if (!toExtract.contains(i + 1)) {
                    workbook.removeSheetAt(i);
                }
            }

            try (FileOutputStream fos = new FileOutputStream(destination.toFile())) {
                workbook.write(fos);
            }
        }
    }

    private List<Path> splitExcel(Path source, Path tempDir, String splitMode, Integer splitParam) throws Exception {
        List<Path> splitFiles = new java.util.ArrayList<>();
        try (FileInputStream fis = new FileInputStream(source.toFile());
             XSSFWorkbook workbook = new XSSFWorkbook(fis)) {
            
            int totalSheets = workbook.getNumberOfSheets();
            if ("at".equalsIgnoreCase(splitMode) && splitParam != null && splitParam > 0 && splitParam < totalSheets) {
                Path part1 = tempDir.resolve(java.util.UUID.randomUUID() + "_part1.xlsx");
                Path part2 = tempDir.resolve(java.util.UUID.randomUUID() + "_part2.xlsx");
                
                try (FileInputStream fis1 = new FileInputStream(source.toFile());
                     XSSFWorkbook wb1 = new XSSFWorkbook(fis1);
                     FileInputStream fis2 = new FileInputStream(source.toFile());
                     XSSFWorkbook wb2 = new XSSFWorkbook(fis2)) {
                    
                    for (int i = totalSheets - 1; i >= splitParam; i--) wb1.removeSheetAt(i);
                    for (int i = splitParam - 1; i >= 0; i--) wb2.removeSheetAt(i);
                    
                    try(FileOutputStream fos = new FileOutputStream(part1.toFile())) { wb1.write(fos); }
                    try(FileOutputStream fos = new FileOutputStream(part2.toFile())) { wb2.write(fos); }
                }
                splitFiles.add(part1);
                splitFiles.add(part2);
            } else {
                int interval = ("interval".equalsIgnoreCase(splitMode) && splitParam != null && splitParam > 0) ? splitParam : 1;
                int chunks = (int) Math.ceil((double) totalSheets / interval);
                for (int c = 0; c < chunks; c++) {
                    int startIdx = c * interval;
                    int endIdx = Math.min(startIdx + interval, totalSheets) - 1;
                    
                    Path partFile = tempDir.resolve(java.util.UUID.randomUUID() + "_split_" + (c + 1) + ".xlsx");
                    try (FileInputStream fisPart = new FileInputStream(source.toFile());
                         XSSFWorkbook wbPart = new XSSFWorkbook(fisPart)) {
                        for (int i = totalSheets - 1; i >= 0; i--) {
                            if (i < startIdx || i > endIdx) {
                                wbPart.removeSheetAt(i);
                            }
                        }
                        try(FileOutputStream fos = new FileOutputStream(partFile.toFile())) { wbPart.write(fos); }
                    }
                    splitFiles.add(partFile);
                }
            }
        }
        return splitFiles;
    }

    private java.util.Set<Integer> parsePages(String input, int maxPage) {
        java.util.Set<Integer> pages = new java.util.HashSet<>();
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

    public String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return (dotIndex == -1) ? "" : filename.substring(dotIndex + 1);
    }
}
