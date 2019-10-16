package com.nemo.document.parser;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.usermodel.*;
import org.apache.poi.hwpf.usermodel.Paragraph;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.xwpf.usermodel.*;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DocumentParser {
    private static String dateRegEx = "(?<day>[1-2][0-9]|3[01]|0?[1-9]).\\s*(?<month>1[0-2]|0[1-9]|января|февраля|марта|апреля|мая|июня|июля|августа|сентября|октября|ноября|декабря).\\s*(?<year>[1-2]\\d{3})";
    private static Pattern datePattern = Pattern.compile(dateRegEx, Pattern.CASE_INSENSITIVE);
    private static Pattern documentNumberPattern = Pattern.compile("№\\s*(?<number>\\S+)(\\s+|$)");
    private static Logger logger = LoggerFactory.getLogger(DocumentParser.class);
    private static     String[] shortMonths = {
            "янв", "фев", "мар", "апр", "ма", "июн",
            "июл", "авг", "сен", "окт", "ноя", "дек"};
    private static Map<Pattern, DocumentType> keyToDocType = Map.ofEntries(
            new AbstractMap.SimpleEntry<>(Pattern.compile("договор(\\s|$)"), DocumentType.CONTRACT),
            new AbstractMap.SimpleEntry<>(Pattern.compile("устав(\\s|$)"), DocumentType.CHARTER),
            new AbstractMap.SimpleEntry<>(Pattern.compile("протокол(\\s|$)"), DocumentType.PROTOCOL),
            new AbstractMap.SimpleEntry<>(Pattern.compile("положение(\\s|$)"), DocumentType.REGULATION),
            new AbstractMap.SimpleEntry<>(Pattern.compile("благотворител"), DocumentType.CHARITY_POLICY),
            new AbstractMap.SimpleEntry<>(Pattern.compile("приказ(\\s|$)"), DocumentType.ORDER),
            new AbstractMap.SimpleEntry<>(Pattern.compile("план работ"), DocumentType.WORK_PLAN),
            new AbstractMap.SimpleEntry<>(Pattern.compile("дополнительное соглашение"), DocumentType.SUPPLEMENTARY_AGREEMENT),
            new AbstractMap.SimpleEntry<>(Pattern.compile("приложение(\\s|$)"), DocumentType.ANNEX),
            new AbstractMap.SimpleEntry<>(Pattern.compile("утвержден(\\s|$)"), DocumentType.CHARTER)
    );

    private static List<String> possibleSubDocuments = List.of("приложение");
    private static Pattern tableOfContentDocPattern = Pattern.compile("PAGEREF _Toc\\d+");
    private static Pattern alphabetPattern = Pattern.compile("[A-Za-zА-Яа-я0-9]{5,}");
    private static Pattern alphabetUpperCasePattern = Pattern.compile("[A-ZА-Я]{5,}");
    private static Pattern ruAlphabetPattern = Pattern.compile("[А-Яа-я]{5,}");
    private static Pattern engAlphabetPattern = Pattern.compile("[A-Za-z]{5,}");
    private static Pattern styleNamePattern = Pattern.compile("title|heading|заголовок");
    private static Pattern valuableSymbolPattern = Pattern.compile("[A-Za-zА-Яа-я]");
    final private static int maxHeaderLength = 1000;
    final private static int maxBodyLength = 100000;
    final private static int firstParagraphBodyCheckLength = 200;
    private static String version;

    static{
        ZipSecureFile.setMinInflateRatio(0.0001d);
        MavenXpp3Reader reader = new MavenXpp3Reader();
        Model model;
        try {
            if ((new File("pom.xml")).exists())
                model = reader.read(new FileReader("pom.xml"));
            else
                model = reader.read(new InputStreamReader(DocumentParser.class.getResourceAsStream(
                                        "/META-INF/maven/com.nemo.document.audit/document-parser/pom.xml")));
            version = model.getVersion();
        }
        catch (IOException | XmlPullParserException ex){
            logger.error("project pom.xml not found or can't be parsed.", ex);
        }
    }

    public static MultiDocumentStructure parse(String filePath) throws IOException {
        String extension = filePath.substring(filePath.lastIndexOf(".") + 1).toUpperCase();
        return parse(new FileInputStream(new File(filePath)), DocumentFileType.valueOf(extension));
    }

    public static MultiDocumentStructure parse(InputStream inputStream, DocumentFileType documentFileType) throws IOException {
        try {
            long startTime = System.currentTimeMillis();
            MultiDocumentStructure result = new MultiDocumentStructure();
            result.setVersion(version);
            DocumentStructure documentResult = new DocumentStructure();
            result.addDocument(documentResult);
            com.nemo.document.parser.Paragraph currentParagraph = null;
            boolean isPrevHeader = false;
            switch (documentFileType) {
                case DOC:
                    HWPFDocument doc = new HWPFDocument(inputStream);
                    Range range = doc.getRange();
                    TableIterator tableIterator = new TableIterator(range);
                    List<InternalTable> tables = getTablesAndParagraphs(tableIterator);
                    int paragraphQuantity = range.numParagraphs();
                    for (int i = 0; i < paragraphQuantity; i++) {
                        Paragraph paragraph = range.getParagraph(i);
                        String paragraphText = paragraph.text().endsWith("\r") ? paragraph.text().substring(0, paragraph.text().length() - 1) :
                                paragraph.text();
                        if (documentResult.getParagraphs().size() != 0 || !paragraphText.trim().isEmpty()) {
                            if (isTableOfContent(paragraphText)) {
                                isPrevHeader = false;
                                continue;
                            }
//                        StyleDescription styleDescription = doc.getStyleSheet().getStyleDescription(paragraph.getStyleIndex());
                            if ((result.getDocuments().size() == 1 && documentResult.getParagraphs().size() == 0) ||
                                    isHeader(paragraph, tables)) {
                                if (isPrevHeader) {
                                    currentParagraph.getParagraphHeader().addText(paragraphText);
                                } else {
                                    currentParagraph = new com.nemo.document.parser.Paragraph();
                                    documentResult.addParagraph(currentParagraph);
                                    currentParagraph.setParagraphHeader(new TextSegment(paragraph.getStartOffset(), paragraphText));
                                }
                                isPrevHeader = true;
                            } else {
                                if (documentResult.getParagraphs().size() == 0) { //page break, but no header detected
                                    documentResult = result.getDocuments().get(result.getDocuments().size() - 2);
                                    result.getDocuments().remove(result.getDocuments().size() - 1);
                                }
                                if (currentParagraph == null) {
                                    currentParagraph = new com.nemo.document.parser.Paragraph();
                                    documentResult.addParagraph(currentParagraph);
                                }
                                if (currentParagraph.getParagraphBody().getOffset() == -1) {
                                    currentParagraph.setParagraphBody(new TextSegment(paragraph.getStartOffset(), paragraphText));
                                } else {
                                    currentParagraph.getParagraphBody().addText(paragraphText);
                                }
                                isPrevHeader = false;
                            }
                        }
                        if (documentResult.getParagraphs().size() != 0 && isSubDocument(paragraph, tables)) {
                            documentResult = new DocumentStructure();
                            result.addDocument(documentResult);
                            isPrevHeader = false;
                        }
                    }
                    break;
                case DOCX:
                    XWPFDocument docx = new XWPFDocument(inputStream);
                    List<IBodyElement> elements = docx.getBodyElements();
                    int globalOffset = 0;
                    for (IBodyElement element : elements) {
                        Triple<Boolean, com.nemo.document.parser.Paragraph, Integer> elementResult =
                                processBodyElement(element, currentParagraph, isPrevHeader, globalOffset, result, true);
                        isPrevHeader = elementResult.getLeft();
                        currentParagraph = elementResult.getMiddle();
                        globalOffset = elementResult.getRight();
                    }
                    break;
            }
            checkDocumentStructure(result);
            for (DocumentStructure documentStructure : result.getDocuments()) {
                if (documentStructure.getParagraphs().size() > 0) {
                    documentStructure.setDocumentType(findDocumentType(documentStructure));
                    documentStructure.setDocumentDate(findDocumentDate(documentStructure));
                    if (documentStructure.getDocumentType() != DocumentType.CHARTER) {
                        documentStructure.setDocumentNumber(findDocumentNumber(documentStructure));
                    }
                }
            }
            logger.info("Document processed successfully. Time spent {}ms", System.currentTimeMillis() - startTime);
            return result;
        }
        finally {
            inputStream.close();
        }
    }

    private static void checkDocumentStructure(MultiDocumentStructure multiDoc){
        for(Iterator<DocumentStructure> iterator = multiDoc.getDocuments().iterator(); iterator.hasNext();) {
            DocumentStructure documentStructure = iterator.next();
            if(documentStructure.getParagraphs().size() == 0){
                iterator.remove();
                continue;
            }
            for (int i = 0; i < documentStructure.getParagraphs().size(); i++) {
                com.nemo.document.parser.Paragraph paragraph = documentStructure.getParagraphs().get(i);
                if (paragraph.getParagraphHeader().getLength() > maxHeaderLength) {
                    String longHeader = paragraph.getParagraphHeader().getText();
                    Pattern pattern = Pattern.compile("\r|\n");
                    Matcher matcher = pattern.matcher(longHeader);
                    if (matcher.find()) {
                        String shortHeader = longHeader.substring(0, matcher.start());
                        String newBody = longHeader.substring(matcher.start()) + paragraph.getParagraphBody().getText();
                        paragraph.setParagraphHeader(new TextSegment(paragraph.getParagraphHeader().getOffset(), shortHeader));
                        paragraph.setParagraphBody(new TextSegment(paragraph.getParagraphHeader().getOffset() + paragraph.getParagraphHeader().getLength(),
                                newBody));
                    } else {
                        logger.warn("Paragraph header is too large. Paragraph number={}, header length={}", i, paragraph.getParagraphBody().getLength());
                    }
                }
                if (paragraph.getParagraphBody().getLength() > maxBodyLength) {
                    logger.warn("Paragraph body is too large. Paragraph number={}, body length={}", i, paragraph.getParagraphBody().getLength());
                }
            }
        }
    }

    private static String findDocumentNumber(DocumentStructure document){
        String result = "";
        int offset = 0;
        String text = "";
        for(com.nemo.document.parser.Paragraph paragraph : document.getParagraphs()) {
            if (paragraph.getParagraphHeader() != null) {
                Matcher matcher = documentNumberPattern.matcher(paragraph.getParagraphHeader().getText());
                if (matcher.find()) {
                    result = matcher.group("number");
                    offset += matcher.start();
                    text = matcher.group();
                }
            }
            if(!result.isEmpty() || paragraph.getParagraphBody().getText().trim().length() != 0){
                break;
            }
            else{
                offset += paragraph.getParagraphBody().getLength() + paragraph.getParagraphHeader().getLength();
            }
        }
        if(result.isEmpty()){
            offset = -1;
        }
        document.setDocumentNumberSegment(new TextSegment(offset, text));
        return result;
    }

    private static LocalDate findDocumentDate(DocumentStructure document){
        LocalDate result = null;
        int offset = 0;
        String text = "";
        for(com.nemo.document.parser.Paragraph paragraph : document.getParagraphs()) {
            if (paragraph.getParagraphHeader() != null) {
                String firstHeader = paragraph.getParagraphHeader().getText();
                Matcher matcher = datePattern.matcher(firstHeader.toLowerCase());
                if (matcher.find()) {
                    result = parseDate(matcher);
                    offset += matcher.start();
                    text = matcher.group();
                } else {
                    if (paragraph.getParagraphBody() != null) {
                        String firstParagraphBody = paragraph.getParagraphBody().getText()
                                .substring(0, Math.min(firstParagraphBodyCheckLength, paragraph.getParagraphBody().getLength()));
                        matcher = datePattern.matcher(firstParagraphBody.toLowerCase());
                        if (matcher.find()) {
                            result = parseDate(matcher);
                            offset += matcher.start();
                            text = matcher.group();
                        }
                    }
                }
            }
            if(result != null || paragraph.getParagraphBody().getText().trim().length() != 0){
                break;
            }
            else{
                offset += paragraph.getParagraphBody().getLength() + paragraph.getParagraphHeader().getLength();
            }
        }
        if(result == null){
            offset = -1;
        }
        document.setDocumentDateSegment(new TextSegment(offset, text));
        return result;
    }

    private static DocumentType findDocumentType(DocumentStructure document){
        DocumentType result = DocumentType.UNKNOWN;
        for(com.nemo.document.parser.Paragraph paragraph : document.getParagraphs()) {
            if (paragraph.getParagraphHeader() != null) {
                int firstOccurrence = paragraph.getParagraphHeader().getLength();
                for (AbstractMap.Entry<Pattern, DocumentType> entry : keyToDocType.entrySet()) {
                    Matcher matcher = entry.getKey().matcher(paragraph.getParagraphHeader().getText().toLowerCase());
                    if (matcher.find()) {
                        if (firstOccurrence > matcher.start()) {
                            result = entry.getValue();
                            firstOccurrence = matcher.start();
                        }
                    }
                }
            }
            if(result != DocumentType.UNKNOWN || paragraph.getParagraphBody().getText().trim().length() != 0){
                break;
            }
        }
        return result;
    }

    private static Triple<Boolean, com.nemo.document.parser.Paragraph, Integer>
        processBodyElement(IBodyElement element, com.nemo.document.parser.Paragraph currentParagraph, boolean isPrevHeader,
                       int globalOffset, MultiDocumentStructure result, boolean canBeHeader){
        DocumentStructure documentStructure = result.getDocuments().get(result.getDocuments().size() - 1);
        if(element.getElementType() == BodyElementType.CONTENTCONTROL){
            return new ImmutableTriple<>(isPrevHeader, currentParagraph, globalOffset);
        }
        if(element.getElementType() == BodyElementType.TABLE){
            XWPFTable table = (XWPFTable)element;
            int prevNumCells = -1;
            boolean constantColumnNumber = true;
            boolean bilingual = false;
            for(XWPFTableRow row : table.getRows()){
                if(constantColumnNumber && prevNumCells != -1 &&
                        prevNumCells != row.getTableCells().size() && row.getTableCells().size() != 0){
                    constantColumnNumber = false;
                }
                if(row.getTableCells().size() != 0) {
                    prevNumCells = row.getTableCells().size();
                }
            }
            if(constantColumnNumber && prevNumCells == 2){
                for (XWPFTableRow row : table.getRows()) {
                    StringBuilder cell1Text = new StringBuilder("");
                    StringBuilder cell2Text = new StringBuilder("");
                    for (XWPFParagraph paragraph : row.getTableCells().get(0).getParagraphs()) {
                        cell1Text.append(paragraph.getText());
                    }
                    for (XWPFParagraph paragraph : row.getTableCells().get(1).getParagraphs()) {
                        cell2Text.append(paragraph.getText());
                    }
                    if(cell1Text.toString().trim().length() > 0 && cell2Text.toString().trim().length() > 0) {
                        bilingual = isBilingual(cell1Text.toString(), cell2Text.toString());
                        break;
                    }
                }
            }
            for(XWPFTableRow row : table.getRows()){
                canBeHeader = row.getTableCells().size() == 1 || (bilingual);
                for(XWPFTableCell cell : row.getTableCells()){
                    for(IBodyElement bodyElement : cell.getBodyElements()){
                        Triple<Boolean, com.nemo.document.parser.Paragraph, Integer> elementResult =
                                processBodyElement(bodyElement, currentParagraph, isPrevHeader, globalOffset, result, canBeHeader);
                        isPrevHeader = elementResult.getLeft();
                        currentParagraph = elementResult.getMiddle();
                        globalOffset = elementResult.getRight();
                        canBeHeader = isPrevHeader || documentStructure.getParagraphs().size() == 0;
                    }
                }
            }
        }
        if(element.getElementType() == BodyElementType.PARAGRAPH) {
            XWPFParagraph paragraph = (XWPFParagraph)element;
            Pair<Boolean, com.nemo.document.parser.Paragraph> paragrapResult =
                    processXWPFParagraph(paragraph, currentParagraph, isPrevHeader, globalOffset, result, canBeHeader);
            isPrevHeader = paragrapResult.getLeft();
            currentParagraph = paragrapResult.getRight();
            globalOffset += paragraph.getText().length();
        }
        return new ImmutableTriple<>(isPrevHeader, currentParagraph, globalOffset);
    }

    private static Pair<Boolean, com.nemo.document.parser.Paragraph>
        processXWPFParagraph(XWPFParagraph paragraph, com.nemo.document.parser.Paragraph currentParagraph,
        boolean isPrevHeader, int globalOffset, MultiDocumentStructure result, boolean canBeHeader){
        DocumentStructure documentStructure = result.getDocuments().get(result.getDocuments().size() - 1);
        if(documentStructure.getParagraphs().size() != 0 && isSubDocument(paragraph)){
            documentStructure = new DocumentStructure();
            result.addDocument(documentStructure);
            isPrevHeader = false;
        }
        if (documentStructure.getParagraphs().size() != 0 || !paragraph.getText().trim().isEmpty()) {
            if(isTableOfContent(paragraph)){
                return new ImmutablePair<>(false, currentParagraph);
            }
            if ((result.getDocuments().size() == 1 && documentStructure.getParagraphs().size() == 0) ||
                    (canBeHeader && isHeader(paragraph, null))) {
                if (isPrevHeader) {
                    currentParagraph.getParagraphHeader().addText(paragraph.getText());
                } else {
                    currentParagraph = new com.nemo.document.parser.Paragraph();
                    documentStructure.addParagraph(currentParagraph);
                    currentParagraph.setParagraphHeader(new TextSegment(globalOffset, paragraph.getText()));
                }
                return new ImmutablePair<>(true, currentParagraph);
            } else {
                if(documentStructure.getParagraphs().size() == 0){ //page break, but no header detected
                    documentStructure = result.getDocuments().get(result.getDocuments().size() - 2);
                    result.getDocuments().remove(result.getDocuments().size() - 1);
                }
                if (currentParagraph == null) {
                    currentParagraph = new com.nemo.document.parser.Paragraph();
                    documentStructure.addParagraph(currentParagraph);
                }
                if (currentParagraph.getParagraphBody().getOffset() == -1) {
                    currentParagraph.setParagraphBody(new TextSegment(globalOffset, paragraph.getText()));
                } else {
                    currentParagraph.getParagraphBody().addText(paragraph.getText());
                }
                return new ImmutablePair<>(false, currentParagraph);
            }
        }
        return new ImmutablePair<>(isPrevHeader, currentParagraph);
    }

    private static boolean isSubDocument(Paragraph paragraph, List<InternalTable> tables){
        if(paragraph.pageBreakBefore() || paragraph.text().contains("\f")){
            return true;
        }
        if(isHeader(paragraph, tables)){
            String lowerCaseText = paragraph.text().toLowerCase();
            for(String possibleSubDocHeader : possibleSubDocuments){
                if(lowerCaseText.contains(possibleSubDocHeader)){
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isSubDocument(XWPFParagraph paragraph){
        if(paragraph.isPageBreak()){
            return true;
        }
        if(paragraph.getCTP().getPPr() != null && paragraph.getCTP().getPPr().getSectPr() != null &&
                paragraph.getCTP().getPPr().getSectPr().isSetPgSz()){
            return true;
        }
        for(XWPFRun run : paragraph.getRuns()){
            for(CTBr ctbr : run.getCTR().getBrList()){
                if(ctbr.getType() != null && ctbr.getType().intValue() == STBrType.PAGE.intValue()){
                    return true;
                }
            }
        }
        if(isHeader(paragraph, null)){
            String lowerCaseText = paragraph.getText().toLowerCase();
            for(String possibleSubDocHeader : possibleSubDocuments){
                if(lowerCaseText.contains(possibleSubDocHeader)){
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isTableOfContent(String paragraphText){
        return tableOfContentDocPattern.matcher(paragraphText).find();
    }

    private static boolean isTableOfContent(XWPFParagraph paragraph){
        StringBuilder instrSB = new StringBuilder();
        for(CTR ctr : paragraph.getCTP().getRList()){
            for(CTText cttext : ctr.getInstrTextList()){
                instrSB.append(cttext.getStringValue());
            }
        }
        return tableOfContentDocPattern.matcher(instrSB.toString()).find();
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

    private static boolean isBilingual(String text1, String text2){
        Matcher matcher1 = ruAlphabetPattern.matcher(text1);
        Matcher matcher2 = engAlphabetPattern.matcher(text2);
        if(matcher1.find() && matcher2.find()){
            return true;
        }
        matcher1 = ruAlphabetPattern.matcher(text2);
        matcher2 = engAlphabetPattern.matcher(text1);
        return matcher1.find() && matcher2.find();
    }

    private static List<InternalTable> getTablesAndParagraphs(TableIterator tableIterator){
        List<InternalTable> result = new ArrayList<>();
        while(tableIterator.hasNext()){
            Table table = tableIterator.next();
            Paragraph[][][] tbl = new Paragraph[table.numRows()][][];
            InternalTable internalTable = new InternalTable();
            internalTable.content = tbl;
            result.add(internalTable);
            int prevNumCells = -1;
            for(int row = 0; row < table.numRows(); row++){
                tbl[row] = new Paragraph[table.getRow(row).numCells()][];
                if(internalTable.constantColumnNumber && prevNumCells != -1 &&
                        prevNumCells != table.getRow(row).numCells() && table.getRow(row).numCells() != 0){
                    internalTable.constantColumnNumber = false;
                }
                if(table.getRow(row).numCells() != 0) {
                    prevNumCells = table.getRow(row).numCells();
                }
                for(int column = 0; column < table.getRow(row).numCells(); column++){
                    tbl[row][column] = new Paragraph[table.getRow(row).getCell(column).numParagraphs()];
                    for(int paragraphNumber = 0; paragraphNumber < table.getRow(row).getCell(column).numParagraphs(); paragraphNumber++){
                        tbl[row][column][paragraphNumber] = table.getRow(row).getCell(column).getParagraph(paragraphNumber);
                    }
                }
            }
            if(internalTable.constantColumnNumber && prevNumCells == 2){
                for (int row = 0; row < internalTable.content.length; row++) {
                    StringBuilder cell1Text = new StringBuilder("");
                    StringBuilder cell2Text = new StringBuilder("");
                    for (int i = 0; i < internalTable.content[0][0].length; i++) {
                        cell1Text.append(internalTable.content[0][0][i]);
                    }
                    for (int i = 0; i < internalTable.content[0][1].length; i++) {
                        cell2Text.append(internalTable.content[0][1][i]);
                    }
                    if(cell1Text.toString().trim().length() > 0 && cell2Text.toString().trim().length() > 0) {
                        internalTable.bilingual = isBilingual(cell1Text.toString(), cell2Text.toString());
                        break;
                    }
                }
            }
        }
        return result;
    }

    private static boolean isSameParagraph(Paragraph paragraph1, Paragraph paragraph2){
        return paragraph1.getStartOffset() == paragraph2.getStartOffset() && paragraph1.getEndOffset() == paragraph2.getEndOffset();
    }

    private static Triple<Integer, Integer, Integer> getTableCoords(List<InternalTable> tables, Paragraph paragraph){
        for(int tbl = 0; tbl < tables.size(); tbl++) {
            for (int row = 0; row < tables.get(tbl).content.length; row++) {
                for (int column = 0; column < tables.get(tbl).content[row].length; column++) {
                    for (int paragraphNumber = 0; paragraphNumber < tables.get(tbl).content[row][column].length; paragraphNumber++) {
                        if (isSameParagraph(tables.get(tbl).content[row][column][paragraphNumber], paragraph)) {
                            return new ImmutableTriple<>(tbl, row, column);
                        }
                    }
                }
            }
        }
        return null;
    }

    private static boolean isHeader(Paragraph paragraph, List<InternalTable> tables){
        Matcher matcher = alphabetPattern.matcher(paragraph.text());
        if(!matcher.find()){
            return false;
        }

//        String styleName = document.getStyleSheet().getStyleDescription(paragraph.getStyleIndex()).getName().toLowerCase();
//        Matcher styleNameMatcher = styleNamePattern.matcher(styleName);
//        if(styleNameMatcher.lookingAt()){
//            return true;
//        }

        if(paragraph.isInTable()){
            Triple<Integer, Integer, Integer> tableCoords = getTableCoords(tables, paragraph);
            if(tableCoords != null && tables.get(tableCoords.getLeft()).content[tableCoords.getMiddle()].length > 1 &&
                    !(tables.get(tableCoords.getLeft()).bilingual)) {
                return false;
            }
        }

        int alignment = paragraph.getFontAlignment();
        int justification = paragraph.getJustification();
        if(alignment == 3 || justification == 1 || justification == 2){
            return true;
        }
        int characterRunQuantity = paragraph.numCharacterRuns();
        boolean allCharactersBold = true;
        boolean allCharactersCapitalized = true;
        for(int j = 0; j < characterRunQuantity; j++) {
            if(!allCharactersBold && !allCharactersCapitalized){
                break;
            }
            CharacterRun characterRun = paragraph.getCharacterRun(j);
            if(!characterRun.text().trim().isEmpty()) {
                String upperCaseRun = characterRun.text().toUpperCase();
                matcher = alphabetUpperCasePattern.matcher(upperCaseRun);
                if (!characterRun.text().equals(upperCaseRun) || !matcher.find()) {
                    allCharactersCapitalized = false;
                }
                if (!characterRun.isBold()) {
                    allCharactersBold = false;
                }
            }
        }
        return allCharactersBold || allCharactersCapitalized;
    }

    private static boolean isHeader(XWPFParagraph paragraph, List<XWPFParagraph> excludeParagraphs){
        Matcher matcher = alphabetPattern.matcher(paragraph.getText());
        if(!matcher.find()){
            return false;
        }


        if(excludeParagraphs != null && excludeParagraphs.contains(paragraph)){
            return false;
        }

        ParagraphAlignment alignment = paragraph.getAlignment();
        if(alignment.equals(ParagraphAlignment.CENTER) || alignment.equals(ParagraphAlignment.RIGHT)){
            return true;
        }
        boolean paragraphBold = false;
        if(paragraph.getStyleID() != null) {
            XWPFStyle style = paragraph.getDocument().getStyles().getStyle(paragraph.getStyleID());
//            if (style != null && style.getCTStyle() != null && style.getCTStyle().getPPr() != null &&
//                    style.getCTStyle().getPPr().getJc() != null) {
//                String aligmentValue = style.getCTStyle().getPPr().getJc().getVal().toString().toLowerCase();
//                if (aligmentValue.equals("center") || aligmentValue.equals("right")) {
//                    return true;
//                }
//            }
            CTRPr cTRPr = style.getCTStyle().getRPr();
            if (cTRPr != null) {
                if (!cTRPr.isSetB()) {
                    paragraphBold = false;
                } else {
                    STOnOff.Enum val = cTRPr.getB().getVal();
                    paragraphBold = !((STOnOff.FALSE == val) || (STOnOff.X_0 == val) || (STOnOff.OFF == val));
                }
            }

        }
        if(paragraphBold){
            return true;
        }

        List<XWPFRun> runs = paragraph.getRuns();
        boolean allCharactersBold = true;
        boolean allCharactersCapitalized = true;
        String upperCaseParagraph = paragraph.getText().toUpperCase();
        matcher = alphabetUpperCasePattern.matcher(upperCaseParagraph);
        if (!paragraph.getText().equals(upperCaseParagraph) || !matcher.find()) {
            allCharactersCapitalized = false;
        }
        for(XWPFRun run : runs) {
            if(!allCharactersBold && !allCharactersCapitalized){
                break;
            }
            if(!run.text().trim().isEmpty()) {
                matcher = valuableSymbolPattern.matcher(run.text());
                if (!isBold(run) && matcher.find()) {
                    allCharactersBold = false;
                }
            }
        }
        return allCharactersBold || allCharactersCapitalized;
    }

    private static boolean isBold(XWPFRun run){
        boolean isRBold = false;
        CTRPr cTRPr = run.getCTR().getRPr();
        if (cTRPr != null) {
            CTString rStyle = cTRPr.getRStyle();
            if (rStyle != null) {
                String rStyleId = rStyle.getVal();
                XWPFStyle style = run.getDocument().getStyles().getStyle(rStyleId);
                if (style != null) {
                    cTRPr = style.getCTStyle().getRPr();
                    if (cTRPr != null) {
                        if (!cTRPr.isSetB()) {
                            isRBold = false;
                        } else {
                            STOnOff.Enum val = cTRPr.getB().getVal();
                            isRBold = !((STOnOff.FALSE == val) || (STOnOff.X_0 == val) || (STOnOff.OFF == val));
                        }
                    }
                }
            }
        }

        cTRPr = run.getCTR().getRPr();
        if (cTRPr != null) {
            if (cTRPr.isSetB()) {
                STOnOff.Enum val = cTRPr.getB().getVal();
                isRBold = !((STOnOff.FALSE == val) || (STOnOff.X_0 == val) || (STOnOff.OFF == val));
            }
        }
        return isRBold;
    }

    private static class InternalTable{
        Paragraph[][][] content;
        boolean constantColumnNumber = true;
        boolean bilingual = false;
    }
}
