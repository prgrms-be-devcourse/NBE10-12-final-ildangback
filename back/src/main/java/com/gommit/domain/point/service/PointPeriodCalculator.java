package com.gommit.domain.point.service;

import com.gommit.domain.point.dto.request.PeriodFilter;
import com.gommit.domain.point.dto.request.PointChangeType;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

// PersonalPointService/GroupPointService가 공통으로 쓰는 기간 필터 계산 로직.
// 하루는 00:00이 아니라 04:00에 시작한다는 프로젝트 전역 규칙을 적용한다.
@Component
public class PointPeriodCalculator {

    private static final int BUSINESS_DAY_CUTOFF_HOUR = 4;

    public LocalDateTime[] toDateRange(PeriodFilter period, LocalDate from, LocalDate to) {
        if ((from == null) != (to == null)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (from != null && to.isBefore(from)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (from != null) {
            LocalDateTime start = from.atTime(BUSINESS_DAY_CUTOFF_HOUR, 0);
            LocalDateTime end = to.plusDays(1).atTime(BUSINESS_DAY_CUTOFF_HOUR, 0);
            return new LocalDateTime[] {start, end};
        }

        if (period == null || period == PeriodFilter.ALL) {
            return new LocalDateTime[] {null, null};
        }
        LocalDate thisMonthFirstDay = currentBusinessMonthFirstDay();
        LocalDateTime startOfThisMonth = startOfBusinessMonth(thisMonthFirstDay);
        if (period == PeriodFilter.THIS_MONTH) {
            return new LocalDateTime[] {startOfThisMonth, null};
        }
        LocalDateTime startOfLastMonth = startOfBusinessMonth(thisMonthFirstDay.minusMonths(1));
        return new LocalDateTime[] {startOfLastMonth, startOfThisMonth};
    }

    public Boolean toEarnFlag(PointChangeType type) {
        if (type == null || type == PointChangeType.ALL) {
            return null;
        }
        return type == PointChangeType.EARN;
    }

    // package-private: PointPeriodCalculatorTest에서 임의의 시각으로 직접 테스트하기 위함.
    LocalDate businessMonthFirstDay(LocalDateTime now) {
        return now.minusHours(BUSINESS_DAY_CUTOFF_HOUR).toLocalDate().withDayOfMonth(1);
    }

    private LocalDate currentBusinessMonthFirstDay() {
        return businessMonthFirstDay(LocalDateTime.now());
    }

    private LocalDateTime startOfBusinessMonth(LocalDate monthFirstDay) {
        return monthFirstDay.atTime(BUSINESS_DAY_CUTOFF_HOUR, 0);
    }
}
