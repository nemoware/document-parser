package com.nemo.document.parser;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.model.StyleDescription;
import org.apache.poi.hwpf.usermodel.CharacterRun;
import org.apache.poi.hwpf.usermodel.Paragraph;
import org.apache.poi.hwpf.usermodel.Range;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.xwpf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DocumentParser {
    private static String dateRegEx = "(?<day>[1-2][0-9]|3[01]|0?[1-9]).{0,3}?\\s*(?<month>1[0-2]|0[1-9]|января|февраля|марта|апреля|мая|июня|июля|августа|сентября|октября|ноября|декабря).?\\s*(?<year>[1-2]\\d{3})";
    private static Pattern datePattern = Pattern.compile(dateRegEx, Pattern.CASE_INSENSITIVE);
    private static Pattern documentNumberPattern = Pattern.compile("№\\s*(?<number>.*?)\\s+");
    private static Logger logger = LoggerFactory.getLogger(DocumentParser.class);
    private static     String[] shortMonths = {
            "янв", "фев", "мар", "апр", "ма", "июн",
            "июл", "авг", "сен", "окт", "ноя", "дек"};
    private static Map<String, DocumentType> keyToDocType = Map.ofEntries(
            new AbstractMap.SimpleEntry<>("договор", DocumentType.CONTRACT),
            new AbstractMap.SimpleEntry<>("устав", DocumentType.CHARTER),
            new AbstractMap.SimpleEntry<>("протокол", DocumentType.PROTOCOL),
            new AbstractMap.SimpleEntry<>("положени", DocumentType.REGULATION),
            new AbstractMap.SimpleEntry<>("благотворител", DocumentType.CHARITY_POLICY),
            new AbstractMap.SimpleEntry<>("приказ", DocumentType.ORDER),
            new AbstractMap.SimpleEntry<>("план работ", DocumentType.WORK_PLAN),
            new AbstractMap.SimpleEntry<>("дополнительное соглашение", DocumentType.SUPPLEMENTARY_AGREEMENT),
            new AbstractMap.SimpleEntry<>("приложение", DocumentType.ANNEX)
    );
    private static Pattern tableOfContentDocPattern = Pattern.compile("\"_Toc\\d+\"");
    static{
        ZipSecureFile.setMinInflateRatio(0.0001d);
    }

    public static DocumentStructure parse(String filePath) throws IOException {
        String extension = filePath.substring(filePath.lastIndexOf(".") + 1).toUpperCase();
        return parse(new FileInputStream(new File(filePath)), DocumentFileType.valueOf(extension));
    }

    public static DocumentStructure parse(InputStream inputStream, DocumentFileType documentFileType) throws IOException {
        DocumentStructure result = new DocumentStructure();
        com.nemo.document.parser.Paragraph currentParagraph = null;
        boolean isPrevHeader = false;
        switch(documentFileType){
            case DOC:
                HWPFDocument doc = new HWPFDocument(inputStream);
                Range range = doc.getRange();
                int paragraphQuantity = range.numParagraphs();
                for(int i = 0; i < paragraphQuantity; i++){
                    Paragraph paragraph = range.getParagraph(i);
                    if(!paragraph.text().trim().isEmpty()) {
                        if (isTableOfContent(paragraph.text())){
                            continue;
                        }
//                        StyleDescription styleDescription = doc.getStyleSheet().getStyleDescription(paragraph.getStyleIndex());
                        if (isHeader(paragraph)) {
                            if (isPrevHeader) {
                                currentParagraph.getParagraphHeader().addText(paragraph.text());
                            } else {
                                currentParagraph = new com.nemo.document.parser.Paragraph();
                                result.addParagraph(currentParagraph);
                                currentParagraph.setParagraphHeader(new TextSegment(paragraph.getStartOffset(), paragraph.text()));
                            }
                            isPrevHeader = true;
                        } else {
                            if (currentParagraph == null) {
                                currentParagraph = new com.nemo.document.parser.Paragraph();
                                result.addParagraph(currentParagraph);
                            }
                            if (currentParagraph.getParagraphBody().getOffset() == -1) {
                                currentParagraph.setParagraphBody(new TextSegment(paragraph.getStartOffset(), paragraph.text()));
                            } else {
                                currentParagraph.getParagraphBody().addText(paragraph.text());
                            }
                            isPrevHeader = false;
                        }
                    }
                }
                break;
            case DOCX:
                XWPFDocument docx = new XWPFDocument(inputStream);
                List<XWPFParagraph> paragraphs = docx.getParagraphs();
                //todo: skip XWPFSDT
                int globalOffset = 0;
//                List<XWPFParagraph> excludeParagraphs = new ArrayList<>();
//                List<XWPFTable> tables = docx.getTables();
//                for (XWPFTable table : tables){
//                    List<XWPFTableRow> rows = table.getRows();
//                    for(XWPFTableRow row : rows){
//                        List<XWPFTableCell> cells = row.getTableCells();
//                        for(XWPFTableCell cell : cells){
//                            excludeParagraphs.addAll(cell.getParagraphs());
//                        }
//                    }
//                }
                for(XWPFParagraph paragraph : paragraphs){
                    if(!paragraph.getText().trim().isEmpty()) {
                        if (isHeader(paragraph, null)) {
                            if (isPrevHeader) {
                                currentParagraph.getParagraphHeader().addText(paragraph.getText());
                            } else {
                                currentParagraph = new com.nemo.document.parser.Paragraph();
                                result.addParagraph(currentParagraph);
                                currentParagraph.setParagraphHeader(new TextSegment(globalOffset, paragraph.getText()));
                            }
                            isPrevHeader = true;
                        } else {
                            if (currentParagraph == null) {
                                currentParagraph = new com.nemo.document.parser.Paragraph();
                                result.addParagraph(currentParagraph);
                            }
                            if (currentParagraph.getParagraphBody().getOffset() == -1) {
                                currentParagraph.setParagraphBody(new TextSegment(globalOffset, paragraph.getText()));
                            } else {
                                currentParagraph.getParagraphBody().addText(paragraph.getText());
                            }
                            isPrevHeader = false;
                        }
                        globalOffset += paragraph.getText().length();
                    }
                }
                break;
        }
        if(result.getParagraphs().size() > 0){
            com.nemo.document.parser.Paragraph firstParagraph = result.getParagraphs().get(0);
            result.setDocumentType(findDocumentType(firstParagraph));
            result.setDocumentDate(findDocumentDate(firstParagraph));
            result.setDocumentNumber(findDocumentNumber(firstParagraph));
        }
        return result;
    }

    private static String findDocumentNumber(com.nemo.document.parser.Paragraph firstParagraph){
        String result = "";
        if(firstParagraph.getParagraphHeader() != null) {
            Matcher matcher = documentNumberPattern.matcher(firstParagraph.getParagraphHeader().getText());
            if(matcher.find()){
                result = matcher.group("number");
            }
        }
        return result;
    }

    private static LocalDate findDocumentDate(com.nemo.document.parser.Paragraph firstParagraph){
        if(firstParagraph.getParagraphHeader() != null) {
            String firstHeader = firstParagraph.getParagraphHeader().getText();
            Matcher matcher = datePattern.matcher(firstHeader.toLowerCase());
            if (matcher.find()) {
                return parseDate(matcher);
            } else {
                if (firstParagraph.getParagraphBody() != null) {
                    matcher = datePattern.matcher(firstParagraph.getParagraphBody().getText().toLowerCase());
                    if (matcher.find()) {
                        return parseDate(matcher);
                    }
                }
            }
        }
        return null;
    }

    private static DocumentType findDocumentType(com.nemo.document.parser.Paragraph firstParagraph){
        DocumentType result = DocumentType.UNKNOWN;
        if(firstParagraph.getParagraphHeader() != null) {
            int firstOccurrence = firstParagraph.getParagraphHeader().getLength();
            for(AbstractMap.Entry<String, DocumentType> entry : keyToDocType.entrySet()){
                int idx = StringUtils.indexOfIgnoreCase(firstParagraph.getParagraphHeader().getText(), entry.getKey());
                if(idx >= 0 && firstOccurrence > idx){
                    result = entry.getValue();
                    firstOccurrence = idx;
                }
            }
        }
        return result;
    }

    private static boolean isTableOfContent(String paragraphText){
        return tableOfContentDocPattern.matcher(paragraphText).find();
    }

    private static LocalDate parseDate(Matcher matcher){
        String day = matcher.group("day");
        String month = matcher.group("month");
        String year = matcher.group("year");
        return LocalDate.of(Integer.parseInt(year), getMonth(month), Integer.parseInt(day));
    }

    private static int getMonth(String monthString){
        for(int i = 0; i < shortMonths.length; i++){
            if(monthString.contains(shortMonths[i])){
                return i + 1;
            }
        }
        return Integer.parseInt(monthString);
    }

    private static boolean isHeader(Paragraph paragraph){
        if(paragraph.text().trim().length() == 0){
            return false;
        }

//        if(styleDescription.getName().equals("Title")){
//            return true;
//        }
//        if(paragraph.isInTable()){
//            return false;
//        }

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

        if(excludeParagraphs != null && excludeParagraphs.contains(paragraph)){
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
