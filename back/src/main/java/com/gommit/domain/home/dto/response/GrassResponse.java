package com.gommit.domain.home.dto.response;

import java.time.LocalDate;

public record GrassResponse(LocalDate date, int checkInCount, int level) {}
