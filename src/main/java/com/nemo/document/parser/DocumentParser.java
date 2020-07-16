package com.nemo.document.parser;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.model.ListData;
import org.apache.poi.hwpf.model.ListLevel;
import org.apache.poi.hwpf.usermodel.*;
import org.apache.poi.hwpf.usermodel.Paragraph;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.xwpf.usermodel.*;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DocumentParser {
    private static String dateRegEx = "(?<day>[1-2][0-9]|3[01]|0?[1-9]).\\s*(?<month>1[0-2]|0[1-9]|января|февраля|марта|апреля|мая|июня|июля|августа|сентября|октября|ноября|декабря).\\s*(?<year>[1-2]\\d{3})";
    private static Pattern datePattern = Pattern.compile(dateRegEx, Pattern.CASE_INSENSITIVE);
    private static Pattern documentNumberPattern = Pattern.compile("№[ \\t]*(?<number>\\S+)(\\s+|$)");
    private static Pattern documentNumberValidationPattern = Pattern.compile("([A-Za-zА-Яа-я0-9]+)");
    private static Logger logger = LoggerFactory.getLogger(DocumentParser.class);
    private static     String[] shortMonths = {
            "янв", "фев", "мар", "апр", "ма", "июн",
            "июл", "авг", "сен", "окт", "ноя", "дек"};
    private static Map<Pattern, DocumentType> keyToDocType = Map.ofEntries(
            new AbstractMap.SimpleEntry<>(Pattern.compile("договор([^А-Яа-я]|$)"), DocumentType.CONTRACT),
            new AbstractMap.SimpleEntry<>(Pattern.compile("у *с *т *а *в([^А-Яа-я]|$)"), DocumentType.CHARTER),
            new AbstractMap.SimpleEntry<>(Pattern.compile("протокол([^А-Яа-я]|$)"), DocumentType.PROTOCOL),
            new AbstractMap.SimpleEntry<>(Pattern.compile("положение([^А-Яа-я]|$)"), DocumentType.REGULATION),
//            new AbstractMap.SimpleEntry<>(Pattern.compile("благотворител"), DocumentType.CHARITY_POLICY),
            new AbstractMap.SimpleEntry<>(Pattern.compile("приказ([^А-Яа-я]|$)"), DocumentType.ORDER),
            new AbstractMap.SimpleEntry<>(Pattern.compile("план работ([^А-Яа-я]|$)"), DocumentType.WORK_PLAN),
            new AbstractMap.SimpleEntry<>(Pattern.compile("дополнительное\\s+соглашение([^А-Яа-я]|$)"), DocumentType.SUPPLEMENTARY_AGREEMENT),
            new AbstractMap.SimpleEntry<>(Pattern.compile("приложение([^А-Яа-я]|$)"), DocumentType.ANNEX),
            new AbstractMap.SimpleEntry<>(Pattern.compile("контракт([^А-Яа-я]|$)"), DocumentType.CONTRACT),
            new AbstractMap.SimpleEntry<>(Pattern.compile("решение([^А-Яа-я]|$)"), DocumentType.PROTOCOL),
            new AbstractMap.SimpleEntry<>(Pattern.compile("соглашение([^А-Яа-я]|$)"), DocumentType.AGREEMENT)
    );

    private static List<Pattern> possibleSubDocuments = List.of(Pattern.compile("^\\s*приложение"),
            Pattern.compile("^\\s*дополнительное\\s+соглашение"));
    private static Pattern tableOfContentDocPattern = Pattern.compile("PAGEREF _Toc\\d+");
    private static Pattern alphabetPattern = Pattern.compile("[A-Za-zА-Яа-я0-9]{5,}");
    private static Pattern alphabetUpperCasePattern = Pattern.compile("[A-ZА-Я]{5,}");
    private static Pattern ruAlphabetPattern = Pattern.compile("[А-Яа-я]{5,}");
    private static Pattern engAlphabetPattern = Pattern.compile("[A-Za-z]{5,}");
    private static Pattern styleNamePattern = Pattern.compile("title|heading|заголовок");
    private static Pattern valuableSymbolPattern = Pattern.compile("[A-Za-zА-Яа-я]");
    private static Pattern endStringPattern = Pattern.compile("\r|\n");
    private static Pattern notHeaderPattern = Pattern.compile("решение +принято");
    final private static int maxHeaderLength = 1000;
    final private static int maxBodyLength = 100000;
    final private static int firstParagraphBodyCheckLength = 200;
    final private static int emptyParagraphs4PageBreakSimulation = 1;
    final private static float minHeaderIndentationLeft = 0.25f;
    final private static int maxDocTypeDetectionHeaders = 5;
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

    public static String getVersion(){
        return version;
    }

    public static MultiDocumentStructure parse(String filePath) throws IOException {
        String extension = filePath.substring(filePath.lastIndexOf(".") + 1).toUpperCase();
        return parse(new FileInputStream(new File(filePath)), DocumentFileType.valueOf(extension));
    }

    public static MultiDocumentStructure parse(InputStream inputStream, DocumentFileType documentFileType) throws IOException {
        try {
            long startTime = System.currentTimeMillis();
            Map<Integer, ListNumber> listNumbers = new HashMap<>();
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
                    int pageWidth = doc.getSectionTable().getSections().get(0).getSectionProperties().getXaPage();
                    int paragraphQuantity = range.numParagraphs();
                    for (int i = 0; i < paragraphQuantity; i++) {
                        Paragraph paragraph = range.getParagraph(i);
                        String paragraphText = paragraph.text().endsWith("\r") ? paragraph.text().substring(0, paragraph.text().length() - 1) :
                                paragraph.text();
                        if (isSubDocument(paragraph, tables, documentResult, pageWidth)) {
//                            int idx = paragraph.text().lastIndexOf("\f");
//                            if(idx > 0 && paragraphText.length() != 0 && currentParagraph != null){
//                                String fromPreviousParagraph = paragraph.text().substring(0, idx);
//                                if(currentParagraph.getParagraphBody().getLength() != 0) {
//                                    currentParagraph.getParagraphBody().addText(fromPreviousParagraph);
//                                }
//                                else{
//                                    currentParagraph.getParagraphHeader().addText(fromPreviousParagraph);
//                                }
//                            }
                            documentResult = new DocumentStructure();
                            result.addDocument(documentResult);
                            isPrevHeader = false;
                        }
                        if (documentResult.getParagraphs().size() != 0 || !paragraphText.trim().isEmpty()) {
                            if (isTableOfContent(paragraphText)) {
                                isPrevHeader = false;
                                continue;
                            }
                            String paragraphPrefix = "";
                            if(paragraph.getIlfo() != 0){
                                ListData listdata = doc.getListTables().getListData(paragraph.getList().getLsid());
                                ListLevel[] listLevel = listdata.getLevels();
                                ListLevel level = listLevel[paragraph.getIlvl()];
                                int numberFormat = level.getNumberFormat();
                                ListNumber rootListNumber = listNumbers.get(paragraph.getList().getLsid());
                                if(rootListNumber == null){
                                    rootListNumber = new ListNumber(0, "" + listLevel[0].getNumberFormat());
                                    rootListNumber.overrideNumber(listLevel[0].getStartAt());
                                    listNumbers.put(paragraph.getList().getLsid(), rootListNumber);
                                 }
                                ListNumber currentListNumber = rootListNumber;
                                for(int l = 1; l <= paragraph.getIlvl(); l++){
                                    if(currentListNumber.getSubNumber() == null){
                                        currentListNumber.setSubNumber(new ListNumber(l, "" + listLevel[l].getNumberFormat()));
                                        if(paragraph.getIlvl() != l) {
                                            currentListNumber.getSubNumber().overrideNumber(listLevel[l].getStartAt());
                                        }
                                    }
                                    currentListNumber = currentListNumber.getSubNumber();
                                }
                                if(level.getStartAt() > currentListNumber.getNumber()){
                                    currentListNumber.overrideNumber(level.getStartAt());
                                }
                                else{
                                    currentListNumber.incrementNumber();
                                }
                                if(numberFormat == 23){//bullet format
                                    paragraphPrefix = "•";
                                }
                                else {
                                    paragraphPrefix = level.getNumberText();
                                    ListNumber listNumber = rootListNumber;
                                    for (int l = 0; l <= currentListNumber.getLevel(); l++) {
                                        paragraphPrefix = paragraphPrefix.replace(Character.toString((char)l), Integer.toString(listNumber.getNumber()));
                                        listNumber = listNumber.getSubNumber();
                                    }
                                }
                            }
                            paragraphText = paragraphPrefix + " " + paragraphText;
//                        StyleDescription styleDescription = doc.getStyleSheet().getStyleDescription(paragraph.getStyleIndex());
                            if ((result.getDocuments().size() == 1 && documentResult.getParagraphs().size() == 0) ||
                                    isHeader(paragraph, tables, pageWidth)) {
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
                    }
                    break;
                case DOCX:
                    XWPFDocument docx = new XWPFDocument(inputStream);
                    List<IBodyElement> elements = docx.getBodyElements();
                    ElementResult elementResult = new ElementResult(isPrevHeader, false, currentParagraph, 0, 0);
                    CanBeHeader canBeHeader = CanBeHeader.CAN;
                    List<String> styleChain = new ArrayList<>();
                    for (IBodyElement element : elements) {
                        elementResult = processBodyElement(element, elementResult, result, canBeHeader, listNumbers, styleChain);
                        canBeHeader = elementResult.isPageBreak ? CanBeHeader.MUST : CanBeHeader.CAN;
                    }
                    break;
            }
            checkDocumentStructure(result);
            for (DocumentStructure documentStructure : result.getDocuments()) {
                if (documentStructure.getParagraphs().size() > 0) {
                    findDocumentType(documentStructure);
//                    findDocumentDate(documentStructure);
//                    if (documentStructure.getDocumentType() != DocumentType.CHARTER) {
//                        findDocumentNumber(documentStructure);
//                    }
                }
            }
            postProcessDocument(result);
            logger.info("Document processed successfully. Time spent {}ms", System.currentTimeMillis() - startTime);
            return result;
        }
        finally {
            inputStream.close();
        }
    }

    private static void checkDocumentStructure(MultiDocumentStructure multiDoc){
        for(Iterator<DocumentStructure> documentIterator = multiDoc.getDocuments().iterator(); documentIterator.hasNext();) {
            DocumentStructure documentStructure = documentIterator.next();
            if(documentStructure.getParagraphs().size() == 0){
                documentIterator.remove();
                continue;
            }
            com.nemo.document.parser.Paragraph previousParagraph = null;
            for (Iterator<com.nemo.document.parser.Paragraph> paragraphIterator = documentStructure.getParagraphs().iterator(); paragraphIterator.hasNext();) {
                com.nemo.document.parser.Paragraph paragraph = paragraphIterator.next();
                if (paragraph.getParagraphHeader().getLength() > maxHeaderLength) {
                    String longHeader = paragraph.getParagraphHeader().getText();
                    Matcher matcher = endStringPattern.matcher(longHeader);
                    if (matcher.find()) {
                        String shortHeader = longHeader.substring(0, matcher.start());
                        String newBody = longHeader.substring(matcher.start()) + paragraph.getParagraphBody().getText();
                        paragraph.setParagraphHeader(new TextSegment(paragraph.getParagraphHeader().getOffset(), shortHeader));
                        paragraph.setParagraphBody(new TextSegment(paragraph.getParagraphHeader().getOffset() + paragraph.getParagraphHeader().getLength(),
                                newBody));
                    }
                }

                if(paragraph.getParagraphHeader().getText().trim().isEmpty()){
                    if(!paragraph.getParagraphBody().getText().trim().isEmpty() && previousParagraph != null){
                        previousParagraph.getParagraphBody().addText(paragraph.getParagraphBody().getText());
                    }
                    paragraphIterator.remove();
                }
                else{
                    previousParagraph = paragraph;
                }
            }
        }
    }

    private static void postProcessDocument(MultiDocumentStructure multiDocumentStructure){
        for(DocumentStructure documentStructure : multiDocumentStructure.getDocuments()){
            if(documentStructure.getDocumentType() == DocumentType.PROTOCOL) {
                com.nemo.document.parser.Paragraph previousParagraph = null;
                for (Iterator<com.nemo.document.parser.Paragraph> paragraphIterator = documentStructure.getParagraphs().iterator(); paragraphIterator.hasNext(); ) {
                    com.nemo.document.parser.Paragraph paragraph = paragraphIterator.next();
                    Matcher matcher = notHeaderPattern.matcher(paragraph.getParagraphHeader().getText().toLowerCase());
                    if (matcher.find() && previousParagraph != null) {
                        previousParagraph.getParagraphBody().addText(paragraph.getParagraphHeader().getText());
                        previousParagraph.getParagraphBody().addText(paragraph.getParagraphBody().getText());
                        paragraphIterator.remove();
                    }
                    else {
                        previousParagraph = paragraph;
                    }
                }
            }
        }
    }

//    private static void findDocumentNumber(DocumentStructure document){
//        String result = "";
//        int offset = 0;
//        String text = "";
//        for(com.nemo.document.parser.Paragraph paragraph : document.getParagraphs()) {
//            if (paragraph.getParagraphHeader() != null) {
//                Matcher matcher = documentNumberPattern.matcher(paragraph.getParagraphHeader().getText());
//                if (matcher.find()) {
//                    result = matcher.group("number");
//                    offset += matcher.start();
//                    text = matcher.group();
//                }
//            }
//            if(!result.isEmpty() || paragraph.getParagraphBody().getText().trim().length() != 0){
//                break;
//            }
//            else{
//                offset += paragraph.getParagraphBody().getLength() + paragraph.getParagraphHeader().getLength();
//            }
//        }
//        if(result.isEmpty()){
//            offset = -1;
//        }
//        Matcher matcher = documentNumberValidationPattern.matcher(result);
//        if(matcher.find()) {
//            document.setDocumentNumberSegment(new TextSegment(offset, text));
//            document.setDocumentNumber(result);
//        }
//    }
//
//    private static void findDocumentDate(DocumentStructure document){
//        LocalDate result = null;
//        int offset = 0;
//        String text = "";
//        for(com.nemo.document.parser.Paragraph paragraph : document.getParagraphs()) {
//            if (paragraph.getParagraphHeader() != null) {
//                String firstHeader = paragraph.getParagraphHeader().getText();
//                Matcher matcher = datePattern.matcher(firstHeader.toLowerCase());
//                if (matcher.find()) {
//                    result = parseDate(matcher);
//                    offset += matcher.start();
//                    text = matcher.group();
//                } else {
//                    offset += paragraph.getParagraphHeader().getLength();
//                    if (paragraph.getParagraphBody() != null) {
//                        String firstParagraphBody = paragraph.getParagraphBody().getText()
//                                .substring(0, Math.min(firstParagraphBodyCheckLength, paragraph.getParagraphBody().getLength()));
//                        matcher = datePattern.matcher(firstParagraphBody.toLowerCase());
//                        if (matcher.find()) {
//                            result = parseDate(matcher);
//                            offset += matcher.start();
//                            text = matcher.group();
//                        }
//                    }
//                }
//            }
//            if(result != null || paragraph.getParagraphBody().getText().trim().length() != 0){
//                break;
//            }
//            else{
//                offset += paragraph.getParagraphBody().getLength();
//            }
//        }
//        if(result == null){
//            offset = -1;
//        }
//        document.setDocumentDateSegment(new TextSegment(offset, text));
//        document.setDocumentDate(result);
//    }

    private static void findDocumentType(DocumentStructure document){
        DocumentType result = DocumentType.UNKNOWN;
        int firstOccurrence = Integer.MAX_VALUE;
        for(int i = 0; i < document.getParagraphs().size() && i < maxDocTypeDetectionHeaders; i++) {
            com.nemo.document.parser.Paragraph paragraph = document.getParagraphs().get(i);
            if (paragraph.getParagraphHeader() != null) {
                for (AbstractMap.Entry<Pattern, DocumentType> entry : keyToDocType.entrySet()) {
                    Matcher matcher = entry.getKey().matcher(paragraph.getParagraphHeader().getText().toLowerCase());
                    if (matcher.find()) {
                        if ((firstOccurrence > matcher.start() + paragraph.getParagraphHeader().getOffset() && result != DocumentType.CHARTER) || entry.getValue() == DocumentType.CHARTER) {
                            result = entry.getValue();
                            firstOccurrence = matcher.start() + paragraph.getParagraphHeader().getOffset();
                        }
                    }
                }
            }
        }
        document.setDocumentType(result);
    }

    private static ElementResult processBodyElement(IBodyElement element, ElementResult prevElementResult, MultiDocumentStructure result,
                                                    CanBeHeader canBeHeader, Map<Integer, ListNumber> listNumbers, List<String> styleChain){
        DocumentStructure documentStructure = result.getDocuments().get(result.getDocuments().size() - 1);
        if(element.getElementType() == BodyElementType.CONTENTCONTROL){
            return prevElementResult;
        }
        if(element.getElementType() == BodyElementType.TABLE){
            XWPFTable table = (XWPFTable)element;
            if(table.getStyleID() != null) {
                styleChain.add(table.getStyleID());
            }
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
                canBeHeader = row.getTableCells().size() == 1 || bilingual ? CanBeHeader.CAN : CanBeHeader.CAN_NOT;
                for(XWPFTableCell cell : row.getTableCells()){
                    for(IBodyElement bodyElement : cell.getBodyElements()){
                        ElementResult elementResult =
                                processBodyElement(bodyElement, prevElementResult, result, canBeHeader, listNumbers, styleChain);
                        prevElementResult.isPrevHeader = elementResult.isPrevHeader;
                        prevElementResult.currentParagraph = elementResult.currentParagraph;
                        prevElementResult.globalOffset = elementResult.globalOffset;
                        if(elementResult.isPageBreak){
                            canBeHeader = CanBeHeader.MUST;
                        }
                        else {
                            canBeHeader = elementResult.isPrevHeader || documentStructure.getParagraphs().size() == 0 ||
                                    row.getTableCells().size() == 1 || bilingual ? CanBeHeader.CAN : CanBeHeader.CAN_NOT;
                        }
                    }
                }
            }
            if(table.getStyleID() != null) {
                styleChain.remove(styleChain.size() - 1);
            }
        }
        if(element.getElementType() == BodyElementType.PARAGRAPH) {
            XWPFParagraph paragraph = (XWPFParagraph)element;
            ParagraphResult paragraphResult =
                    processXWPFParagraph(paragraph, prevElementResult, result, canBeHeader, listNumbers, styleChain);
            prevElementResult.isPrevHeader = paragraphResult.isPrevHeader;
            prevElementResult.currentParagraph = paragraphResult.currentParagraph;
            prevElementResult.globalOffset += paragraph.getText().length();
        }
        return prevElementResult;
    }

    private static ParagraphResult processXWPFParagraph(XWPFParagraph paragraph, ElementResult prevElementResult,
            MultiDocumentStructure result, CanBeHeader canBeHeader, Map<Integer, ListNumber> listNumbers, List<String> styleCain){
        DocumentStructure documentStructure = result.getDocuments().get(result.getDocuments().size() - 1);
//        if(isPageBreak(paragraph, documentStructure, prevElementResult.emptyParagraphsBefore)){
//            prevElementResult.isPageBreak = true;
//            canBeHeader = CanBeHeader.MUST;
//        }
        if(isSubDocument(paragraph, documentStructure, canBeHeader, styleCain)){
            documentStructure = new DocumentStructure();
            result.addDocument(documentStructure);
            prevElementResult.isPrevHeader = false;
        }
        if(paragraph.getText().trim().isEmpty()){
            prevElementResult.emptyParagraphsBefore++;
        }
        else{
            prevElementResult.emptyParagraphsBefore = 0;
        }
        if (documentStructure.getParagraphs().size() != 0 || !paragraph.getText().trim().isEmpty()) {
            if(isTableOfContent(paragraph)){
                return new ParagraphResult(false, prevElementResult.isPageBreak,
                        prevElementResult.currentParagraph, prevElementResult.emptyParagraphsBefore);
            }
            String paragraphPrefix = getNumberPrefix(paragraph, listNumbers);
            if ((result.getDocuments().size() == 1 && documentStructure.getParagraphs().size() == 0) ||
                    canBeHeader == CanBeHeader.MUST || (canBeHeader != CanBeHeader.CAN_NOT && isHeader(paragraph, null, styleCain))) {
                if (prevElementResult.isPrevHeader) {
                    prevElementResult.currentParagraph.getParagraphHeader().addText(paragraphPrefix + paragraph.getText());
                } else {
                    prevElementResult.currentParagraph = new com.nemo.document.parser.Paragraph();
                    documentStructure.addParagraph(prevElementResult.currentParagraph);
                    prevElementResult.currentParagraph.setParagraphHeader(new TextSegment(prevElementResult.globalOffset, paragraphPrefix + paragraph.getText()));
                }
                if (prevElementResult.currentParagraph.getParagraphHeader().getText().trim().length() != 0){
                    prevElementResult.isPageBreak = false;
                }
                return new ParagraphResult(true, prevElementResult.isPageBreak,
                        prevElementResult.currentParagraph, prevElementResult.emptyParagraphsBefore);
            } else {
                if(documentStructure.getParagraphs().size() == 0){
                    documentStructure = result.getDocuments().get(result.getDocuments().size() - 2);
                    result.getDocuments().remove(result.getDocuments().size() - 1);
                }
                if (prevElementResult.currentParagraph == null) {
                    prevElementResult.currentParagraph = new com.nemo.document.parser.Paragraph();
                    documentStructure.addParagraph(prevElementResult.currentParagraph);
                }
                if (prevElementResult.currentParagraph.getParagraphBody().getOffset() == -1) {
                    prevElementResult.currentParagraph.setParagraphBody(new TextSegment(prevElementResult.globalOffset, paragraphPrefix + paragraph.getText()));
                } else {
                    prevElementResult.currentParagraph.getParagraphBody().addText(paragraphPrefix + paragraph.getText());
                }
                return new ParagraphResult(false, prevElementResult.isPageBreak,
                        prevElementResult.currentParagraph, prevElementResult.emptyParagraphsBefore);
            }
        }
        return new ParagraphResult(prevElementResult.isPrevHeader, prevElementResult.isPageBreak,
                prevElementResult.currentParagraph, prevElementResult.emptyParagraphsBefore);
    }

    private static String getNumberPrefix(XWPFParagraph paragraph, Map<Integer, ListNumber> listNumbers){
        String paragraphPrefix = "";
        if(paragraph.getNumID() != null){
            BigInteger numId = paragraph.getDocument().getNumbering().getAbstractNumID(paragraph.getNumID());
            int numberingId;
            if(numId != null) {
                numberingId = paragraph.getDocument().getNumbering().getAbstractNumID(paragraph.getNumID()).intValue();
            }
            else{
                numberingId = paragraph.getNumID().intValue();
            }
            ListNumber rootListNumber = listNumbers.get(numberingId);
            if(rootListNumber == null){
                rootListNumber = new ListNumber(paragraph.getNumIlvl().intValue(), paragraph.getNumFmt());
                listNumbers.put(numberingId, rootListNumber);
            }
            ListNumber currentListNumber = rootListNumber;
            for(int i = 0; i < paragraph.getNumIlvl().intValue(); i++){
                if(currentListNumber.getSubNumber() == null){
                    currentListNumber.setSubNumber(new ListNumber(i + 1, paragraph.getNumFmt()));
                }
                currentListNumber = currentListNumber.getSubNumber();
            }
            int startNumber = getStartNumber(paragraph);
            if(startNumber > currentListNumber.getNumber()){
                currentListNumber.overrideNumber(startNumber);
            }
            else {
                currentListNumber.incrementNumber();
            }
            if("bullet".equals(paragraph.getNumFmt())){
                paragraphPrefix = "•";
            }
            else {
                paragraphPrefix = paragraph.getNumLevelText();
                if(paragraphPrefix == null){
                    paragraphPrefix = "";
                }
                ListNumber listNumber = rootListNumber;
                for (int i = 1; i <= currentListNumber.getLevel() + 1; i++) {
                    if(listNumber.getNumber() == 0){
                        listNumber.overrideNumber(1, false);
                    }
                    paragraphPrefix = paragraphPrefix.replace("%" + i, Integer.toString(listNumber.getNumber()));
                    listNumber = listNumber.getSubNumber();
                }
            }
        }
        else if(!paragraph.getText().trim().isEmpty() && paragraph.getStyleID() != null){
            CTStyle style = paragraph.getDocument().getStyles().getStyle(paragraph.getStyleID()).getCTStyle();
            if(style != null && style.isSetPPr() && style.getPPr().isSetNumPr() && style.getPPr().getNumPr().isSetNumId()){
                BigInteger abstractNumId = paragraph.getDocument().getNumbering().getAbstractNumID(style.getPPr().getNumPr().getNumId().getVal());
                int listId = abstractNumId.intValue();
                CTAbstractNum abstractNum = paragraph.getDocument().getNumbering().getAbstractNum(abstractNumId).getCTAbstractNum();
                List<CTLvl> levels = abstractNum.getLvlList();
                CTLvl abstractLevel = null;
                for(CTLvl ctLvl : levels){
                    if(ctLvl.getPStyle() != null && paragraph.getStyleID().equals(ctLvl.getPStyle().getVal())) {
                        abstractLevel = ctLvl;
                        break;
                    }
                }
                if(abstractLevel != null) {
                    int listLevel = abstractLevel.getIlvl().intValue();
                    String numberFormat = abstractLevel.getNumFmt().getVal().toString();
                    ListNumber rootListNumber = listNumbers.get(listId);
                    if (rootListNumber == null) {
                        rootListNumber = new ListNumber(listLevel, numberFormat);
                        listNumbers.put(listId, rootListNumber);
                    }
                    ListNumber currentListNumber = rootListNumber;
                    for (int i = 0; i < listLevel; i++) {
                        if (currentListNumber.getSubNumber() == null) {
                            currentListNumber.setSubNumber(new ListNumber(i + 1, numberFormat));
                        }
                        currentListNumber = currentListNumber.getSubNumber();
                    }
                    int startNumber = 0;
                    if(abstractLevel.isSetStart()) {
                        startNumber = abstractLevel.getStart().getVal().intValue();
                    }
                    if (startNumber > currentListNumber.getNumber()) {
                        currentListNumber.overrideNumber(startNumber);
                    } else {
                        currentListNumber.incrementNumber();
                    }
                    if ("bullet".equals(numberFormat)) {
                        paragraphPrefix = "•";
                    } else {
                        paragraphPrefix = "";
                        if (abstractLevel.getLvlText() != null) {
                            paragraphPrefix = abstractLevel.getLvlText().getVal();
                        }
                        ListNumber listNumber = rootListNumber;
                        for (int i = 1; i <= currentListNumber.getLevel() + 1; i++) {
                            if(listNumber.getNumber() == 0){
                                listNumber.overrideNumber(1, false);
                            }
                            paragraphPrefix = paragraphPrefix.replace("%" + i, Integer.toString(listNumber.getNumber()));
                            listNumber = listNumber.getSubNumber();
                        }
                    }
                }
            }
        }
        return paragraphPrefix.isEmpty() ? paragraphPrefix : paragraphPrefix + " ";
    }

    private static int getStartNumber(XWPFParagraph paragraph){
        try {
            XWPFNumbering numbering = paragraph.getDocument().getNumbering();
            if (numbering != null && paragraph.getNumID() != null) {
                XWPFAbstractNum abstractNum = numbering.getAbstractNum(numbering.getAbstractNumID(paragraph.getNumID()));
                if (abstractNum != null && abstractNum.getCTAbstractNum() != null && paragraph.getNumIlvl() != null) {
                    CTLvl lvl = abstractNum.getCTAbstractNum().getLvlArray(paragraph.getNumIlvl().intValue());
                    if (lvl != null && lvl.getStart() != null && lvl.getStart().getVal() != null) {
                        return lvl.getStart().getVal().intValue();
                    }
                }
            }
        }
        catch (Exception ex){
            return 0;
        }
        return 0;
    }

    private static boolean isSubDocument(Paragraph paragraph, List<InternalTable> tables, DocumentStructure documentStructure, int pageWidth){
//        if(paragraph.pageBreakBefore() || (paragraph.text().contains("\f") && !paragraph.text().contains("FORM"))){
//            return true;
//        }
        if(!isAllBodiesEmpty(documentStructure) && isHeader(paragraph, tables, pageWidth)){
            String lowerCaseText = paragraph.text().toLowerCase();
            for(Pattern possibleSubDocHeader : possibleSubDocuments){
                Matcher matcher = possibleSubDocHeader.matcher(lowerCaseText);
                if(matcher.find()){
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isSubDocument(XWPFParagraph paragraph, DocumentStructure documentStructure,
                                         CanBeHeader canBeHeader, List<String> styleChain){
        if(!isAllBodiesEmpty(documentStructure) && (canBeHeader == CanBeHeader.MUST ||
                (canBeHeader == CanBeHeader.CAN && isHeader(paragraph, null, styleChain)))){
            String lowerCaseText = paragraph.getText().toLowerCase();
            for(Pattern possibleSubDocHeader : possibleSubDocuments){
                Matcher matcher = possibleSubDocHeader.matcher(lowerCaseText);
                if(matcher.find()){
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isAllBodiesEmpty(DocumentStructure documentStructure){
        for(com.nemo.document.parser.Paragraph paragraph : documentStructure.getParagraphs()){
            if(paragraph.getParagraphBody().getText().trim().length() > 0){
                return false;
            }
        }
        return true;
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

    private static boolean isHeader(Paragraph paragraph, List<InternalTable> tables, int pageWidth){
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

        if(paragraph.getIndentFromLeft() / (double)pageWidth > minHeaderIndentationLeft){
            return true;
        }

        int alignment = paragraph.getFontAlignment();
        int justification = paragraph.getJustification();
        if(alignment == 3 || justification == 1 || justification == 2){
            return true;
        }

        Matcher matcher = alphabetPattern.matcher(paragraph.text());
        if(!matcher.find()){
            return false;
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

    private static boolean isPageBreak(XWPFParagraph paragraph, DocumentStructure documentStructure, int emptyParagrapshBefore){
        if(documentStructure.getParagraphs().size() != 0) {
            com.nemo.document.parser.Paragraph lastParagraph = documentStructure.getParagraphs().get(documentStructure.getParagraphs().size() - 1);
            if(emptyParagrapshBefore >= emptyParagraphs4PageBreakSimulation){
                return true;
            }
        }
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
        return false;
    }

    private static boolean isHeader(XWPFParagraph paragraph, List<XWPFParagraph> excludeParagraphs, List<String> styleCain){
        if(excludeParagraphs != null && excludeParagraphs.contains(paragraph)){
            return false;
        }

        CTPageSz pageSize = paragraph.getDocument().getDocument().getBody().getSectPr().getPgSz();
        long pageWidth = pageSize.getW().longValue();
        CTPPr ctPPr = paragraph.getCTP().getPPr();
        if(ctPPr != null) {
            CTSectPr sectPr = ctPPr.getSectPr();
            if(sectPr != null && sectPr.getPgSz() != null && sectPr.getPgSz().getW() != null){
                pageWidth = sectPr.getPgSz().getW().longValue();
            }

            if(paragraph.getIndentationLeft() / (double)pageWidth > minHeaderIndentationLeft){
                return true;
            }
        }

        ParagraphAlignment alignment = paragraph.getAlignment();
        if(alignment.equals(ParagraphAlignment.CENTER) || alignment.equals(ParagraphAlignment.RIGHT)){
            return true;
        }

        Matcher matcher = alphabetPattern.matcher(paragraph.getText());
        if(!matcher.find()){
            return false;
        }

        if(paragraph.getStyleID() != null){
            styleCain.add(paragraph.getStyleID());
        }

        boolean paragraphBold = false;
        for(String styleId : styleCain){
            paragraphBold ^= isBold(paragraph.getDocument(), styleId);
        }

        if(paragraph.getStyleID() != null) {
            styleCain.remove(styleCain.size() - 1);
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
                if (!isBold(run, paragraphBold) && matcher.find()) {
                    allCharactersBold = false;
                }
            }
        }
        return allCharactersBold || allCharactersCapitalized;
    }

    private static boolean isBold(XWPFDocument document, String styleID){
        boolean result = false;
        XWPFStyle style = document.getStyles().getStyle(styleID);
        CTRPr cTRPr = style.getCTStyle().getRPr();
        if (cTRPr != null) {
            if (cTRPr.isSetB()) {
                STOnOff.Enum val = cTRPr.getB().getVal();
                result = !((STOnOff.FALSE == val) || (STOnOff.X_0 == val) || (STOnOff.OFF == val));
            }
            else{
                String baseStyleId = style.getBasisStyleID();
                if(baseStyleId != null){
                    result = isBold(document, baseStyleId);
                }
            }
        }
        return result;
    }

    private static boolean isBold(XWPFRun run, boolean paragraphBold){
        boolean isRBold = false;
        boolean styleBold = false;
        CTRPr cTRPr = run.getCTR().getRPr();
        if (cTRPr != null) {
            CTString rStyle = cTRPr.getRStyle();
            if (rStyle != null) {
                String rStyleId = rStyle.getVal();
                styleBold = isBold(run.getDocument(), rStyleId);
            }
        }

        cTRPr = run.getCTR().getRPr();
        if (cTRPr != null) {
            if (cTRPr.isSetB()) {
                STOnOff.Enum val = cTRPr.getB().getVal();
                isRBold = !((STOnOff.FALSE == val) || (STOnOff.X_0 == val) || (STOnOff.OFF == val));
            }
            else{
                isRBold = styleBold ^ paragraphBold;
            }
        }
        else{
            isRBold = styleBold ^ paragraphBold;
        }
        return isRBold;
    }

    private static class InternalTable{
        Paragraph[][][] content;
        boolean constantColumnNumber = true;
        boolean bilingual = false;
    }

    private static class ParagraphResult{
        public ParagraphResult(boolean isPrevHeader, boolean isPageBreak, com.nemo.document.parser.Paragraph currentParagraph, int emptyParagraphsBefore) {
            this.isPrevHeader = isPrevHeader;
            this.isPageBreak = isPageBreak;
            this.currentParagraph = currentParagraph;
            this.emptyParagraphsBefore = emptyParagraphsBefore;
        }

        boolean isPrevHeader;
        boolean isPageBreak;
        com.nemo.document.parser.Paragraph currentParagraph;
        int emptyParagraphsBefore = 0;
    }

    private static class ElementResult extends ParagraphResult{
        public ElementResult(boolean isPrevHeader, boolean isPageBreak, com.nemo.document.parser.Paragraph currentParagraph, int globalOffset, int emptyParagraphsBefore) {
            super(isPrevHeader, isPageBreak, currentParagraph, emptyParagraphsBefore);
            this.globalOffset = globalOffset;
        }

        int globalOffset;
    }
}
