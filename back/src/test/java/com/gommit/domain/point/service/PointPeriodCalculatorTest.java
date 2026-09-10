package com.gommit.domain.point.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gommit.domain.point.dto.request.PeriodFilter;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.time.BusinessClock;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PointPeriodCalculator — 기간 필터 → [start, end) 범위")
class PointPeriodCalculatorTest {

    // 2026-09-15 14:00 → 영업월 1일 = 2026-09-01
    private final PointPeriodCalculator calculator = new PointPeriodCalculator(new BusinessClock(
            Clock.fixed(LocalDateTime.of(2026, 9, 15, 14, 0).toInstant(ZoneOffset.UTC), ZoneId.of("UTC"))));

    @Test
    @DisplayName("from/to 를 주면 [from 04:00, to+1일 04:00)")
    void explicitRange() {
        LocalDateTime[] range = calculator.toDateRange(null, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        assertThat(range[0]).isEqualTo(LocalDateTime.of(2026, 9, 1, 4, 0));
        assertThat(range[1]).isEqualTo(LocalDateTime.of(2026, 10, 1, 4, 0));
    }

    @Test
    @DisplayName("THIS_MONTH → [이번 영업월 1일 04:00, null)")
    void thisMonth() {
        LocalDateTime[] range = calculator.toDateRange(PeriodFilter.THIS_MONTH, null, null);
        assertThat(range[0]).isEqualTo(LocalDateTime.of(2026, 9, 1, 4, 0));
        assertThat(range[1]).isNull();
    }

    @Test
    @DisplayName("LAST_MONTH → [지난 영업월 1일 04:00, 이번 영업월 1일 04:00)")
    void lastMonth() {
        LocalDateTime[] range = calculator.toDateRange(PeriodFilter.LAST_MONTH, null, null);
        assertThat(range[0]).isEqualTo(LocalDateTime.of(2026, 8, 1, 4, 0));
        assertThat(range[1]).isEqualTo(LocalDateTime.of(2026, 9, 1, 4, 0));
    }

    @Test
    @DisplayName("ALL / null → [null, null)")
    void all() {
        assertThat(calculator.toDateRange(PeriodFilter.ALL, null, null)).containsExactly(null, null);
        assertThat(calculator.toDateRange(null, null, null)).containsExactly(null, null);
    }

    @Test
    @DisplayName("from 만 있고 to 가 없으면 예외")
    void fromWithoutTo() {
        assertThatThrownBy(() -> calculator.toDateRange(null, LocalDate.of(2026, 9, 1), null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("to 가 from 보다 빠르면 예외")
    void toBeforeFrom() {
        assertThatThrownBy(() -> calculator.toDateRange(null, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 1)))
                .isInstanceOf(BusinessException.class);
    }
}
