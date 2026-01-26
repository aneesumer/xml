import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.Writer;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

public class MondialSaxTransform {

    public static void main(String[] args) throws Exception {

        String parentCountryCode = "E";
        String provinceNameToRemove = "Catalonia";
        String newCountryCode = "CAT";

        String mondialInput = "./src/resources/mondial.xml";
        String newCountryData = "./src/resources/catdata.xml";
        String output = "./mondial_updated.xml";

        CountryData newCountry = CountryData.load(newCountryData);

        try (Writer out = new BufferedWriter(new FileWriter(output))) {

            SAXParser parser = SAXParserFactory.newInstance().newSAXParser();

            MondialHandler handler = new MondialHandler(
                    out,
                    parentCountryCode,
                    provinceNameToRemove,
                    newCountryCode,
                    newCountry
            );

            parser.parse(new File(mondialInput), handler);
        }
    }
}
