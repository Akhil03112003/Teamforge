package com.teamforge.teams.application;

import com.teamforge.platform.api.ApiException;
import com.teamforge.platform.idempotency.IdempotentMutations;
import com.teamforge.teams.persistence.TeamStore;
import com.teamforge.teams.persistence.TeamStore.Row;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class MembershipAdmissionService {
    /** Caller derives actor from authenticated server context; no public direct-join route. */
    public record Command(UUID teamId, UUID participantId, UUID actorId,
                          AdmissionAuthorization authorization, String idempotencyKey) {}
    public record Result(UUID teamId, UUID hackathonId, UUID participantId, UUID membershipId, int memberCount) {}
    private record Fingerprint(int version, UUID teamId, UUID participantId, String authorityKind, AdmissionAuthorization authorization) {}
    private final TeamStore store;
    private final IdempotentMutations mutations;
    public MembershipAdmissionService(TeamStore store,IdempotentMutations mutations) { this.store=store;this.mutations=mutations; }
    public Result admit(Command command) {
        if (command==null || command.teamId()==null || command.participantId()==null
            || command.actorId()==null || command.authorization()==null)
            throw new ApiException(400,"INVALID_ADMISSION","Supply a trusted, complete admission command.");
        return mutations.execute(command.actorId(),"MEMBERSHIP_ADMIT",command.idempotencyKey(),
            new Fingerprint(1,command.teamId(),command.participantId(),command.authorization().kind(),command.authorization()),
            Result.class,() -> {
                var locator=store.find(command.teamId(),false);
                if (!store.participantExists(command.participantId()))
                    throw new ApiException(404,"PARTICIPANT_NOT_FOUND","Participant not found.");
                store.ensureParticipant(locator.eventId(),command.participantId());
                store.lockParticipant(locator.eventId(),command.participantId());
                var team=store.find(command.teamId(),true);
                command.authorization().verify(team,command.actorId());
                UUID membership=append(team,command.participantId());
                store.audit(team,command.actorId(),"MEMBER_ADMITTED","USER",command.participantId());
                return new Result(team.id(),team.eventId(),command.participantId(),membership,store.memberCount(team.id()));
            });
    }
    // Creator setup is covered by the outer persisted TEAM_CREATE outcome.
    void creator(Row team,UUID actor) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
            || !team.leaderId().equals(actor) || store.memberCount(team.id())!=0)
            throw new IllegalStateException("Creator admission requires a new owned team transaction.");
        append(team,actor);
    }
    private UUID append(Row team,UUID participant) {
        if (store.membership(team.eventId(),participant).isPresent())
            throw new ApiException(409,"ALREADY_TEAMED","This participant already belongs to a team in this hackathon.");
        if (store.memberCount(team.id())>=store.event(team.eventId()).maxSize())
            throw new ApiException(409,"TEAM_FULL","This team has no remaining seats.");
        try { return store.addMember(team,participant); }
        catch (DuplicateKeyException error) {
            throw new ApiException(409,"ALREADY_TEAMED","Membership changed. Retry with the same key.");
        }
    }
}
