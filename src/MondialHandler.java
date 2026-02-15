import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class MondialHandler extends DefaultHandler {

    private final Writer out;
    private final String parentCountryCode;
    private final String provinceNameToRemove; // e.g., "Catalonia"
    private final String newCountryCode;
    private final CountryData newCountryData;

    private boolean inParentCountry = false;
    private String parentMemberships = null; // store memberships attribute
    private int depth = 0; // XML indentation

    // Province tracking
    private boolean inProvince = false;
    private boolean skipCurrentProvince = false;
    private StringBuilder provinceBuffer = new StringBuilder();
    private int provinceDepth = 0;

    private final StringBuilder text = new StringBuilder();

    public MondialHandler(Writer out,
                          String parentCountryCode,
                          String provinceNameToRemove,
                          String newCountryCode,
                          CountryData newCountryData) {
        this.out = out;
        this.parentCountryCode = parentCountryCode;
        this.provinceNameToRemove = provinceNameToRemove;
        this.newCountryCode = newCountryCode;
        this.newCountryData = newCountryData;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attrs)
            throws SAXException {

        text.setLength(0);

        // Enter parent country
        if ("country".equals(qName) && parentCountryCode.equals(attrs.getValue("car_code"))) {
            inParentCountry = true;
            parentMemberships = attrs.getValue("memberships"); // capture memberships attribute
        }

        // Start tracking a province
        if ("province".equals(qName) && inParentCountry) {
            inProvince = true;
            skipCurrentProvince = false;
            provinceDepth = 1;
            provinceBuffer.setLength(0);
            appendStartToBuffer(qName, attrs);
            return;
        }

        // Inside a province being tracked
        if (inProvince) {
            provinceDepth++;
            appendStartToBuffer(qName, attrs);
            return;
        }

        // Normal element outside province
        writeStart(qName, attrs);
    }

    @Override
    public void characters(char[] ch, int start, int length) {
        String value = new String(ch, start, length);
        text.append(value);

        if (inProvince) {
            provinceBuffer.append(value);
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {

        // Handling province tracking
        if (inProvince) {
            appendEndToBuffer(qName, text.toString());

            provinceDepth--;
            if ("name".equals(qName) && text.toString().trim().equals(provinceNameToRemove)) {
                skipCurrentProvince = true; // mark province to skip
            }

            // End of province
            if (provinceDepth == 0) {
                inProvince = false;
                if (!skipCurrentProvince) {
                    try {
                        out.write(provinceBuffer.toString());
                    } catch (IOException e) {
                        throw new SAXException(e);
                    }
                }
            }

            text.setLength(0);
            return;
        }

        // Normal element
        writeText(text.toString());
        writeEnd(qName);

        // End of parent country
        if ("country".equals(qName) && inParentCountry) {
            inParentCountry = false;
            writeNewCountry();
        }

        text.setLength(0);
    }

    // ----------------- Helper methods -----------------

    private void appendStartToBuffer(String qName, Attributes attrs) {
        provinceBuffer.append("\n").append("    ".repeat(depth)).append("<").append(qName);
        for (int i = 0; i < attrs.getLength(); i++) {
            provinceBuffer.append(" ")
                    .append(attrs.getQName(i))
                    .append("=\"")
                    .append(attrs.getValue(i))
                    .append("\"");
        }
        provinceBuffer.append(">");
    }

    private void appendEndToBuffer(String qName, String value) {
        if (!value.isBlank()) {
            provinceBuffer.append(escape(value));
        }
        provinceBuffer.append("</").append(qName).append(">");
    }

    private void writeStart(String qName, Attributes attrs) throws SAXException {
        try {
            out.write("\n" + "    ".repeat(depth) + "<" + qName);
            for (int i = 0; i < attrs.getLength(); i++) {
                out.write(" " + attrs.getQName(i) + "=\"" + attrs.getValue(i) + "\"");
            }
            out.write(">");
            depth++;
        } catch (IOException e) {
            throw new SAXException(e);
        }
    }

    private void writeEnd(String qName) throws SAXException {
        try {
            depth--;
            out.write("\n" + "    ".repeat(depth) + "</" + qName + ">");
        } catch (IOException e) {
            throw new SAXException(e);
        }
    }

    private void writeText(String value) throws SAXException {
        try {
            if (!value.isBlank()) {
                out.write(escape(value));
            }
        } catch (IOException e) {
            throw new SAXException(e);
        }
    }

    private void writeNewCountry() throws SAXException {
        try {
            // Include memberships attribute if present
            out.write("\n" + "    ".repeat(depth) + "<country car_code=\"" + newCountryCode + "\"");
            if (parentMemberships != null) {
                out.write(" memberships=\"" + parentMemberships + "\"");
            }
            out.write(">");

            depth++;

            // Write new country data
            newCountryData.writeCountryData(out);

            depth--;
            out.write("\n" + "    ".repeat(depth) + "</country>");
        } catch (IOException e) {
            throw new SAXException(e);
        }
    }

    private String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
