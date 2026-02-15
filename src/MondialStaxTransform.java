import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class MondialStaxTransform {

    public static void main(String[] args) throws Exception {
        String parentCountryCode = "E";
        String provinceNameToRemove = "Catalonia";
        String newCountryCode = "CAT";

        String mondialInput = "./src/resources/mondial.xml";
        String newCountryDataFile = "./src/resources/catdata.xml";
        String outputFile = "./mondial_updated.xml";

        // Load new country data (DOM)
        CountryData newCountry = CountryData.load(newCountryDataFile);

        XMLInputFactory inputFactory = XMLInputFactory.newInstance();
        XMLOutputFactory outputFactory = XMLOutputFactory.newInstance();
        XMLEventFactory eventFactory = XMLEventFactory.newInstance();

        try (FileInputStream fis = new FileInputStream(mondialInput);
             Writer writer = new FileWriter(outputFile)) {

            XMLEventReader reader = inputFactory.createXMLEventReader(fis);
            XMLEventWriter xmlWriter = outputFactory.createXMLEventWriter(writer);

            boolean inParentCountry = false;
            boolean inProvince = false;
            boolean skipProvince = false;
            String parentMemberships = null;

            List<XMLEvent> provinceBuffer = new ArrayList<>();

            while (reader.hasNext()) {
                XMLEvent event = reader.nextEvent();

                if (event.isStartElement()) {
                    StartElement start = event.asStartElement();
                    String name = start.getName().getLocalPart();

                    // Enter parent country
                    if ("country".equals(name)) {
                        Attribute carCodeAttr = start.getAttributeByName(QName.valueOf("car_code"));
                        if (carCodeAttr != null && parentCountryCode.equals(carCodeAttr.getValue())) {
                            inParentCountry = true;
                            Attribute membershipsAttr = start.getAttributeByName(QName.valueOf("memberships"));
                            parentMemberships = membershipsAttr != null ? membershipsAttr.getValue() : null;
                        }
                    }

                    // Start province
                    if (inParentCountry && "province".equals(name)) {
                        inProvince = true;
                        skipProvince = false;
                        provinceBuffer.clear();
                        provinceBuffer.add(event);
                        continue;
                    }

                    // Inside province buffer
                    if (inProvince) {
                        provinceBuffer.add(event);
                        continue;
                    }

                    // Normal write
                    xmlWriter.add(event);

                } else if (event.isCharacters()) {
                    if (inProvince) {
                        Characters chars = event.asCharacters();
                        String text = chars.getData().trim();
                        if (provinceNameToRemove.equals(text)) {
                            skipProvince = true;
                        }
                        provinceBuffer.add(event);
                        continue;
                    }
                    xmlWriter.add(event);

                } else if (event.isEndElement()) {
                    EndElement end = event.asEndElement();
                    String name = end.getName().getLocalPart();

                    if (inProvince) {
                        provinceBuffer.add(event);
                        if ("province".equals(name)) {
                            inProvince = false;
                            if (!skipProvince) {
                                for (XMLEvent e : provinceBuffer) {
                                    xmlWriter.add(e);
                                }
                            }
                        }
                        continue;
                    }

                    if ("country".equals(name) && inParentCountry) {
                        inParentCountry = false;
                        xmlWriter.add(end); // close parent country

                        // -------- Write new country fully via XMLEventWriter --------
                        List<Attribute> attrs = new ArrayList<>();
                        attrs.add(eventFactory.createAttribute("car_code", newCountryCode));
                        if (parentMemberships != null) {
                            attrs.add(eventFactory.createAttribute("memberships", escapeXml(parentMemberships)));
                        }
                        xmlWriter.add(eventFactory.createStartElement("", "", "country", attrs.iterator(), null));

                        // Write new country content from DOM
                        writeNodeChildrenStax(newCountry.getRootNode(), xmlWriter, eventFactory);

                        xmlWriter.add(eventFactory.createEndElement("", "", "country"));
                        continue;
                    }

                    xmlWriter.add(event);
                } else {
                    xmlWriter.add(event);
                }
            }

            xmlWriter.flush();
            xmlWriter.close();
        }
    }

    // Recursively write DOM node children as StAX events
    private static void writeNodeChildrenStax(Node node, XMLEventWriter writer, XMLEventFactory factory) throws Exception {
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            switch (n.getNodeType()) {
                case Node.ELEMENT_NODE:
                    List<Attribute> attrs = new ArrayList<>();
                    if (n.hasAttributes()) {
                        for (int j = 0; j < n.getAttributes().getLength(); j++) {
                            Node attr = n.getAttributes().item(j);
                            attrs.add(factory.createAttribute(attr.getNodeName(), escapeXml(attr.getNodeValue())));
                        }
                    }
                    writer.add(factory.createStartElement("", "", n.getNodeName(), attrs.iterator(), null));
                    writeNodeChildrenStax(n, writer, factory);
                    writer.add(factory.createEndElement("", "", n.getNodeName()));
                    break;
                case Node.TEXT_NODE:
                    String text = n.getTextContent().trim();
                    if (!text.isEmpty()) {
                        writer.add(factory.createCharacters(escapeXml(text)));
                    }
                    break;
                default:
                    // ignore comments, processing instructions
                    break;
            }
        }
    }

    // Escape XML special characters
    private static String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
