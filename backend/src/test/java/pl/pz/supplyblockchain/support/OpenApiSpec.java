package pl.pz.supplyblockchain.support;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.nio.file.Files;
import java.nio.file.Path;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

/** Reads shared/schemas/openapi.yaml straight from the repo, so tests always check the current file. */
public final class OpenApiSpec {

    private static final Path SCHEMAS = locateSchemas();

    private final JsonNode document;
    // the validator does not read local files on its own, so files from shared/schemas are handed over here
    private final SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12,
            builder -> builder.schemas(OpenApiSpec::readSharedSchema));

    private OpenApiSpec(JsonNode document) {
        this.document = document;
    }

    public static OpenApiSpec load() {
        return new OpenApiSpec(YAMLMapper.builder().build().readTree(openApiFile().toFile()));
    }

    public static Path schemasDir() {
        return SCHEMAS;
    }

    public static Path openApiFile() {
        return SCHEMAS.resolve("openapi.yaml");
    }

    public JsonNode document() {
        return document;
    }

    public SchemaRegistry registry() {
        return registry;
    }

    /** The operation node, e.g. {@code operation("/api/v1/batches/{batchId}/stages", "post")}. */
    public JsonNode operation(String path, String method) {
        JsonNode operation = document.path("paths").path(path).path(method);
        if (operation.isMissingNode()) {
            throw new IllegalArgumentException("no " + method + " " + path + " in openapi.yaml");
        }
        return operation;
    }

    /** The documented response for a status, with {@code #/components/responses} references followed. */
    public JsonNode response(JsonNode operation, int status) {
        JsonNode response = operation.path("responses").path(String.valueOf(status));
        if (response.has("$ref")) {
            response = document.at(pointer(response.get("$ref").asString()));
        }
        return response;
    }

    /** A compiled component schema, e.g. {@code schema("#/components/schemas/Problem")}. */
    public Schema schema(String ref) {
        if (!ref.startsWith("#/components/schemas/")) {
            throw new IllegalArgumentException("expected a component schema reference, got " + ref);
        }
        return registry.getSchema(SchemaLocation.of(openApiFile().toUri() + ref));
    }

    public static String pointer(String ref) {
        return ref.substring(ref.indexOf('#') + 1);
    }

    private static String readSharedSchema(String iri) {
        String prefix = SCHEMAS.toUri().toString();
        if (!iri.startsWith(prefix)) {
            return null;
        }
        Path file = SCHEMAS.resolve(iri.substring(prefix.length()));
        if (!Files.isRegularFile(file)) {
            return null;
        }
        // YAML is passed on as JSON text
        return YAMLMapper.builder().build().readTree(file.toFile()).toString();
    }

    private static Path locateSchemas() {
        // Maven runs tests from backend/, an IDE may run them from the repo root
        for (Path candidate : new Path[] {Path.of("../shared/schemas"), Path.of("shared/schemas")}) {
            if (Files.isRegularFile(candidate.resolve("openapi.yaml"))) {
                return candidate.toAbsolutePath().normalize();
            }
        }
        throw new IllegalStateException("shared/schemas/openapi.yaml not found from " + Path.of("").toAbsolutePath());
    }
}
