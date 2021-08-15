package common;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import java.io.Reader;

public class StAXXmlParser {



    public static void parse(Reader sourceReader, XMLEventHandler handler){

        try {
            // 获得解析器
            XMLInputFactory factory = XMLInputFactory.newFactory();
            XMLEventReader reader = factory.createXMLEventReader(sourceReader);
            handler.handle(reader);
        } catch (XMLStreamException e) {
            e.printStackTrace();
        }
    }
}
