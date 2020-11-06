package com.nemo.document.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemo.document.parser.web.DocumentResponse;
import org.apache.commons.io.FileUtils;
import org.springframework.http.HttpEntity;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ConclusionGeneratorClient {
    final private static URI uri = URI.create("http://localhost:8889/document-generator/conclusion");
    final private static HttpClient client = HttpClient.newHttpClient();

    public static void main(String[] args) {
        callDocParser("C:\\work\\tmp\\out.docx");
    }

    private static void callDocParser(String filePath) {
        try {
            byte[] fileContent = FileUtils.readFileToByteArray(new File("C:\\work\\tmp\\logo.png"));
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            var values = new HashMap<String, String>() {{
                put("base64Logo", Base64.getEncoder().encodeToString(fileContent));
                put("subsidiaryName", "Некое ДО");
                put("auditDate", dateFormat.format(new Date()));
                put("riskMatrix", null);
                put("orgLevels", null);
                put("violations", null);
            }};

            var objectMapper = new ObjectMapper();
            String requestBody = objectMapper
                    .writeValueAsString(values);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("Content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            String jsonString = response.body();
            ObjectMapper mapper = new ObjectMapper();
            DocumentResponse documentResponse = mapper.readValue(jsonString, DocumentResponse.class);
            FileUtils.writeByteArrayToFile(new File(filePath), Base64.getDecoder().decode(documentResponse.getBase64Document()));
        }
        catch (Throwable th){
            th.printStackTrace();
        }
    }
}
