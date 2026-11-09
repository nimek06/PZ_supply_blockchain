package pl.pz.supplyblockchain.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The contract allows an empty object; the batch number is assigned by the backend. Unknown
 * fields are still rejected so that a typo cannot silently create an unintended batch.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record CreateBatchRequest() {
}
