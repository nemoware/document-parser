package com.nemo.document.parser.web;

import com.nemo.document.parser.*;
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
    public MultiDocumentStructure getDocumentStructureByPath(@RequestParam(name="filePath") String filePath) throws IOException {
        String fullPath = new File(fileRootPath, filePath).getAbsolutePath();
//        try {
            return DocumentParser.parse(fullPath);
//        }
//        catch(IOException ex){
//            logger.error("File=" + fullPath + " not found.");
//            throw new RuntimeException("error");
//        }
    }

    @PostMapping("/document-parser")
    @ResponseBody
    public MultiDocumentStructure getDocumentStructureByContent(@RequestBody DocumentParserRequest request) throws IOException{
        byte[] decodedBytes = Base64.getDecoder().decode(request.getBase64Content());
        return DocumentParser.parse(new ByteArrayInputStream(decodedBytes), DocumentFileType.valueOf(request.getDocumentFileType()));
    }

    @PostMapping("/document-generator/conclusion")
    @ResponseBody
    public DocumentResponse getConclusionDocument(@RequestBody ConclusionRequest conclusionRequest) throws IOException{
        byte[] document = ConclusionGenerator.generate(conclusionRequest);
        return new DocumentResponse(Base64.getEncoder().encodeToString(document));
    }
}
