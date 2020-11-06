package com.nemo.document.parser;

import org.apache.poi.sl.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Color;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.rmi.server.ExportException;
import java.text.SimpleDateFormat;
import java.util.Base64;

public class ConclusionGenerator {
    private static Logger logger = LoggerFactory.getLogger(ConclusionGenerator.class);
    private static SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");

    public static byte[] generate(ConclusionRequest conclusionRequest) throws IOException {
        XWPFDocument document = new XWPFDocument();
        try {
            createFrontPage(document, conclusionRequest);
            createTableOfContent(document);
            createIntro(document);
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

    private static void createIntro(XWPFDocument document){
        XWPFParagraph paragraph = document.createParagraph();
        addRun("Вводная часть", paragraph, true);
        generateWordContent(document, StaticText.introText);
        document.createParagraph().createRun().addBreak(BreakType.PAGE);
    }

    private static void createTableOfContent(XWPFDocument document){
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
