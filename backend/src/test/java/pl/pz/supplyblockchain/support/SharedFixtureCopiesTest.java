package pl.pz.supplyblockchain.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Copies of shared fixtures in {@code src/test/resources/fixtures} must stay identical to
 * {@code shared/fixtures} (the source of truth owned by the simulator). Only free-text
 * {@code description} fields may differ; every value used by tests has to match.
 * Fails instead of skipping when the shared directory cannot be found.
 */
class SharedFixtureCopiesTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    static List<String> copiedFixtures() throws IOException {
        try (Stream<Path> files = Files.list(copiesDir())) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".json"))
                    .sorted()
                    .toList();
        }
    }

    @Test
    void copiesExist() throws IOException {
        assertThat(copiedFixtures())
                .contains("signature-vector.json", "complete-stage-calldata.json", "station-certificate.json");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("copiedFixtures")
    void copyMatchesSharedFixture(String name) throws IOException {
        Path shared = sharedDir().resolve(name);
        assertThat(shared).as("shared/fixtures/%s", name).isRegularFile();

        JsonNode copy = withoutDescriptions(MAPPER.readTree(copiesDir().resolve(name).toFile()));
        JsonNode source = withoutDescriptions(MAPPER.readTree(shared.toFile()));

        assertThat(copy)
                .as("backend copy of %s differs from shared/fixtures - copy the file again", name)
                .isEqualTo(source);
    }

    private static JsonNode withoutDescriptions(JsonNode node) {
        if (node instanceof ObjectNode object) {
            object.remove("description");
            object.properties().forEach(entry -> withoutDescriptions(entry.getValue()));
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                withoutDescriptions(node.get(i));
            }
        }
        return node;
    }

    private static Path copiesDir() {
        return repositoryRoot().resolve("backend/src/test/resources/fixtures");
    }

    private static Path sharedDir() {
        return repositoryRoot().resolve("shared/fixtures");
    }

    private static Path repositoryRoot() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            if (Files.isDirectory(dir.resolve("shared/fixtures"))
                    && Files.isDirectory(dir.resolve("backend/src/test/resources/fixtures"))) {
                return dir;
            }
        }
        throw new IllegalStateException(
                "repository root with shared/fixtures not found from " + Path.of("").toAbsolutePath());
    }
}
