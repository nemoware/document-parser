package com.nemo.document.parser;

import org.apache.poi.xwpf.usermodel.*;
import org.apache.xmlbeans.XmlCursor;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConclusionGenerator {
    private static Logger logger = LoggerFactory.getLogger(ConclusionGenerator.class);
    private static SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");
    private static BigInteger listId = BigInteger.ZERO;
    private static Pattern placeholderPattern = Pattern.compile("\\$\\{(.*?)}");

    public static byte[] generate(ConclusionRequest conclusionRequest) throws IOException {
        byte[] templateBytes = Base64.getDecoder().decode(conclusionRequest.getBase64Template());
        XWPFDocument document = null;

        List<Replace> replaceList = new ArrayList<>();
        List<TableReplace> tableReplaceList = new ArrayList<>();

        try(ByteArrayInputStream bais = new ByteArrayInputStream(templateBytes)) {
            document = new XWPFDocument(bais);
            if(conclusionRequest.getViolations() == null){
                conclusionRequest.setViolations(new Violation[0]);
            }
            if(conclusionRequest.getSubdivisions() == null){
                conclusionRequest.setSubdivisions(new Subdivision[0]);
            }


            Iterator<IBodyElement> bodyElementIterator = document.getBodyElementsIterator();
            List<String> styleChain = new ArrayList<>();
            while (bodyElementIterator.hasNext()) {
                IBodyElement element = bodyElementIterator.next();
                processBodyElement(element, conclusionRequest, styleChain, replaceList, tableReplaceList);
            }

            for(Replace replace: replaceList){
                delayedReplace(replace, null);
            }

            for(TableReplace replace : tableReplaceList){
                delayedTableReplace(replace);
            }

        }
        catch (Exception ex){
            logger.error("Error: ", ex);
        }

        try(ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            if(document != null) {
                document.write(baos);
                document.close();
            }
            return baos.toByteArray();
        }
    }

    private static void processBodyElement(IBodyElement element, ConclusionRequest conclusionRequest, List<String> styleChain, List<Replace> replaceList, List<TableReplace> tableReplaceList){
        if(element.getElementType() == BodyElementType.CONTENTCONTROL){
            return;
        }
        if(element.getElementType() == BodyElementType.TABLE){
            XWPFTable table = (XWPFTable)element;
            if(table.getStyleID() != null) {
                styleChain.add(table.getStyleID());
            }
            String[][] values = null;
            int rowIdx = 0;
            for(XWPFTableRow row : table.getRows()){
                int cellIdx = 0;
                for(XWPFTableCell cell : row.getTableCells()){
                    for(IBodyElement bodyElement : cell.getBodyElements()){
                        if(bodyElement.getElementType() == BodyElementType.PARAGRAPH) {
                            XWPFParagraph paragraph = (XWPFParagraph) bodyElement;
                            String oldText = paragraph.getText();
                            Matcher matcher = placeholderPattern.matcher(oldText);
                            String newText = oldText;
                            if (paragraph.getRuns().size() > 0) {
                                while (matcher.find()) {
                                    String placeholder = matcher.group(1);
                                    if("number".equals(placeholder) || "subdivision.name".equals(placeholder) || "subdivision.address".equals(placeholder)){
                                        if(values == null){
                                            values = new String[conclusionRequest.getSubdivisions().length][table.getRow(0).getTableCells().size()];
                                        }
                                        switch(placeholder){
                                            case "number":
                                                for(int i = 0; i < conclusionRequest.getSubdivisions().length; i++) {
                                                    values[i][cellIdx] = paragraph.getText().replace(getFullPlaceholder("number"), Integer.toString(i + 1));
                                                }
                                                break;
                                            case "subdivision.name":
                                                for(int i = 0; i < conclusionRequest.getSubdivisions().length; i++) {
                                                    values[i][cellIdx] = paragraph.getText().replace(getFullPlaceholder("subdivision.name"), conclusionRequest.getSubdivisions()[i].getName());
                                                }
                                                break;
                                            case "subdivision.address":
                                                for(int i = 0; i < conclusionRequest.getSubdivisions().length; i++) {
                                                    values[i][cellIdx] = paragraph.getText().replace(getFullPlaceholder("subdivision.address"), conclusionRequest.getSubdivisions()[i].getAddress());
                                                }
                                                break;
                                        }
                                    }
                                    else if ("violation.foundingDocument".equals(placeholder) || "violation.reference".equals(placeholder) || "violation.type".equals(placeholder) || "violation.reason".equals(placeholder)){
                                        if(values == null){
                                            values = new String[conclusionRequest.getViolations().length][table.getRow(0).getTableCells().size()];
                                        }
                                        switch(placeholder){
                                            case "violation.foundingDocument":
                                                for(int i = 0; i < conclusionRequest.getViolations().length; i++) {
                                                    if(conclusionRequest.getViolations()[i].getFoundingDocument() == null){
                                                        conclusionRequest.getViolations()[i].setFoundingDocument("");
                                                    }
                                                    values[i][cellIdx] = paragraph.getText().replace(getFullPlaceholder("violation.foundingDocument"), conclusionRequest.getViolations()[i].getFoundingDocument());
                                                }
                                                break;
                                            case "violation.reference":
                                                for(int i = 0; i < conclusionRequest.getViolations().length; i++) {
                                                    if(conclusionRequest.getViolations()[i].getReference() == null){
                                                        conclusionRequest.getViolations()[i].setReference("");
                                                    }
                                                    values[i][cellIdx] = paragraph.getText().replace(getFullPlaceholder("violation.reference"), conclusionRequest.getViolations()[i].getReference());
                                                }
                                                break;
                                            case "violation.type":
                                                for(int i = 0; i < conclusionRequest.getViolations().length; i++) {
                                                    values[i][cellIdx] = paragraph.getText().replace(getFullPlaceholder("violation.type"), conclusionRequest.getViolations()[i].getViolationType());
                                                }
                                                break;
                                            case "violation.reason":
                                                for(int i = 0; i < conclusionRequest.getViolations().length; i++) {
                                                    if(conclusionRequest.getViolations()[i].getViolationReason() == null){
                                                        conclusionRequest.getViolations()[i].setViolationReason("");
                                                    }
                                                    values[i][cellIdx] = paragraph.getText().replace(getFullPlaceholder("violation.reason"), conclusionRequest.getViolations()[i].getViolationReason());
                                                }
                                                break;
                                        }
                                    }
                                }
                            }
                        }
                        processBodyElement(bodyElement, conclusionRequest, styleChain, replaceList, tableReplaceList);
                    }
                    cellIdx++;
                }
                rowIdx++;
            }
            if(values != null) {
                TableReplace tableReplace = new TableReplace();
                tableReplace.table = table;
                tableReplace.values = values;
                tableReplaceList.add(tableReplace);
            }
            if(table.getStyleID() != null) {
                styleChain.remove(styleChain.size() - 1);
            }
        }
        if(element.getElementType() == BodyElementType.PARAGRAPH) {
            XWPFParagraph paragraph = (XWPFParagraph)element;
            String oldText = paragraph.getText();
            Matcher matcher = placeholderPattern.matcher(oldText);
            String newText = oldText;
            if(paragraph.getRuns().size() > 0) {
                while (matcher.find()) {
                    String placeholder = matcher.group(1);
                    switch (placeholder) {
                        case "subsidiaryName":
                            newText = newText.replace(getFullPlaceholder("subsidiaryName"), conclusionRequest.getSubsidiaryName());
                            break;
                        case "currentDate":
                            newText = newText.replace(getFullPlaceholder("currentDate"), dateFormat.format(new Date()));
                            break;
                        case "intro":
                            newText = conclusionRequest.getIntro();
                            break;
                        case "shortSummary":
                            newText = conclusionRequest.getShortSummary();
                            break;
                        case "corporateStructure1":
                            newText = conclusionRequest.getCorporateStructure1();
                            break;
                        case "corporateStructure2":
                            newText = conclusionRequest.getCorporateStructure2();
                            break;
                        case "results1":
                            newText = conclusionRequest.getResults1();
                            break;
                        case "results2":
                            newText = conclusionRequest.getResults2();
                            break;
                        case "results3":
                            newText = conclusionRequest.getResults3();
                            break;
                        case "results4":
                            newText = conclusionRequest.getResults4();
                            break;
                        case "strengths":
                            newText = conclusionRequest.getStrengths();
                            break;
                        case "disadvantages":
                            newText = conclusionRequest.getDisadvantages();
                            break;
                        case "recommendations":
                            newText = conclusionRequest.getRecommendations();
                            break;
                        case "risks":
                            newText = conclusionRequest.getRisks();
                            break;
                        case "legalEntityType":
                            newText = conclusionRequest.getLegalEntityType();
                            break;
//                        default:
//                            logger.warn("Unknown placeholder {}", placeholder);
                    }
                }
                if(!oldText.equals(newText)) {
                    replaceParagraphText(paragraph, newText, replaceList);
                }
            }
        }
    }

    public static void cloneParagraph(XWPFParagraph clone, XWPFParagraph source) {
        CTPPr pPr = clone.getCTP().isSetPPr() ? clone.getCTP().getPPr() : clone.getCTP().addNewPPr();
        pPr.set(source.getCTP().getPPr());
        for (XWPFRun r : source.getRuns()) {
            XWPFRun nr = clone.createRun();
            cloneRun(nr, r);
        }
    }

    public static void cloneRun(XWPFRun clone, XWPFRun source) {
        CTRPr rPr = clone.getCTR().isSetRPr() ? clone.getCTR().getRPr() : clone.getCTR().addNewRPr();
        rPr.set(source.getCTR().getRPr());
        clone.setText(source.getText(0));
    }

    private static String getFullPlaceholder(String placeholderName){
        return "${" + placeholderName + "}";
    }

    private static void delayedReplace(Replace replace, XWPFTableCell cell){
        if("".equals(replace.text.trim())){
            if(cell != null){
                for(XWPFParagraph paragraph : cell.getParagraphs()) {
                    for(XWPFRun run : paragraph.getRuns()){
                        run.setText("", 0);
                    }
                }
            }
            else {
                replace.paragraph.getDocument().removeBodyElement(replace.paragraph.getDocument().getPosOfParagraph(replace.paragraph));
            }
        }
        else {
            XWPFParagraph lastParagraph = replace.paragraph;
            String[] textParagraphs = replace.text.split("\\r?\\n");
            for (int i = 1; i < replace.paragraph.getRuns().size(); i++) {
                replace.paragraph.getRuns().get(i).setText("", 0);
            }
            if (replace.paragraph.getRuns().size() == 0) {
                replace.paragraph.createRun();
            }
            replace.paragraph.getRuns().get(0).setText(textParagraphs[0], 0);

            for (int i = 1; i < textParagraphs.length; i++) {
                XWPFParagraph newParagraph;
                if (cell != null) {
                    newParagraph = cell.addParagraph();
                } else {
                    XmlCursor cursor = lastParagraph.getCTP().newCursor();
                    cursor.toNextSibling();
                    newParagraph = replace.paragraph.getDocument().insertNewParagraph(cursor);
                    lastParagraph = newParagraph;
                }
                cloneParagraph(newParagraph, replace.paragraph);
                for (int j = 1; j < replace.paragraph.getRuns().size(); j++) {
                    replace.paragraph.getRuns().get(j).setText("", 0);
                }
                newParagraph.getRuns().get(0).setText(textParagraphs[i], 0);
            }
        }
    }

    private static void delayedTableReplace(TableReplace replace){
        if(replace.values.length == 0){
            int pos = replace.table.getBody().getXWPFDocument().getPosOfTable(replace.table);
            replace.table.getBody().getXWPFDocument().removeBodyElement(pos);
        }
        else{
            for(int i = 1; i < replace.values.length + 1; i++){
                if(i > 1){
                    XWPFTableRow lastRow = replace.table.getRow(replace.table.getNumberOfRows() - 1);
                    replace.table.addRow(lastRow);
                }
                for(int j = 0; j < replace.values[0].length; j++){
                    Replace paragraphReplace = new Replace();
                    paragraphReplace.paragraph = replace.table.getRow(i).getCell(j).getParagraphArray(0);
                    paragraphReplace.text = replace.values[i - 1][j];
                    for(int p = replace.table.getRow(i).getCell(j).getParagraphs().size() - 1; p > 0; p--) {
                        replace.table.getRow(i).getCell(j).removeParagraph(p);
                    }
                    delayedReplace(paragraphReplace, replace.table.getRow(i).getCell(j));
//                    replace.table.getRow(i).getCell(j).setText(replace.values[i - 1][j]);
                }
            }
            if(replace.table.getNumberOfRows() > 2) {
                XWPFTableRow lastRow = replace.table.getRow(replace.table.getNumberOfRows() - 1);
                replace.table.addRow(lastRow);
                replace.table.removeRow(1);
            }
        }
    }

    private static void replaceParagraphText(XWPFParagraph paragraph, String text, List<Replace> replaceList){
        Replace replace = new Replace();
        replace.paragraph = paragraph;
        replace.text = text;
        replaceList.add(replace);
    }

    private static BigInteger createList(XWPFDocument document, String format){
        CTAbstractNum cTAbstractNum = CTAbstractNum.Factory.newInstance();
        cTAbstractNum.setAbstractNumId(listId);
        listId = listId.add(BigInteger.ONE);
        CTLvl cTLvl = cTAbstractNum.addNewLvl();
        if(format.equals("•")) {
            cTLvl.addNewNumFmt().setVal(STNumberFormat.BULLET);
        }
        if(format.startsWith("%")){
            cTLvl.addNewNumFmt().setVal(STNumberFormat.DECIMAL);
            cTLvl.addNewStart().setVal(BigInteger.valueOf(1));
        }
        cTLvl.addNewLvlText().setVal(format);
        XWPFAbstractNum abstractNum = new XWPFAbstractNum(cTAbstractNum);
        XWPFNumbering numbering = document.createNumbering();
        BigInteger abstractNumID = numbering.addAbstractNum(abstractNum);
        BigInteger numID = numbering.addNum(abstractNumID);
        XWPFNum num = numbering.getNum(numID);
        return numID;
    }

    private static void addParagraph(String text, XWPFDocument document, boolean bold){
        XWPFParagraph paragraph = document.createParagraph();
        addRun(text, paragraph, bold);
        if(bold) paragraph.setStyle("heading 1");
    }

    private static void addParagraph(String text, XWPFDocument document){
        addParagraph(text, document, false);
    }

    private static void addRun(String text, XWPFParagraph paragraph, boolean bold){
        XWPFRun run = paragraph.createRun();
        run.setFontFamily("Arial");
        run.setFontSize(12);
        run.setBold(bold);
        run.setText(text);
    }

    private static void addRun(String text, XWPFParagraph paragraph){
        addRun(text, paragraph, false);
    }

    private static class Replace{
        XWPFParagraph paragraph;
        String text;
    }

    private static class TableReplace{
        XWPFTable table;
        String[][] values;
    }
}
