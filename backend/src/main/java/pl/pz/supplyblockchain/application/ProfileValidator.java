package pl.pz.supplyblockchain.application;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import pl.pz.supplyblockchain.domain.DomainException;
import pl.pz.supplyblockchain.domain.ErrorCode;
import pl.pz.supplyblockchain.domain.TelemetryReport;
import tools.jackson.databind.ObjectMapper;

@Component
public class ProfileValidator {

    private final Map<String, StageProfile> profiles;

    public ProfileValidator(ObjectMapper objectMapper) {
        try (InputStream input = new ClassPathResource("profiles/ebike-v1.json").getInputStream()) {
            ProfileDocument document = objectMapper.readValue(input, ProfileDocument.class);
            this.profiles = document.profiles();
        } catch (IOException exception) {
            throw new IllegalStateException("unable to load measurement profile", exception);
        }
    }

    public void validate(TelemetryReport report) {
        String stageKey = Integer.toString(report.stageId());
        StageProfile profile = profiles.get(stageKey);
        if (profile == null) {
            throw new DomainException(ErrorCode.PROFILE_VIOLATION, "no measurement profile for stage");
        }
        Map<String, Long> measurements = report.measurements();
        if (measurements == null || measurements.isEmpty()) {
            throw new DomainException(ErrorCode.PROFILE_VIOLATION, "measurements are required by the profile");
        }
        if (!measurements.keySet().equals(profile.fields().keySet())) {
            throw new DomainException(ErrorCode.PROFILE_VIOLATION,
                    "measurements must contain exactly the profile fields");
        }
        for (Map.Entry<String, Long> entry : measurements.entrySet()) {
            Long value = entry.getValue();
            FieldRule rule = profile.fields().get(entry.getKey());
            if (value == null || value < rule.min() || value > rule.max()) {
                throw new DomainException(ErrorCode.PROFILE_VIOLATION,
                        "measurement " + entry.getKey() + " is outside the profile range");
            }
        }
        boolean measurementsPass = evaluate(profile.qaRule(), measurements);
        if (report.qaPassed() != measurementsPass) {
            throw new DomainException(ErrorCode.PROFILE_VIOLATION,
                    "qaPassed is inconsistent with the measurement profile");
        }
    }

    private static boolean evaluate(String rule, Map<String, Long> measurements) {
        return switch (rule) {
            case "CHECKLIST_COMPLETE" ->
                    measurements.get("checklistItemsPassed").equals(measurements.get("checklistItemsTotal"));
            case "OPERATIONS_COMPLETE" ->
                    measurements.get("operationsCompleted").equals(measurements.get("operationsTotal"));
            case "QUALITY_CONTROL" ->
                    measurements.get("defectsFound") == 0L
                            && measurements.get("checklistItemsPassed")
                                    .equals(measurements.get("checklistItemsTotal"));
            case "LABELS_COMPLETE" ->
                    measurements.get("labelsApplied").equals(measurements.get("unitsPacked"));
            default -> throw new DomainException(
                    ErrorCode.PROFILE_VIOLATION, "unknown profile rule " + rule);
        };
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private record ProfileDocument(Map<String, StageProfile> profiles) {
    }

    private record StageProfile(Map<String, FieldRule> fields, String qaRule) {
    }

    private record FieldRule(long min, long max) {
    }
}
