package com.nemo.document.parser;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


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
                DocumentStructure documentStructure = DocumentParser.parse(filePath);
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                String json = gson.toJson(documentStructure);
                System.out.println(json);
            }
            catch (Throwable th){

            }
        }
        catch( ParseException exp ) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("doc2json", options);
        }
    }

}
