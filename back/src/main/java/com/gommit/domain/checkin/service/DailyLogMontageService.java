package com.gommit.domain.checkin.service;

import com.gommit.domain.challenge.entity.ChallengeMemberStatus;
import com.gommit.domain.challenge.repository.ChallengeMemberRepository;
import com.gommit.domain.checkin.entity.CheckIn;
import com.gommit.domain.checkin.entity.DailyLog;
import com.gommit.domain.checkin.entity.MediaType;
import com.gommit.domain.checkin.media.CheckInMediaStore;
import com.gommit.domain.checkin.media.DailyLogMediaStore;
import com.gommit.domain.checkin.repository.CheckInRepository;
import com.gommit.domain.checkin.repository.DailyLogRepository;
import com.gommit.domain.checkin.support.DailyLogMontageBuilder;
import com.gommit.domain.checkin.support.DailyLogMontageBuilder.Frame;
import com.gommit.domain.checkin.support.DailyLogMontageBuilder.Kind;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// @Transactional 이 적용되도록 항상 다른 빈(DailyLogMontageEventListener, DailyLogMontageScheduler)에서 호출한다.
//
// 몽타주 = 회차별 그리드 프레임을 순차 concat 한 영상.
// - 그리드 크기 N = 그 businessDate 시점 스냅샷 멤버 수(앞 MAX_CELLS 명).
// - 칸 = 멤버 고정 슬롯(가입순). 어떤 회차에 그 멤버가 인증 안 했으면 그 칸은 검정.
// - roundNo 오름차순으로 앞 montage-max-rounds 회차만.
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyLogMontageService {

    private final DailyLogRepository dailyLogRepository;
    private final CheckInRepository checkInRepository;
    private final ChallengeMemberRepository challengeMemberRepository;
    private final CheckInMediaStore checkInMediaStore;
    private final DailyLogMediaStore dailyLogMediaStore;
    private final DailyLogMontageBuilder montageBuilder;

    @Value("${app.dailylog.montage-max-rounds:8}")
    private int maxRounds;

    @Transactional
    public void generateMontage(Long challengeId, LocalDate businessDate) {
        DailyLog dailyLog = dailyLogRepository
                .findByChallengeIdAndLogDate(challengeId, businessDate)
                .orElse(null);
        if (dailyLog == null || dailyLog.getVideoKey() != null) {
            return; // row 없음(레이스) 또는 이미 생성됨 — 재실행해도 안전
        }

        Map<Long, Integer> cellByUser = resolveCells(challengeId, businessDate);
        int cellCount = cellByUser.size();
        if (cellCount == 0) {
            return; // 스냅샷 멤버 없음
        }

        List<CheckIn> checkIns = checkInRepository.findByChallengeIdAndBusinessDate(challengeId, businessDate);
        if (checkIns.isEmpty()) {
            return;
        }

        List<List<Frame>> rounds = buildRounds(checkIns, cellByUser, cellCount);
        if (rounds.isEmpty()) {
            return; // 칸이 배정된 멤버가 인증한 회차가 없음
        }

        montageBuilder
                .build(cellCount, rounds)
                .ifPresentOrElse(
                        video -> dailyLog.attachVideo(dailyLogMediaStore.store(video)),
                        () -> log.warn(
                                "DailyLog 몽타주 생성 실패/ffmpeg 부재 — videoKey null 유지 (challengeId={}, businessDate={})",
                                challengeId,
                                businessDate));
    }

    // 그 businessDate 시점 스냅샷 멤버(가입순) 앞 MAX_CELLS 명 → userId → 칸 인덱스.
    private Map<Long, Integer> resolveCells(Long challengeId, LocalDate businessDate) {
        // TODO(feat/20): businessDate 경계가 04:00 로 바뀌면 이 창도 이동 (DailyLogService.countsFor 와 동일 규칙).
        LocalDateTime startOfDay = businessDate.atStartOfDay();
        LocalDateTime endOfDay = businessDate.plusDays(1).atStartOfDay();
        List<Long> snapshot = challengeMemberRepository.findSnapshotMemberUserIds(
                challengeId, startOfDay, endOfDay, ChallengeMemberStatus.ACTIVE);

        if (snapshot.size() > DailyLogMontageBuilder.MAX_CELLS) {
            log.warn(
                    "스냅샷 멤버 {}명 — 그리드 상한 {}칸 초과, 앞 {}명만 사용 (challengeId={}, businessDate={})",
                    snapshot.size(),
                    DailyLogMontageBuilder.MAX_CELLS,
                    DailyLogMontageBuilder.MAX_CELLS,
                    challengeId,
                    businessDate);
        }

        int cellCount = Math.min(snapshot.size(), DailyLogMontageBuilder.MAX_CELLS);
        Map<Long, Integer> cellByUser = new HashMap<>();
        for (int i = 0; i < cellCount; i++) {
            cellByUser.put(snapshot.get(i), i);
        }
        return cellByUser;
    }

    private List<List<Frame>> buildRounds(List<CheckIn> checkIns, Map<Long, Integer> cellByUser, int cellCount) {
        // roundNo 오름차순 그룹. 회차 내 같은 유저는 먼저 조회된(id asc) 것만.
        TreeMap<Integer, Map<Long, CheckIn>> byRound = new TreeMap<>();
        for (CheckIn checkIn : checkIns) {
            byRound.computeIfAbsent(checkIn.getRoundNo(), k -> new HashMap<>())
                    .putIfAbsent(checkIn.getUserId(), checkIn);
        }

        List<List<Frame>> rounds = new ArrayList<>();
        for (Map<Long, CheckIn> roundCheckIns : byRound.values()) {
            if (rounds.size() >= maxRounds) {
                break;
            }
            List<Frame> slots = new ArrayList<>(Collections.nCopies(cellCount, null));
            boolean anyMapped = false;
            for (Map.Entry<Long, CheckIn> entry : roundCheckIns.entrySet()) {
                Integer cell = cellByUser.get(entry.getKey());
                if (cell == null) {
                    continue; // 칸 없는 유저(MAX_CELLS 초과분 등) — 무시
                }
                CheckIn checkIn = entry.getValue();
                slots.set(
                        cell,
                        new Frame(
                                checkInMediaStore.load(checkIn.getMediaKey()),
                                extensionOf(checkIn.getMediaKey()),
                                kindOf(checkIn.getMediaType())));
                anyMapped = true;
            }
            if (anyMapped) {
                rounds.add(slots); // 전 칸 검정이 될 회차는 스킵
            }
        }
        return rounds;
    }

    private static Kind kindOf(MediaType mediaType) {
        return switch (mediaType) {
            case IMAGE -> Kind.IMAGE;
        };
    }

    private static String extensionOf(String storageKey) {
        int dot = storageKey.lastIndexOf('.');
        return (dot < 0 || dot == storageKey.length() - 1) ? "jpg" : storageKey.substring(dot + 1);
    }
}
