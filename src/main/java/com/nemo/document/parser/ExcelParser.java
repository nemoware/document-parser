package com.nemo.document.parser;

import com.nemo.document.parser.web.BeneficiaryChain;
import com.nemo.document.parser.web.StakeholderResponse;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.codehaus.plexus.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExcelParser {
    private static Logger logger = LoggerFactory.getLogger(ExcelParser.class);

    private static String dateRegEx = "(?<day>[1-2][0-9]|3[01]|0?[1-9])?\\.?\\s*(?<month>1[0-2]|0[1-9]|январь|февраль|март|апрель|май|июнь|июль|август|сентябрь|октябрь|ноябрь|декабрь).\\.?\\s*(?<year>[1-2]\\d{3})";
    private static Pattern datePattern = Pattern.compile(dateRegEx, Pattern.CASE_INSENSITIVE);
    private static Pattern nameColumnTitlePattern = Pattern.compile("^наименование\\s*(компании)?$");
    private static Pattern reasonColumnTitlePattern = Pattern.compile("основани[ея]");
    private static Pattern yearColumnTitlePattern = Pattern.compile("дата\\s*наступления\\s*основания");
    private static String[] months = {"январь", "февраль", "март", "апрель", "май", "июнь", "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь"};
    private static Pattern namePattern = Pattern.compile("^(?<name>.*)\\(\\s*сокращенное\\s*-?\\s*(?<shortName>.*)\\)$");
    private static Pattern reasonPattern = Pattern.compile("[1-9][0-9]\\.|[1-9]\\.(?<reason>((?!([1-9][0-9]\\.|[1-9]\\.)).)*)");
    private static Pattern shortNamePattern = Pattern.compile("(?<person>(([А-Я]\\.\\s*){2}[А-Я][а-я]+(-[А-Я][а-я]+)?)|([А-Я][а-я]+(-[А-Я][а-я]+)?\\s*([А-Я]\\.\\s*){2}))");
    private static Pattern yearPattern = Pattern.compile("(?<year>[0-9]{4})");
    private static Pattern namePersonColumnTitlePattern = Pattern.compile("наименование.+фио");

    public static StakeholderResponse parseStakeholderDocument(String filePath) throws IOException {
        String extension = filePath.substring(filePath.lastIndexOf(".") + 1).toUpperCase();
        return parseStakeholderDocument(new FileInputStream(new File(filePath)), DocumentFileType.valueOf(extension));
    }

    public static StakeholderResponse parseStakeholderDocument(InputStream inputStream, DocumentFileType documentFileType) throws IOException {
        try (inputStream) {
            switch (documentFileType) {
                case XLS:
                    return parseStakeholderWorkbook(new HSSFWorkbook(inputStream));
                case XLSX:
                    return parseStakeholderWorkbook(new XSSFWorkbook(inputStream));
            }
        }
        return null;
    }

    public static BeneficiaryChain parseBeneficiaries(String filePath) throws IOException{
        String extension = filePath.substring(filePath.lastIndexOf(".") + 1).toUpperCase();
        return parseBeneficiaries(new FileInputStream(new File(filePath)), DocumentFileType.valueOf(extension));
    }

    public static BeneficiaryChain parseBeneficiaries(InputStream inputStream, DocumentFileType documentFileType) throws IOException{
        try (inputStream) {
            switch (documentFileType) {
                case XLS:
                    return parseBeneficiaryWorkbook(new HSSFWorkbook(inputStream));
                case XLSX:
                    return parseBeneficiaryWorkbook(new XSSFWorkbook(inputStream));
            }
        }
        return null;
    }

    private static StakeholderResponse parseStakeholderWorkbook(Workbook workbook){
        StakeholderResponse result = new StakeholderResponse();
        result.setVersion(DocumentParser.getVersion());
        Iterator<Sheet> sheetIterator = workbook.sheetIterator();
        while(sheetIterator.hasNext()) {
            Sheet sheet = sheetIterator.next();
            LocalDate sheetDate = parseDateFromSheetName(sheet.getSheetName());
            StakeholderList stakeholderList = new StakeholderList();
            result.getSheets().add(stakeholderList);
            stakeholderList.setDate(sheetDate);
            Iterator<Row> rowIterator = sheet.rowIterator();
            Integer nameColumnIndex = null;
            Integer reasonColumnIndex = null;
            Integer yearColumnIndex = null;
            while (rowIterator.hasNext()){
                Row row = rowIterator.next();
                Stakeholder stakeholder = new Stakeholder();
                boolean emptyStakeholder = true;
                Iterator<Cell> cellIterator = row.cellIterator();
                while (cellIterator.hasNext()){
                    Cell cell = cellIterator.next();
//                    logger.info(cell.getRowIndex() + " " + cell.getColumnIndex());
                    if (cell.getCellType() == CellType.STRING) {
                        String cellValue = cell.getStringCellValue().trim();
                        Matcher matcher = nameColumnTitlePattern.matcher(cellValue.toLowerCase());
                        if (nameColumnIndex == null && matcher.find()) {
                            nameColumnIndex = cell.getColumnIndex();
                            continue;
                        }
                        matcher = reasonColumnTitlePattern.matcher(cellValue.toLowerCase());
                        if (reasonColumnIndex == null && matcher.find()) {
                            reasonColumnIndex = cell.getColumnIndex();
                            continue;
                        }
                        matcher = yearColumnTitlePattern.matcher(cellValue.toLowerCase());
                        if (yearColumnIndex == null && matcher.find()) {
                            yearColumnIndex = cell.getColumnIndex();
                            continue;
                        }
                        if (nameColumnIndex != null && cell.getColumnIndex() == nameColumnIndex) {
                            emptyStakeholder = false;
                            matcher = namePattern.matcher(cellValue);
                            if (matcher.find()){
                                stakeholder.setName(matcher.group("name").trim());
                                stakeholder.setShortName(matcher.group("shortName").trim());
                            }
                            else {
                                stakeholder.setName(cellValue);
                            }
                        }
                        if (reasonColumnIndex != null && cell.getColumnIndex() == reasonColumnIndex) {
                            emptyStakeholder = false;
                            stakeholder.setReasons(parseReasons(cellValue));
                        }
                        if (yearColumnIndex != null && yearColumnIndex == cell.getColumnIndex()){
                            emptyStakeholder = false;
                            matcher = yearPattern.matcher(cellValue);
                            if (matcher.find()){
                                stakeholder.setYear(Integer.parseInt(matcher.group("year")));
                            }
                        }
                    }
                }
                if (!emptyStakeholder){
                    stakeholderList.getStakeholders().add(stakeholder);
                }
            }
        }
        return result;
    }

    private static BeneficiaryChain parseBeneficiaryWorkbook(Workbook workbook){
        BeneficiaryChain result = new BeneficiaryChain();
        result.setVersion(DocumentParser.getVersion());
        Iterator<Sheet> sheetIterator = workbook.sheetIterator();
        while(sheetIterator.hasNext()) {
            Sheet sheet = sheetIterator.next();
            Iterator<Row> rowIterator = sheet.rowIterator();
            Integer nameColumnIndex = null;
            Integer namePersonColumnIndex = null;
            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                Beneficiary beneficiary = new Beneficiary();
                boolean emptyBeneficiary = true;
                Iterator<Cell> cellIterator = row.cellIterator();
                while (cellIterator.hasNext()) {
                    Cell cell = cellIterator.next();
                    if (cell.getCellType() == CellType.STRING) {
                        String cellValue = cell.getStringCellValue().trim();
                        Matcher matcher = nameColumnTitlePattern.matcher(cellValue.toLowerCase());
                        if (nameColumnIndex == null && matcher.find()) {
                            nameColumnIndex = cell.getColumnIndex();
                            continue;
                        }
                        matcher = namePersonColumnTitlePattern.matcher(cellValue.toLowerCase());
                        if (namePersonColumnIndex == null && matcher.find()) {
                            namePersonColumnIndex = cell.getColumnIndex();
                            continue;
                        }
                        if (nameColumnIndex != null && cell.getColumnIndex() == nameColumnIndex) {
                            emptyBeneficiary = false;
                            beneficiary.setName(cellValue);
                        }
                        if (namePersonColumnIndex != null && cell.getColumnIndex() == namePersonColumnIndex) {
                            emptyBeneficiary = false;
                            beneficiary.setNamePerson(cellValue);
                        }
                    }
                }
                if (!emptyBeneficiary){
                    result.getBenefeciaries().add(beneficiary);
                }
            }
        }
        return result;
    }

    private static LocalDate parseDateFromSheetName(String sheetName){
        Matcher matcher = datePattern.matcher(sheetName.toLowerCase());
        LocalDate result = null;
        if (matcher.find()) {
            String day = matcher.group("day");
            String month = matcher.group("month");
            String year = matcher.group("year");
            if (day == null){
                day = "01";
            }
            if (!StringUtils.isNumeric(month)){
                result = LocalDate.of(Integer.parseInt(year), monthAsNumber(month), Integer.parseInt(day));
            }
            else {
                result = LocalDate.of(Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day));
            }
        }
        return result;
    }

    private static int monthAsNumber(String  month) {
        for (int i = 0; i < months.length; i++) {
            if (months[i].equalsIgnoreCase(month)) {
                return i + 1;
            }
        }
        return -1; // no match
    }

    private static boolean isEmpty(Cell cell){
        switch (cell.getCellType()){
            case BLANK:
            case ERROR:
                return true;
            case STRING:
                return cell.getStringCellValue().isEmpty();
        }
        return false;
    }

    private static List<Reason> parseReasons(String text){
        Matcher matcher = reasonPattern.matcher(text);
        List<Reason> result = new ArrayList<>();
        boolean list = false;
        while (matcher.find()){
            list = true;
            Reason reason = new Reason();
            result.add(reason);
            String reasonText = matcher.group("reason");
            if (reasonText != null) {
                reason.setText(reasonText.trim());
                Matcher shortNameMatcher = shortNamePattern.matcher(reason.getText());
                while (shortNameMatcher.find()){
                    reason.getPersons().add(shortNameMatcher.group("person"));
                }
            }
        }
        if (!list){
            Reason reason = new Reason();
            result.add(reason);
            reason.setText(text);
            matcher = shortNamePattern.matcher(text);
            while (matcher.find()){
                reason.getPersons().add(matcher.group("person"));
            }
        }
        return result;
    }
}
