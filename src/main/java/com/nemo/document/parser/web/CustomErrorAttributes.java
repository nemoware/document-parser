package com.nemo.document.parser.web;

import com.nemo.document.parser.DocumentParser;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;

@Component
public class CustomErrorAttributes extends DefaultErrorAttributes {
    @Override
    public Map<String, Object> getErrorAttributes(
            WebRequest webRequest, boolean includeStackTrace) {
        Map<String, Object> errorAttributes =
                super.getErrorAttributes(webRequest, includeStackTrace);
        errorAttributes.put("version", DocumentParser.getVersion());
        return errorAttributes;
    }
}
