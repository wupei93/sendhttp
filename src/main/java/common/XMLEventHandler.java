package common;

import javax.xml.stream.XMLEventReader;

@FunctionalInterface
public interface XMLEventHandler {
    void handle(XMLEventReader eventReader);
}
