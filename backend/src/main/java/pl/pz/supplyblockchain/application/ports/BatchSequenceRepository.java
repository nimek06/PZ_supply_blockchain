package pl.pz.supplyblockchain.application.ports;

import pl.pz.supplyblockchain.domain.DeploymentId;

public interface BatchSequenceRepository {

    /**
     * Atomically allocates the next sequence value for a deployment and calendar year.
     */
    int nextValue(DeploymentId deploymentId, int year);
}
