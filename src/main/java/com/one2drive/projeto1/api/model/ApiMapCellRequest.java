package com.one2drive.projeto1.api.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/** One road map cell to paint: row along the road, lane name and legend code (0 frees it). */
public record ApiMapCellRequest(
    @Min(0) int row,
    @NotBlank String lane,
    @Min(0) int code
) {
}
