package com.nemo.document.parser;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class ConclusionGenerator {
    public static byte[] generate(ConclusionRequest conclusionRequest) throws IOException {
        XWPFDocument document = new XWPFDocument();
        XWPFParagraph tmpParagraph = document.createParagraph();
        XWPFRun tmpRun = tmpParagraph.createRun();
        tmpRun.setText("Conclusion test");
        tmpRun.setFontSize(18);
        try(ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            document.write(baos);
            document.close();
            return baos.toByteArray();
        }
    }
}
