package pl.pz.supplyblockchain.api.dto;

import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public class MeasurementsDeserializer extends ValueDeserializer<Map<String, Long>> {

    @Override
    public Map<String, Long> deserialize(JsonParser parser, DeserializationContext context)
            throws JacksonException {
        if (!parser.hasToken(JsonToken.START_OBJECT)) {
            return context.reportInputMismatch(Map.class, "report.measurements must be an object");
        }
        Map<String, Long> measurements = new LinkedHashMap<>();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String key = parser.currentName();
            JsonToken token = parser.nextToken();
            if (token != JsonToken.VALUE_NUMBER_INT) {
                return context.reportInputMismatch(Long.class,
                        "report.measurements values must be integers");
            }
            measurements.put(key, parser.getLongValue());
        }
        return measurements;
    }
}
