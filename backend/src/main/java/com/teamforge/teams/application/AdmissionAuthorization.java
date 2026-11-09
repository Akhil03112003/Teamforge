package com.teamforge.teams.application;

import com.teamforge.platform.api.ApiException;
import com.teamforge.teams.persistence.TeamStore.Row;
import java.util.UUID;

/** Trusted server-domain authorization, never an HTTP DTO. M4 must add validated invite proof here. */
public sealed interface AdmissionAuthorization permits AdmissionAuthorization.LeaderApproval {
    void verify(Row lockedTeam, UUID actor);
    String kind();
    record LeaderApproval(UUID leaderId) implements AdmissionAuthorization {
        @Override public String kind() { return "LEADER_APPROVAL"; }
        @Override public void verify(Row lockedTeam,UUID actor) {
            if (!actor.equals(leaderId) || !lockedTeam.leaderId().equals(actor))
                throw new ApiException(403,"LEADER_REQUIRED","Only the current leader can authorize admission.");
        }
    }
}
