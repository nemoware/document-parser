package com.nemo.document.parser;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.model.StyleDescription;
import org.apache.poi.hwpf.usermodel.CharacterRun;
import org.apache.poi.hwpf.usermodel.Paragraph;
import org.apache.poi.hwpf.usermodel.Range;
import org.apache.poi.xwpf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DocumentParser {
    private static String dateRegEx = ".?(?<day>0?[1-9]|[1-2][0-9]|3[01]).?\\s*(?<month>января|февраля|марта|апреля|мая|июня|июля|августа|сентября|октября|ноября|декабря)\\s*(?<year>\\d{4})";
    private static Logger logger = LoggerFactory.getLogger(DocumentParser.class);
    private static     String[] shortMonths = {
            "янв", "фев", "мар", "апр", "май", "июн",
            "июл", "авг", "сен", "окт", "ноя", "дек"};

    public static DocumentStructure parse(String filePath) throws IOException {
        String extension = filePath.substring(filePath.lastIndexOf(".") + 1).toUpperCase();
        return parse(new FileInputStream(filePath), DocumentType.valueOf(extension));
    }

    public static DocumentStructure parse(InputStream inputStream, DocumentType documentType) throws IOException {
        DocumentStructure result = new DocumentStructure();
        com.nemo.document.parser.Paragraph currentParagraph = null;
        boolean isPrevHeader = false;
        switch(documentType){
            case DOC:
                HWPFDocument doc = new HWPFDocument(inputStream);
                Range range = doc.getRange();
                int paragraphQuantity = range.numParagraphs();
                for(int i = 0; i < paragraphQuantity; i++){
                    Paragraph paragraph = range.getParagraph(i);
                    StyleDescription styleDescription = doc.getStyleSheet().getStyleDescription(paragraph.getStyleIndex());
                    if(isHeader(paragraph, styleDescription)){
                        if(isPrevHeader){
                            currentParagraph.getParagraphHeader().addText(paragraph.text());
                        }
                        else {
                            currentParagraph = new com.nemo.document.parser.Paragraph();
                            result.addParagraph(currentParagraph);
                            currentParagraph.setParagraphHeader(new TextSegment(paragraph.getStartOffset(), paragraph.text()));
                        }
                        isPrevHeader = true;
                    }
                    else{
                        if(currentParagraph == null){
                            currentParagraph = new com.nemo.document.parser.Paragraph();
                            result.addParagraph(currentParagraph);
                        }
                        if(currentParagraph.getParagraphBody() == null) {
                            currentParagraph.setParagraphBody(new TextSegment(paragraph.getStartOffset(), paragraph.text()));
                        }
                        else{
                            currentParagraph.getParagraphBody().addText(paragraph.text());
                        }
                        isPrevHeader = false;
                    }
                }
                break;
            case DOCX:
                XWPFDocument docx = new XWPFDocument(inputStream);
                List<XWPFParagraph> paragraphs = docx.getParagraphs();
                int globalOffset = 0;
                List<XWPFParagraph> excludeParagraphs = new ArrayList<>();
                List<XWPFTable> tables = docx.getTables();
                for (XWPFTable table : tables){
                    List<XWPFTableRow> rows = table.getRows();
                    for(XWPFTableRow row : rows){
                        List<XWPFTableCell> cells = row.getTableCells();
                        for(XWPFTableCell cell : cells){
                            excludeParagraphs.addAll(cell.getParagraphs());
                        }
                    }
                }
                for(XWPFParagraph paragraph : paragraphs){
                    if(isHeader(paragraph, excludeParagraphs)){
                        if(isPrevHeader){
                            currentParagraph.getParagraphHeader().addText(paragraph.getText());
                        }
                        else {
                            currentParagraph = new com.nemo.document.parser.Paragraph();
                            result.addParagraph(currentParagraph);
                            currentParagraph.setParagraphHeader(new TextSegment(globalOffset, paragraph.getText()));
                        }
                        isPrevHeader = true;
                    }
                    else{
                        if(currentParagraph == null){
                            currentParagraph = new com.nemo.document.parser.Paragraph();
                            result.addParagraph(currentParagraph);
                        }
                        if(currentParagraph.getParagraphBody() == null) {
                            currentParagraph.setParagraphBody(new TextSegment(globalOffset, paragraph.getText()));
                        }
                        else{
                            currentParagraph.getParagraphBody().addText(paragraph.getText());
                        }
                        isPrevHeader = false;
                    }
                    globalOffset += paragraph.getText().length();
                }
                break;
        }
        if(result.getParagraphs().size() > 0){
            com.nemo.document.parser.Paragraph firstParagraph = result.getParagraphs().get(0);
            Pattern pattern = Pattern.compile(dateRegEx, Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(firstParagraph.getParagraphHeader().getText());
            if(matcher.find()){
                String dateSubString = firstParagraph.getParagraphHeader().getText().substring(matcher.start(), matcher.end());

            }
            else{
                matcher = pattern.matcher(firstParagraph.getParagraphBody().getText());
                if(matcher.find()) {
                    String day = matcher.group("day");
                    String month = matcher.group("month");
                    String year = matcher.group("year");
                    result.setDocumentDate(LocalDate.of(Integer.parseInt(year), getMonth(month), Integer.parseInt(day)));
                }
            }
        }
        return result;
    }

    private static int getMonth(String monthString){
        for(int i = 0; i < shortMonths.length; i++){
            if(monthString.contains(shortMonths[i])){
                return i + 1;
            }
        }
        return 0;
    }

    private static boolean isHeader(Paragraph paragraph, StyleDescription styleDescription){
        if(paragraph.text().trim().length() == 0){
            return false;
        }

//        if(styleDescription.getName().equals("Title")){
//            return true;
//        }
        if(paragraph.isInTable()){
            return false;
        }

        int alignment = paragraph.getFontAlignment();
        int justification = paragraph.getJustification();
        if(alignment == 3 || justification == 1){
            return true;
        }
        int characterRunQuantity = paragraph.numCharacterRuns();
        boolean allCharactersBold = true;
        boolean allCharactersCapitalized = true;
        for(int j = 0; j < characterRunQuantity; j++) {
            CharacterRun characterRun = paragraph.getCharacterRun(j);
            if(!characterRun.text().equals(characterRun.text().toUpperCase())){
                allCharactersCapitalized = false;
            }
            if(!characterRun.isBold()){
                allCharactersBold = false;
            }
        }
        return allCharactersBold || allCharactersCapitalized;
    }

    private static boolean isHeader(XWPFParagraph paragraph, List<XWPFParagraph> excludeParagraphs){
        if(paragraph.getText().trim().length() == 0){
            return false;
        }

//        XWPFStyle style = paragraph.getDocument().getStyles().getStyle(paragraph.getStyleID());
//        if(style != null && style.getName().equals("Title")){
//            return true;
//        }

        if(excludeParagraphs.contains(paragraph)){
            return false;
        }

        ParagraphAlignment alignment = paragraph.getAlignment();
        if(alignment.equals(ParagraphAlignment.CENTER)){
            return true;
        }

        List<XWPFRun> runs = paragraph.getRuns();
        boolean allCharactersBold = true;
        boolean allCharactersCapitalized = true;
        for(XWPFRun run : runs) {
            if(!run.text().equals(run.text().toUpperCase())){
                allCharactersCapitalized = false;
            }
            if(!run.isBold()){
                allCharactersBold = false;
            }
        }
        return allCharactersBold || allCharactersCapitalized;
    }
}
