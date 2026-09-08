package com.gommit.domain.point.service;

import com.gommit.domain.point.dto.request.PeriodFilter;
import com.gommit.domain.point.dto.request.PointChangeType;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import com.gommit.global.time.BusinessClock;
import com.gommit.global.time.BusinessDayCutoff;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// PersonalPointService/GroupPointService가 공통으로 쓰는 기간 필터 계산 로직.
// 하루는 04:00에 시작한다. BusinessDayCutoff을 활용하고, "지금"은 BusinessClock에서 받는다.
@Component
@RequiredArgsConstructor
public class PointPeriodCalculator {

    private final BusinessClock businessClock;

    public LocalDateTime[] toDateRange(PeriodFilter period, LocalDate from, LocalDate to) {
        if ((from == null) != (to == null)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (from != null && to.isBefore(from)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (from != null) {
            return new LocalDateTime[] {
                BusinessDayCutoff.startTimeOfBusinessDate(from), BusinessDayCutoff.startTimeOfBusinessDate(to.plusDays(1))
            };
        }

        if (period == null || period == PeriodFilter.ALL) {
            return new LocalDateTime[] {null, null};
        }
        LocalDate thisMonthFirstDay = businessClock.firstDayOfBusinessMonth();
        LocalDateTime startOfThisMonth = BusinessDayCutoff.startTimeOfBusinessDate(thisMonthFirstDay);
        if (period == PeriodFilter.THIS_MONTH) {
            return new LocalDateTime[] {startOfThisMonth, null};
        }
        LocalDateTime startOfLastMonth = BusinessDayCutoff.startTimeOfBusinessDate(thisMonthFirstDay.minusMonths(1));
        return new LocalDateTime[] {startOfLastMonth, startOfThisMonth};
    }

    public Boolean toEarnFlag(PointChangeType type) {
        if (type == null || type == PointChangeType.ALL) {
            return null;
        }
        return type == PointChangeType.EARN;
    }
}
