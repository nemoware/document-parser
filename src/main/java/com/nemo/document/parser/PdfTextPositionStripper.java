package com.nemo.document.parser;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PdfTextPositionStripper extends PDFTextStripper {

    private boolean startOfLine = true;
    private ArrayList<TextLine> lines = null;

    public PdfTextPositionStripper() throws IOException
    {
    }

    @Override
    protected void startPage(PDPage page) throws IOException
    {
        startOfLine = true;
        super.startPage(page);
    }

    @Override
    protected void writeLineSeparator() throws IOException
    {
        startOfLine = true;
        super.writeLineSeparator();
    }

    @Override
    public String getText(PDDocument doc) throws IOException
    {
        lines = new ArrayList<TextLine>();
        return super.getText(doc);
    }

    @Override
    protected void writeWordSeparator() throws IOException
    {
        TextLine tmpline = null;

        tmpline = lines.get(lines.size() - 1);
        tmpline.setText(tmpline.getText() + getWordSeparator());

        super.writeWordSeparator();
    }


    @Override
    protected void writeString(String text, List<TextPosition> textPositions) throws IOException
    {
        TextLine tmpline = null;

        if (startOfLine) {
            tmpline = new TextLine();
            tmpline.setText(text);
            tmpline.setTextPositions(textPositions);
            lines.add(tmpline);
        } else {
            tmpline = lines.get(lines.size() - 1);
            tmpline.setText(tmpline.getText() + text);
            tmpline.getTextPositions().addAll(textPositions);
        }

        if (startOfLine)
        {
            startOfLine = false;
        }
        super.writeString(text, textPositions);
    }

    public ArrayList<TextLine> getLines() {
        return lines;
    }
}
