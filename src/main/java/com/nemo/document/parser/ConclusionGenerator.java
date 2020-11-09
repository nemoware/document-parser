package com.nemo.document.parser;

import org.apache.poi.sl.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Color;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.*;

public class ConclusionGenerator {
    private static Logger logger = LoggerFactory.getLogger(ConclusionGenerator.class);
    private static SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");
    private static Map<String, String> violation2RiskMatrixMapping = new HashMap<String, String>() {{
        put("Протокол не найден", "Отсутствует одобрение Общего собрания участников Общества/Совета директоров на совершение cделки");
    }};
    private static BigInteger listId = BigInteger.ZERO;

    public static byte[] generate(ConclusionRequest conclusionRequest) throws IOException {
        XWPFDocument document = new XWPFDocument();
        try {
            if(conclusionRequest.getViolations() == null){
                conclusionRequest.setViolations(new Violation[0]);
            }
            if(conclusionRequest.getOrgLevels() == null){
                conclusionRequest.setOrgLevels(new String[0]);
            }
            if(conclusionRequest.getRiskMatrix() == null){
                conclusionRequest.setRiskMatrix(new RiskMatrixRow[0]);
            }

            Set<RiskMatrixRow> applicableRisks = new HashSet<>();
            for(Violation violation : conclusionRequest.getViolations()){
                for(Map.Entry<String, String> entry : violation2RiskMatrixMapping.entrySet()){
                    if(violation.violationType.startsWith(entry.getKey())) {
                        for(RiskMatrixRow riskMatrixRow : conclusionRequest.riskMatrix){
                            if(riskMatrixRow.violation.equals(entry.getValue())){
                                applicableRisks.add(riskMatrixRow);
                            }
                        }
                    }
                }
            }

            createFrontPage(document, conclusionRequest);
            createTableOfContent(document);
            createIntro(document);
            createShortSummary(document, applicableRisks);
            XWPFRun run = document.createParagraph().createRun();
            run.setFontSize(14);
            run.setFontFamily("Arial");
            run.setBold(true);
            run.setText("Полный отчет");
            createCorporateStructure(document);
            createResults(document, conclusionRequest);
            createRisks(document, applicableRisks);
        }
        catch (Exception ex){
            logger.error("Error: ", ex);
        }

        try(ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            document.write(baos);
            document.close();
            return baos.toByteArray();
        }
    }

    private static void createRisks(XWPFDocument document, Set<RiskMatrixRow> applicableRisks){
        addParagraph("Риски", document, true);
        BigInteger numId = createList(document, "%1.");
        XWPFParagraph paragraph;
        for(RiskMatrixRow riskMatrixRow : applicableRisks){
            paragraph = document.createParagraph();
            paragraph.setNumID(numId);
            addRun(riskMatrixRow.getRisk(), paragraph);
        }
    }

    private static void createResults(XWPFDocument document, ConclusionRequest conclusionRequest){
        addParagraph("Результаты проверки документов КН на предмет наличия/отсутствия корпоративных одобрений и их достоверности", document, true);
        String replacedText = StaticText.resultStart;
        if(conclusionRequest.getAuditStart() != null && conclusionRequest.getAuditEnd() != null) {
            replacedText = replacedText.replace("<<audit_period>>", "с " + dateFormat.format(conclusionRequest.getAuditStart()) + " по " + dateFormat.format(conclusionRequest.getAuditEnd()));
        }
        generateWordContent(document, replacedText);
        addParagraph("", document);
        XWPFTable table = document.createTable(conclusionRequest.violations.length + 1, 4);
        addRun("Учредительный документ", table.getRow(0).getCell(0).addParagraph(), true);
        addRun("Подпункт, пункт, статья", table.getRow(0).getCell(1).addParagraph(), true);
        addRun("Нарушение", table.getRow(0).getCell(2).addParagraph(), true);
        addRun("Основание нарушения", table.getRow(0).getCell(3).addParagraph(), true);
        for(Violation violation : conclusionRequest.getViolations()){
            addRun(violation.getFoundingDocument(), table.getRow(0).getCell(0).addParagraph(), true);
            addRun(violation.getReference(), table.getRow(0).getCell(1).addParagraph(), true);
            addRun(violation.getViolationType(), table.getRow(0).getCell(2).addParagraph(), true);
            addRun(violation.getViolationReason(), table.getRow(0).getCell(3).addParagraph(), true);
        }
        addParagraph("", document);

        addParagraph(StaticText.resultEnd, document);
        addParagraph("", document);
    }

    private static void createCorporateStructure(XWPFDocument document){
        addParagraph("Текущая корпоративная структура и управление КН", document, true);
        addParagraph("", document);
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
        }
        cTLvl.addNewLvlText().setVal(format);
        XWPFAbstractNum abstractNum = new XWPFAbstractNum(cTAbstractNum);
        XWPFNumbering numbering = document.createNumbering();
        BigInteger abstractNumID = numbering.addAbstractNum(abstractNum);
        BigInteger numID = numbering.addNum(abstractNumID);
        return numID;
    }

    private static void createShortSummary(XWPFDocument document, Set<RiskMatrixRow> applicableRisks){
        addParagraph("Краткие выводы", document, true);
        addParagraph(StaticText.shortSummaryText, document);

        addParagraph("Сильные стороны", document, true);
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setNumID(createList(document, "•"));
        addRun(StaticText.strongSides, paragraph);

        addParagraph("Недостатки", document, true);
        BigInteger numId = createList(document, "•");
        for(RiskMatrixRow riskMatrixRow : applicableRisks){
            paragraph = document.createParagraph();
            paragraph.setNumID(numId);
            addRun(riskMatrixRow.disadvantage, paragraph);
        }

        addParagraph("", document);

        addParagraph("Рекомендации по усовершенствованию системы корпоративного управления КН, как инструмента повышения общеуправленческой эффективности:", document, true);
        numId = createList(document, "%1)");
        for(RiskMatrixRow riskMatrixRow : applicableRisks){
            paragraph = document.createParagraph();
            paragraph.setNumID(numId);
            addRun(riskMatrixRow.recommendation, paragraph);
        }

        document.createParagraph().createRun().addBreak(BreakType.PAGE);
    }

    private static void createIntro(XWPFDocument document){
        XWPFParagraph paragraph = document.createParagraph();
        addRun("Вводная часть", paragraph, true);
        generateWordContent(document, StaticText.introText);
        document.createParagraph().createRun().addBreak(BreakType.PAGE);
    }

    private static void createTableOfContent(XWPFDocument document){
        addParagraph("Оглавление", document, true);
//        CTSdtBlock block = getDocument().getBody().addNewSdt();
//        TOC toc = new TOC(block);
//        for (XWPFParagraph par : this.paragraphs) {
//            String parStyle = par.getStyle();
//            if ((parStyle != null) && (parStyle.startsWith("Heading"))) try {
//                int level = Integer.valueOf(parStyle.substring("Heading".length())).intValue();
//                toc.addRow(level, par.getText(), 1, "112723803");
//            } catch (NumberFormatException e) {
//                e.printStackTrace();
//            }
//        }
//        document.createTOC();
//        XWPFParagraph paragraph = document.createParagraph();
//        CTP ctP = paragraph.getCTP();
//        CTSimpleField toc = ctP.addNewFldSimple();
//        toc.setInstr("TOC \\h");
//        toc.setDirty(STOnOff.TRUE);
        document.createParagraph().createRun().addBreak(BreakType.PAGE);
    }

    private static void createFrontPage(XWPFDocument document, ConclusionRequest conclusionRequest) throws Exception{
        byte[] logo = Base64.getDecoder().decode(conclusionRequest.getBase64Logo());
        XWPFTable table = document.createTable(1, 2);
        table.removeBorders();
        XWPFTableRow tableRow = table.getRow(0);
        XWPFTableCell cell = tableRow.getCell(0);

        XWPFParagraph paragraph = cell.addParagraph();
        XWPFRun run = paragraph.createRun();
        XWPFPicture picture = run.addPicture(new ByteArrayInputStream(logo), Document.PICTURE_TYPE_PNG, "", Units.toEMU(200), Units.toEMU(145));

        paragraph = tableRow.getCell(1).addParagraph();
        paragraph.setAlignment(ParagraphAlignment.RIGHT);
        addRun("УТВЕРЖДАЮ:", paragraph, true);
        paragraph = tableRow.getCell(1).addParagraph();
        paragraph.setAlignment(ParagraphAlignment.RIGHT);
        addRun("______________________", paragraph);
        paragraph = tableRow.getCell(1).addParagraph();
        paragraph.setAlignment(ParagraphAlignment.RIGHT);
        addRun("", paragraph);
        paragraph = tableRow.getCell(1).addParagraph();
        paragraph.setAlignment(ParagraphAlignment.RIGHT);
        addRun("Заместитель генерального директора", paragraph);
        paragraph = tableRow.getCell(1).addParagraph();
        paragraph.setAlignment(ParagraphAlignment.RIGHT);
        addRun("по правовым и корпоративным вопросам", paragraph);
        paragraph = tableRow.getCell(1).addParagraph();
        paragraph.setAlignment(ParagraphAlignment.RIGHT);
        addRun("", paragraph);
        paragraph = tableRow.getCell(1).addParagraph();
        paragraph.setAlignment(ParagraphAlignment.RIGHT);
        addRun("Е.А. Илюхина", paragraph);
        addParagraph("", document);
        addParagraph("", document);

        table = document.createTable(6, 1);
        table.removeBorders();
        cell = table.getRow(0).getCell(0);
        paragraph = cell.addParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        addRun("Блок правовых и корпоративных вопросов ПАО «Газпром нефть»", paragraph);
        paragraph = cell.addParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        addRun("ДЕПАРТАМЕНТ КОРПОРАТИВНОГО РЕГУЛИРОВАНИЯ", paragraph);
        cell = table.getRow(1).getCell(0);
        paragraph = cell.addParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        run = paragraph.createRun();
        run.setFontFamily("Cambria");
        run.setFontSize(40);
        run.setText("«" + conclusionRequest.subsidiaryName + "»");
        CTTc ctTc = cell.getCTTc();
        CTTcPr tcPr = ctTc.addNewTcPr();
        CTTcBorders border = tcPr.addNewTcBorders();
        CTBorder ctBorder = border.addNewBottom();
        ctBorder.setVal(STBorder.SINGLE);
        ctBorder.setColor("2196F3");
        cell = table.getRow(2).getCell(0);
        paragraph = cell.addParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        addRun("Отчет по результатам аудита практики корпоративного управления", paragraph);
        cell = table.getRow(5).getCell(0);
        paragraph = cell.addParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        run = paragraph.createRun();
        run.setFontFamily("Arial");
        run.setFontSize(12);
        run.setBold(true);
        run.setItalic(true);
        run.setText(dateFormat.format(conclusionRequest.auditDate));
        run = document.createParagraph().createRun();
        run.addBreak(BreakType.PAGE);
    }

    private static void generateWordContent(XWPFDocument document, String text){
        String[] paragraphTexts = text.split("\n");
        for (String paragraphText : paragraphTexts){
            addParagraph(paragraphText, document);
        }
    }

    private static void addParagraph(String text, XWPFDocument document, boolean bold){
        XWPFParagraph paragraph = document.createParagraph();
        addRun(text, paragraph, bold);
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
}
