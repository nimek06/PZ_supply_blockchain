package pl.pz.supplyblockchain.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pl.pz.supplyblockchain.application.ports.CommandRepository;
import pl.pz.supplyblockchain.domain.CommandStatus;
import pl.pz.supplyblockchain.domain.CommandType;
import pl.pz.supplyblockchain.domain.DomainException;
import pl.pz.supplyblockchain.domain.ErrorCode;
import pl.pz.supplyblockchain.domain.StageCommand;

class CommandQueryServiceTest {

    private final CommandRepository commandRepository = mock(CommandRepository.class);
    private final RoleService roleService = mock(RoleService.class);
    private final CommandQueryService service = new CommandQueryService(commandRepository, roleService);

    private final String owner = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8";
    private final String other = "0x3c44cdd05a9000f507687041266870c569253590";
    private final StageCommand command = new StageCommand(
            UUID.randomUUID(), CommandType.STAGE, owner, "0xbatch", null, 1, owner,
            "0xd6337752b30f2464c72077439190dafc2d68d9f20fe3bdf436d6e9b2c1c3fd3e",
            "0xhash", "0xdigest", 1800000600L, true, CommandStatus.ACCEPTED, null,
            null, null, null, null,
            Instant.ofEpochSecond(1800000000L), Instant.ofEpochSecond(1800000000L));

    @Test
    void ownerSeesOwnCommand() {
        when(commandRepository.findById(command.id())).thenReturn(Optional.of(command));

        assertThat(service.getVisible(command.id(), owner)).isEqualTo(command);
    }

    @Test
    void administratorSeesAnyCommand() {
        when(commandRepository.findById(command.id())).thenReturn(Optional.of(command));
        when(roleService.isAdmin(other)).thenReturn(true);

        assertThat(service.getVisible(command.id(), other)).isEqualTo(command);
    }

    @Test
    void otherUserGetsNotFound() {
        when(commandRepository.findById(command.id())).thenReturn(Optional.of(command));
        when(roleService.isAdmin(other)).thenReturn(false);

        assertThatThrownBy(() -> service.getVisible(command.id(), other))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void missingCommandGetsNotFound() {
        UUID missing = UUID.randomUUID();
        when(commandRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getVisible(missing, owner))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void unavailableRoleRegistryIsExplicitDependencyError() {
        when(commandRepository.findById(command.id())).thenReturn(Optional.of(command));
        when(roleService.isAdmin(other)).thenThrow(
                new DomainException(ErrorCode.DEPENDENCY_UNAVAILABLE, "role registry down"));

        assertThatThrownBy(() -> service.getVisible(command.id(), other))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).code())
                        .isEqualTo(ErrorCode.DEPENDENCY_UNAVAILABLE));
    }
}
