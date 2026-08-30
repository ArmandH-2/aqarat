package co.syntropyhq.aqarat;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.xml.sax.SAXException;

/**
 * Every panel is well-formed XML.
 *
 * <p>A mismatched tag in an FXML file compiles perfectly happily and fails at
 * runtime, when a person clicks the navigation item: the panel throws, the
 * sidebar highlight has already moved, and the previous screen stays on display
 * — which looks like the click did nothing rather than like a crash. That is an
 * expensive way to find a typo, and it happened while restructuring a filter
 * block.
 *
 * <p>This does not load the views — that needs a JavaFX toolkit and a display —
 * it only proves the markup parses, which is the failure worth catching cheaply.
 */
class FxmlWellFormedTest {

    private static final Path FXML_DIR = Path.of("src", "main", "resources", "fxml");

    static Stream<Path> panels() throws IOException {
        try (Stream<Path> files = Files.list(FXML_DIR)) {
            List<Path> panels = files
                .filter(path -> path.getFileName().toString().endsWith(".fxml"))
                .sorted()
                .toList();
            if (panels.isEmpty()) {
                fail("No FXML found under " + FXML_DIR.toAbsolutePath());
            }
            return panels.stream();
        }
    }

    @ParameterizedTest(name = "{0} is well-formed")
    @MethodSource("panels")
    @DisplayName("every panel parses")
    void parses(Path panel) throws ParserConfigurationException, IOException, URISyntaxException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // FXML uses processing instructions for imports and no schema, so
        // validation is off and only well-formedness is asserted.
        factory.setValidating(false);
        factory.setNamespaceAware(true);
        try (InputStream stream = Files.newInputStream(panel)) {
            factory.newDocumentBuilder().parse(stream);
        } catch (SAXException e) {
            fail(panel.getFileName() + " is not well-formed XML: " + e.getMessage());
        }
    }
}
