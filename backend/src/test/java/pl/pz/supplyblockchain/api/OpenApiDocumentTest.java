package pl.pz.supplyblockchain.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pl.pz.supplyblockchain.support.OpenApiSpec;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class OpenApiDocumentTest {

    private final OpenApiSpec spec = OpenApiSpec.load();

    @Test
    void isValidOpenApi31() throws Exception {
        // official schema from https://spec.openapis.org/oas/3.1/schema/2022-10-07
        Schema oas;
        try (InputStream input = getClass().getResourceAsStream("/openapi/oas-3.1-schema.json")) {
            oas = spec.registry().getSchema(input, InputFormat.JSON);
        }

        assertThat(messages(oas.validate(spec.document()))).isEmpty();
        assertThat(spec.document().path("openapi").asString()).startsWith("3.1.");
    }

    // the OpenAPI schema above does not look inside schema objects, so they are checked separately
    @Test
    void allSchemasAreValidJsonSchema() {
        Schema metaSchema = spec.registry().getSchema(SchemaLocation.of("https://json-schema.org/draft/2020-12/schema"));
        Map<String, JsonNode> schemas = new LinkedHashMap<>();
        spec.document().path("components").path("schemas").properties()
                .forEach(entry -> schemas.put("components/schemas/" + entry.getKey(), entry.getValue()));
        collectSchemas(spec.document().path("paths"), "paths", schemas);
        collectSchemas(spec.document().path("components"), "components", schemas);

        List<String> problems = new ArrayList<>();
        schemas.forEach((where, schema) ->
                messages(metaSchema.validate(schema)).forEach(message -> problems.add(where + ": " + message)));

        assertThat(schemas).hasSizeGreaterThan(50);
        assertThat(problems).isEmpty();
    }

    @Test
    void sharedJsonSchemasAreValid() throws Exception {
        Schema metaSchema = spec.registry().getSchema(SchemaLocation.of("https://json-schema.org/draft/2020-12/schema"));
        JsonMapper mapper = JsonMapper.builder().build();
        List<String> problems = new ArrayList<>();
        try (var files = Files.list(OpenApiSpec.schemasDir())) {
            for (Path file : files.filter(path -> path.toString().endsWith(".schema.json")).toList()) {
                messages(metaSchema.validate(mapper.readTree(file.toFile())))
                        .forEach(message -> problems.add(file.getFileName() + ": " + message));
            }
        }

        assertThat(problems).isEmpty();
    }

    @Test
    void everyRefPointsToSomething() {
        List<String> broken = new ArrayList<>();
        collectRefs(spec.document(), broken);

        assertThat(broken).isEmpty();
    }

    @Test
    void operationIdsAreUnique() {
        Set<String> seen = new HashSet<>();
        List<String> duplicates = new ArrayList<>();
        for (Map.Entry<String, JsonNode> path : spec.document().path("paths").properties()) {
            for (Map.Entry<String, JsonNode> operation : path.getValue().properties()) {
                String id = operation.getValue().path("operationId").asString();
                if (id.isEmpty() || !seen.add(id)) {
                    duplicates.add(operation.getKey() + " " + path.getKey() + " -> '" + id + "'");
                }
            }
        }

        assertThat(duplicates).isEmpty();
    }

    // every "schema" value in parameters, headers and media types
    private static void collectSchemas(JsonNode node, String where, Map<String, JsonNode> schemas) {
        if (!node.isContainer()) {
            return;
        }
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                String childWhere = where + "/" + entry.getKey();
                if (entry.getKey().equals("schema") && entry.getValue().isObject()) {
                    schemas.put(childWhere, entry.getValue());
                } else if (!entry.getKey().equals("schemas")) {
                    collectSchemas(entry.getValue(), childWhere, schemas);
                }
            }
        } else {
            int index = 0;
            for (JsonNode child : node.values()) {
                collectSchemas(child, where + "/" + index++, schemas);
            }
        }
    }

    private void collectRefs(JsonNode node, List<String> broken) {
        if (node.isObject()) {
            JsonNode ref = node.get("$ref");
            if (ref != null && ref.isString() && !resolves(ref.asString())) {
                broken.add(ref.asString());
            }
            node.values().forEach(child -> collectRefs(child, broken));
        } else if (node.isArray()) {
            node.values().forEach(child -> collectRefs(child, broken));
        }
    }

    private boolean resolves(String ref) {
        if (ref.startsWith("#")) {
            return !spec.document().at(OpenApiSpec.pointer(ref)).isMissingNode();
        }
        String file = ref.contains("#") ? ref.substring(0, ref.indexOf('#')) : ref;
        return Files.isRegularFile(OpenApiSpec.schemasDir().resolve(file));
    }

    private static List<String> messages(List<Error> errors) {
        return errors.stream().map(Error::toString).toList();
    }
}
