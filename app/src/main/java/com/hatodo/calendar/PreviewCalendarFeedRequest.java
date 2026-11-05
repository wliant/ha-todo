package com.hatodo.calendar;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PreviewCalendarFeedRequest(@NotBlank @Size(max = 2000) String url) {
}
