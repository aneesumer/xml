import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class CountryData {

    private final Node countryNode;

    private CountryData(Node countryNode) {
        this.countryNode = countryNode;
    }

    public Node getRootNode() {
        return countryNode;
    }

    public static CountryData load(String file) throws Exception {
        DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();
        DocumentBuilder builder =
                factory.newDocumentBuilder();
        Document document =
                builder.parse(new File(file));

        Node root = document.getDocumentElement();

        flattenDom(root);

        return new CountryData(root);
    }

    public void writeCountryData(Writer out) throws IOException {
        NodeList children = countryNode.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);

            if (n.getNodeType() == Node.ELEMENT_NODE) {
                out.write(nodeToString(n));
            }
        }
    }

    private static void flattenDom(Node node) {
        NodeList children = node.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);

            if (child.getNodeType() == Node.ELEMENT_NODE) {
                flattenDom(child);
            }
        }

        // Do not unwrap document root
        if (node.getParentNode() == null ||
                node.getParentNode().getNodeType() ==
                        Node.DOCUMENT_NODE) {
            return;
        }

        // Only unwrap elements that contain element children
        boolean hasElementChildren = false;
        NodeList nodeChildren = node.getChildNodes();

        for (int i = 0; i < nodeChildren.getLength(); i++) {
            if (nodeChildren.item(i).getNodeType() ==
                    Node.ELEMENT_NODE) {
                hasElementChildren = true;
                break;
            }
        }

        if (!hasElementChildren) {
            return;
        }

        Node parent = node.getParentNode();
        Node nextSibling = node.getNextSibling();

        while (node.hasChildNodes()) {
            Node child = node.getFirstChild();
            node.removeChild(child);
            parent.insertBefore(child, nextSibling);
        }

        parent.removeChild(node);
    }

    private String nodeToString(Node node) throws IOException {
        try {
            StringWriter sw = new StringWriter();
            Transformer transformer =
                    TransformerFactory
                            .newInstance()
                            .newTransformer();

            transformer.setOutputProperty(
                    OutputKeys.OMIT_XML_DECLARATION,
                    "yes");
            transformer.setOutputProperty(
                    OutputKeys.INDENT,
                    "yes");
            transformer.setOutputProperty(
                    "{http://xml.apache.org/xslt}indent-amount",
                    "4");

            transformer.transform(
                    new DOMSource(node),
                    new StreamResult(sw));

            return sw.toString();
        } catch (Exception e) {
            throw new IOException(e);
        }
    }
}
