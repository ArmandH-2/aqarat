package co.syntropyhq.aqarat;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javafx.fxml.FXML;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Every panel's markup and its controller agree with each other.
 *
 * <p>{@link FxmlWellFormedTest} proves the XML parses. That is not enough: a
 * well-formed panel whose {@code onAction} names a method nobody wrote, or
 * whose controller declares an {@code @FXML} field with no matching
 * {@code fx:id}, compiles and parses and then fails when a person clicks it —
 * as a handler exception, or as a null field the first time the panel tries to
 * paint itself.
 *
 * <p>Checked by reflection rather than by loading the views, deliberately. A
 * real {@code FXMLLoader} needs the JavaFX toolkit and a display, which would
 * make the suite refuse to run anywhere without one. The markup and the class
 * are both readable without either, and they are where the mistake is.
 */
class FxmlBindingTest {

    private static final Path FXML_DIR = Path.of("src", "main", "resources", "fxml");

    static Stream<Path> panels() throws IOException {
        try (Stream<Path> files = Files.list(FXML_DIR)) {
            return files
                .filter(path -> path.getFileName().toString().endsWith(".fxml"))
                .sorted()
                .toList()
                .stream();
        }
    }

    @ParameterizedTest(name = "{0} binds to its controller")
    @MethodSource("panels")
    @DisplayName("markup and controller agree")
    void bindsToItsController(Path panel) throws Exception {
        Element root = parse(panel);
        String controllerName = root.getAttribute("fx:controller");
        if (controllerName.isBlank()) {
            return;
        }
        Class<?> controller = Class.forName(controllerName);
        String file = panel.getFileName().toString();

        Set<String> ids = new HashSet<>();
        Set<String> handlers = new HashSet<>();
        collect(root, ids, handlers);

        for (String handler : handlers) {
            assertTrue(hasMethod(controller, handler),
                file + " calls #" + handler + ", which " + controller.getSimpleName()
                    + " does not declare.");
        }

        for (Field field : injectedFields(controller)) {
            assertTrue(ids.contains(field.getName()),
                controller.getSimpleName() + "." + field.getName()
                    + " is @FXML but no fx:id in " + file + " matches it, so it stays null.");
        }
    }

    /**
     * Every element the panel uses is imported.
     *
     * <p>FXML resolves tag names through its {@code <?import ?>} processing
     * instructions. A tag with no import parses perfectly as XML and throws
     * the moment {@code FXMLLoader} reaches it, which is when somebody clicks
     * the panel - the same late failure {@link FxmlWellFormedTest} exists to
     * avoid, one layer up.
     */
    @ParameterizedTest(name = "{0} imports everything it uses")
    @MethodSource("panels")
    @DisplayName("every element used is imported")
    void importsEveryElementItUses(Path panel) throws Exception {
        String source = Files.readString(panel);
        Set<String> imported = new HashSet<>();
        Matcher imports = Pattern.compile("<\\?import\\s+([\\w.]+)\\s*\\?>").matcher(source);
        while (imports.find()) {
            String type = imports.group(1);
            imported.add(type.substring(type.lastIndexOf('.') + 1));
        }

        Set<String> used = new HashSet<>();
        collectTags(parse(panel), used);
        for (String tag : used) {
            assertTrue(imported.contains(tag),
                panel.getFileName() + " uses <" + tag + "> with no matching <?import ?>.");
        }
    }

    // Only capitalised tags name a class. A lower-case tag is a property
    // element - <padding>, <children>, <content> - and needs no import.
    // <StackPane.margin> is a static property: it is the owning type before
    // the dot that has to be imported, not the whole tag.
    private void collectTags(Node node, Set<String> tags) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            String name = node.getNodeName();
            if (!name.contains(":") && Character.isUpperCase(name.charAt(0))) {
                int dot = name.indexOf('.');
                tags.add(dot < 0 ? name : name.substring(0, dot));
            }
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            collectTags(children.item(i), tags);
        }
    }

    private Element parse(Path panel) throws ParserConfigurationException, IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setValidating(false);
        try (InputStream stream = Files.newInputStream(panel)) {
            return factory.newDocumentBuilder().parse(stream).getDocumentElement();
        } catch (SAXException e) {
            fail(panel.getFileName() + " is not well-formed XML: " + e.getMessage());
            throw new IllegalStateException("unreachable");
        }
    }

    // fx:id and every on* handler, anywhere in the tree. Handlers are written
    // "#methodName"; anything else is a script binding, which this project
    // does not use.
    private void collect(Node node, Set<String> ids, Set<String> handlers) {
        NamedNodeMap attributes = node.getAttributes();
        if (attributes != null) {
            for (int i = 0; i < attributes.getLength(); i++) {
                Node attribute = attributes.item(i);
                String name = attribute.getNodeName();
                String value = attribute.getNodeValue();
                if ("fx:id".equals(name)) {
                    ids.add(value);
                } else if (name.startsWith("on") && value.startsWith("#")) {
                    handlers.add(value.substring(1));
                }
            }
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            collect(children.item(i), ids, handlers);
        }
    }

    private boolean hasMethod(Class<?> controller, String name) {
        for (Class<?> type = controller; type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.getName().equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<Field> injectedFields(Class<?> controller) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> type = controller; type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (field.isAnnotationPresent(FXML.class)) {
                    fields.add(field);
                }
            }
        }
        return fields;
    }
}
