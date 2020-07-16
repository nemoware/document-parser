package com.nemo.document.parser;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class DocumentParserClient {
    final private static URI uri = URI.create("http://localhost:8889/document-parser");
    final private static HttpClient client = HttpClient.newHttpClient();
    final private static AtomicInteger fileCount = new AtomicInteger(0);

    public static void main(String[] args) {
        if(args.length > 0) {
            try {
                Files.walk(Paths.get(args[0]))
                        .filter(Files::isRegularFile)
                        .filter(path -> {return path.toString().toLowerCase().endsWith(".doc") || path.toString().toLowerCase().endsWith(".docx");})
                        .forEach(DocumentParserClient::callDocParser);
            }
            catch(Throwable ex){
                ex.printStackTrace();
            }
        }
    }

    private static void callDocParser(Path filePath) {
        try {
            var values = new HashMap<String, String>() {{
                put("documentFileType", filePath.toString().toUpperCase().substring(filePath.toString().lastIndexOf(".") + 1));
                put("base64Content", Base64.getEncoder().encodeToString(Files.readAllBytes(filePath)));
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

            System.out.print(fileCount.incrementAndGet());
            System.out.println(". " + filePath);
        }
        catch (Throwable th){
            th.printStackTrace();
        }
    }
}
