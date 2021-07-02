package com.nemo.document.parser;

import com.nemo.document.parser.web.StakeholderResponse;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.apache.pdfbox.text.TextPosition;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RectangularShape;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PdfParser {
    private static Logger logger = LoggerFactory.getLogger(PdfParser.class);
    private static float maxLineThickness = 10;
    private static float maxDiff = 1.0f;
    private static String startPagePattern = "Состав аффилированных лиц";
    private static String endPagePattern = "Изменения, произошедшие в списке аффилированных лиц";
    private static String nameColumnPattern = "Полное фирменное наименование";
    private static String reasonColumnPattern = "Основание";
    private static String reasonDateColumnPattern = "Дата наступления основания";
    private static String shareColumnPattern = "Доля участия";
    private static Pattern datePattern = Pattern.compile("(0[1-9]|[12][0-9]|3[01])\\.(0[1-9]|1[012])\\.((19|2[0-9])[0-9]{2})");
    private static Pattern numberPattern = Pattern.compile("[0-9]+([,.][0-9]*)?");

    public static StakeholderResponse parseStakeholderDocument(String filePath) throws IOException {
        String extension = filePath.substring(filePath.lastIndexOf(".") + 1).toUpperCase();
        return parseStakeholderDocument(new FileInputStream(new File(filePath)), DocumentFileType.valueOf(extension));
    }

    public static StakeholderResponse parseStakeholderDocument(InputStream inputStream, DocumentFileType documentFileType) throws IOException {
        StakeholderResponse result = new StakeholderResponse();
        result.setVersion(DocumentParser.getVersion());
        try (inputStream; PDDocument document = Loader.loadPDF(inputStream)) {
            String[] lastRowFromPreviousPage = null;
            int nameColumnIdx = -1;
            int reasonColumnIdx = -1;
            int reasonDateColumnIdx = -1;
            int shareColumnIdx = -1;
            boolean tableFound = false;
            for(int i = 0; i < document.getNumberOfPages(); i++) {
                PdfTextPositionStripper textStripper = new PdfTextPositionStripper();
                textStripper.setStartPage(i + 1);
                textStripper.setEndPage(i + 1);
                String pageText = textStripper.getText(document);
                if(pageText.contains(endPagePattern)){
                    break;
                }
                if(pageText.contains(startPagePattern)){
                    tableFound = true;
                }
                if(!tableFound){
                    continue;
                }
                String[][] table = getTableFromPage(document, i);
                boolean skipRow = false;
                if(lastRowFromPreviousPage != null){
                    if(table[0][0].trim().isEmpty() && table[0].length == lastRowFromPreviousPage.length){//merge first row and last row from previous page
                        for(int c = 0; c < lastRowFromPreviousPage.length; c++){
                            table[0][c] = lastRowFromPreviousPage[c] + table[0][c];
                        }
                    }
                    else{
                        Stakeholder stakeholder = new Stakeholder();
                        result.getStakeholders().add(stakeholder);
                        for(int c = 0; c < lastRowFromPreviousPage.length; c++){
                            processCell(nameColumnIdx, reasonColumnIdx, reasonDateColumnIdx, shareColumnIdx, stakeholder, c, lastRowFromPreviousPage);
                        }
                    }
                }
                for (int r = 0; r < table.length - 1; r++) {
                    String[] row = table[r];
                    if(skipRow){
                        skipRow = false;
                        continue;
                    }
                    Stakeholder stakeholder = new Stakeholder();
                    for (int column = 0; column < row.length; column++) {
                        String cellText = row[column];
                        if (nameColumnIdx < 0 && cellText.contains(nameColumnPattern)) {
                            nameColumnIdx = column;
                            skipRow = true;
                            continue;
                        }
                        if (reasonColumnIdx < 0 && cellText.contains(reasonColumnPattern)) {
                            reasonColumnIdx = column;
                            skipRow = true;
                            continue;
                        }
                        if(reasonDateColumnIdx < 0 && cellText.contains(reasonDateColumnPattern)){
                            reasonDateColumnIdx = column;
                            skipRow = true;
                            continue;
                        }
                        if(shareColumnIdx < 0 && cellText.contains(shareColumnPattern)){
                            shareColumnIdx = column;
                            skipRow = true;
                            continue;
                        }
                        processCell(nameColumnIdx, reasonColumnIdx, reasonDateColumnIdx, shareColumnIdx, stakeholder, column, row);
                    }
                    if(stakeholder.getName() != null){
                        result.getStakeholders().add(stakeholder);
                    }
                }
                lastRowFromPreviousPage = table[table.length - 1];
            }
            if(lastRowFromPreviousPage != null){
                Stakeholder stakeholder = new Stakeholder();
                result.getStakeholders().add(stakeholder);
                for(int column = 0; column < lastRowFromPreviousPage.length; column++){
                    processCell(nameColumnIdx, reasonColumnIdx, reasonDateColumnIdx, shareColumnIdx, stakeholder, column, lastRowFromPreviousPage);
                }
            }
        }
        return result;
    }

    private static void processCell(int nameColumnIdx, int reasonColumnIdx, int reasonDateColumnIdx, int shareColumnIdx, Stakeholder stakeholder, int column, String[] row) {
        String cellText = row[column];
//        if(column == 0){
//            logger.info(row[column]);
//        }
        if(column == nameColumnIdx) {
            stakeholder.setName(cellText.trim());
        }
        if(column == reasonColumnIdx){
            List<String> split = splitReason(cellText);
            while(stakeholder.getReasons().size() < split.size()){
                stakeholder.getReasons().add(new Reason());
            }
            for(int r = 0; r < stakeholder.getReasons().size(); r++){
                stakeholder.getReasons().get(r).setText(split.get(r));
            }
        }
        if(column == reasonDateColumnIdx){
            List<String> split = splitDates(cellText);
            while(stakeholder.getReasons().size() < split.size()){
                stakeholder.getReasons().add(new Reason());
            }
            for(int r = 0; r < split.size(); r++){
                final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM.yyyy");
                final LocalDate date = LocalDate.parse(split.get(r), dtf);
                stakeholder.getReasons().get(r).setDate(date);
            }
        }
        Matcher matcher = numberPattern.matcher(cellText);
        if(column == shareColumnIdx && matcher.find()){
            stakeholder.setShare(new BigDecimal(matcher.group(0).replace(",", ".")));
        }
    }

    private static String[][] getTableFromPage(PDDocument document, int pageNumber) throws IOException{
        PdfTextPositionStripper textStripper = new PdfTextPositionStripper();
        textStripper.setStartPage(pageNumber + 1);
        textStripper.setEndPage(pageNumber + 1);
        textStripper.getText(document);
        PDPage page = document.getPage(pageNumber);
        LineCatcher lineCatcher = new LineCatcher(page);
        lineCatcher.processPage(page);
        List<Rectangle2D> horizontalLines = new ArrayList<>();
        List<Rectangle2D> verticalLines = new ArrayList<>();
        float pageWidth = page.getMediaBox().getWidth();
        float pageHeight = page.getMediaBox().getHeight();
        for (Rectangle2D rectangle : lineCatcher.getRectangles()) {
            if (rectangle.getWidth() < maxLineThickness) {
                verticalLines.add(rectangle);
            }
            if (rectangle.getHeight() < maxLineThickness) {
                horizontalLines.add(rectangle);
            }
//                    else {
//                        logger.info("Not table line: " + rectangle.toString());
//                    }
        }
        horizontalLines = concatenateHorizontals(horizontalLines);
        verticalLines = concatenateVerticals(verticalLines);
        horizontalLines.removeIf(hl -> hl.getWidth() < pageWidth * 0.9);
        verticalLines.sort(Comparator.comparingDouble(Rectangle2D::getHeight));
        double maxVerticalLineHeight = verticalLines.get(verticalLines.size() - 1).getHeight();
        verticalLines.removeIf(vl -> maxVerticalLineHeight * 0.9 > vl.getHeight());
        verticalLines.sort(Comparator.comparingDouble(Rectangle2D::getX));
        horizontalLines.sort(Comparator.comparingDouble(Rectangle2D::getY).reversed());
        addBorderLines(horizontalLines, verticalLines);

        String[][] table = new String[horizontalLines.size() - 1][];
        for(int row = 0; row < horizontalLines.size() - 1; row++) {
            table[row] = new String[verticalLines.size() - 1];
            Arrays.fill(table[row], "");
            for(int column = 0; column < verticalLines.size() - 1; column++) {
                for (TextLine textLine : textStripper.getLines()) {
                    StringBuilder sb = new StringBuilder();
                    for(TextPosition textPosition : textLine.getTextPositions()){
                        float x = textPosition.getTextMatrix().getTranslateX();
                        float y = textPosition.getTextMatrix().getTranslateY();
                        if (y >= horizontalLines.get(row + 1).getY() && y <= horizontalLines.get(row).getY()
                                && x >= verticalLines.get(column).getX() && x <= verticalLines.get(column + 1).getX()){
                            sb.append(textPosition.getUnicode());
                        }
                    }
                    if (sb.length() > 0) {
                        table[row][column] += sb.toString();
                    }
                }
            }
        }
        return table;
    }

    private static List<Rectangle2D> concatenateVerticals(List<Rectangle2D> input){
        List<Rectangle2D> result = new ArrayList<>();
        List<Rectangle2D> tmp = new ArrayList<>(input);
        tmp.sort(Comparator.comparingDouble(RectangularShape::getY));
        for(Rectangle2D rect : tmp){
            if(isAlreadyIncluded(rect, result)){
                continue;
            }
            Rectangle2D line = new Rectangle2D.Float();
            line.setRect(rect);
            for(Rectangle2D candidate : tmp){
                if(Math.abs(candidate.getX() - line.getX()) < maxDiff && Math.abs(candidate.getY() - (line.getY() + line.getHeight())) < maxDiff){
                    line.setRect(line.getX(), line.getY(), line.getWidth(), candidate.getY() + candidate.getHeight() - line.getY());
                }
            }
            result.add(line);
        }
        return result;
    }

    private static List<Rectangle2D> concatenateHorizontals(List<Rectangle2D> input){
        List<Rectangle2D> result = new ArrayList<>();
        List<Rectangle2D> tmp = new ArrayList<>(input);
        tmp.sort(Comparator.comparingDouble(RectangularShape::getX));
        for(Rectangle2D rect : tmp){
            if(isAlreadyIncluded(rect, result)){
                continue;
            }
            Rectangle2D line = new Rectangle2D.Float();
            line.setRect(rect);
            for(Rectangle2D candidate : tmp){
                if(Math.abs(candidate.getY() - line.getY()) < maxDiff && Math.abs(candidate.getX() - (line.getX() + line.getWidth())) < maxDiff){
                    line.setRect(line.getX(), line.getY(), candidate.getX() + candidate.getWidth() - line.getX(), line.getHeight());
                }
            }
            result.add(line);
        }
        return result;
    }

    private static boolean isAlreadyIncluded(Rectangle2D shortLine, List<Rectangle2D> longLines){
        for(Rectangle2D longLine : longLines){
            if(Math.abs(longLine.getX() - shortLine.getX()) < maxDiff
                    && longLine.getY() <= shortLine.getY() && longLine.getY() + longLine.getHeight() >= shortLine.getY()){
                return true;
            }
            if(Math.abs(longLine.getY() - shortLine.getY()) < maxDiff
                    && longLine.getX() <= shortLine.getX() && longLine.getX() + longLine.getWidth() >= shortLine.getX()){
                return true;
            }
        }
        return false;
    }

    private static void addBorderLines(List<Rectangle2D> horizontalLines, List<Rectangle2D> verticalLines){
        float sumMinY = 0;
        float sumMaxY = 0;
        float sumMinX = 0;
        float sumMaxX = 0;
        for(Rectangle2D verticalLine : verticalLines){
            sumMinY += verticalLine.getY();
            sumMaxY += verticalLine.getY() + verticalLine.getHeight();
        }
        for(Rectangle2D horizontalLine : horizontalLines){
            sumMinX += horizontalLine.getX();
            sumMaxX += horizontalLine.getX() + horizontalLine.getWidth();
        }
        float avgMinY = sumMinY / verticalLines.size();
        float avgMaxY = sumMaxY / verticalLines.size();
        float avgMinX = sumMinX / horizontalLines.size();
        float avgMaxX = sumMaxX / horizontalLines.size();

        if(Math.abs(avgMinY - horizontalLines.get(horizontalLines.size() - 1).getY()) > 10){
            horizontalLines.add(new Rectangle2D.Float(avgMinX, avgMinY, avgMaxX - avgMinX, 1.0f));
        }
        if (Math.abs(avgMaxY - horizontalLines.get(0).getY()) > 10){
            horizontalLines.add(0, new Rectangle2D.Float(avgMinX, avgMaxY, avgMaxX - avgMinX, 1.0f));
        }
    }

    private static List<String> splitReason(String cellText){
        List<String> split = new ArrayList<>(Arrays.asList(cellText.split("\\d\\.")));
        split.removeIf(String::isEmpty);
        if(split.size() > 1){
            return split;
        }
        split = Arrays.asList(cellText.split("\\."));
        split.removeIf(String::isEmpty);
        return split;
    }

    private static List<String> splitDates(String cellText){
        Matcher matcher = datePattern.matcher(cellText);
        List<String> result = new ArrayList<>();
        while(matcher.find()) {
            result.add(matcher.group(0));
        }
        return result;
    }
}
