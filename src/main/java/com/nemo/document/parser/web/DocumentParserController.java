package com.nemo.document.parser.web;

import com.nemo.document.parser.DocumentStructure;
import com.nemo.document.parser.DocumentParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Base64;

@Controller
public class DocumentParserController {
    private static Logger logger = LoggerFactory.getLogger(DocumentParserController.class);

    @Value("${root.file.path}")
    String fileRootPath;

    @GetMapping("/document-parser")
    @ResponseBody
    public DocumentStructure getDocumentStructureByPath(@RequestParam(name="filePath") String filePath) {
        String fullPath = new File(fileRootPath, filePath).getAbsolutePath();
        try {
            return DocumentParser.parse(fullPath);
        }
        catch(IOException ex){
            logger.error("File=" + fullPath + " not found.");
            throw new RuntimeException(ex);
        }
    }

    @PostMapping("/document-parser")
    @ResponseBody
    public DocumentStructure getDocumentStructureByContent(@RequestBody DocumentParserRequest request){
        byte[] decodedBytes = Base64.getDecoder().decode(request.getBase64Content());
        try {
            return DocumentParser.parse(new ByteArrayInputStream(decodedBytes), request.getDocumentType());
        }
        catch(IOException ex){
            throw new RuntimeException(ex);
        }
    }
}
