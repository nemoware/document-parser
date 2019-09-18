package com.nemo.document.parser.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan("com.nemo.document.parser")
public class DocumentParserService {
    public static void main(String[] args) {
        SpringApplication.run(DocumentParserService.class, args);
    }
}
