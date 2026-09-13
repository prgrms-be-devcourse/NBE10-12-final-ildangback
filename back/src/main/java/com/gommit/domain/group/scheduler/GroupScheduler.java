package com.gommit.domain.group.scheduler;

import com.gommit.domain.group.service.OwnerKickVoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GroupScheduler {
    private final OwnerKickVoteService ownerKickVoteService;

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void expireOutdatedKickVotes() {
        ownerKickVoteService.expireOutdatedVotes();
    }
}
