package com.nemo.document.parser;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormat;
import java.text.SimpleDateFormat;


/**
 * Hello world!
 *
 */
public class App 
{
    private static Logger logger = LoggerFactory.getLogger(App.class);

    public static void main(String[] args)
    {
        Options options = new Options();
        options.addRequiredOption("i", "input-file", true, "Input doc or docx file.");
        CommandLineParser parser = new DefaultParser();
        try {
            // parse the command line arguments
            CommandLine line = parser.parse( options, args );
            try {
                String filePath = line.getOptionValue("i");
                ch.qos.logback.classic.Logger rootLogger = (ch.qos.logback.classic.Logger)LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
                rootLogger.setLevel(Level.toLevel("error"));
                DocumentStructure documentStructure = DocumentParser.parse(filePath);
                ObjectMapper mapper = new ObjectMapper();
                DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
                mapper.setDateFormat(df);
                String json = mapper.writeValueAsString(documentStructure);
                System.out.println(json);
            }
            catch (Throwable th){
                logger.error("Error: ", th);
            }
        }
        catch( ParseException exp ) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("doc2json", options);
        }
    }

}
