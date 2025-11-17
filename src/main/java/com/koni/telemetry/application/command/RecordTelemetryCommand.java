package com.koni.telemetry.application.command;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Command to record telemetry data from a device.
 * This command represents the intent to persist temperature measurement data.
 */
@Getter
@AllArgsConstructor
public class RecordTelemetryCommand {

    /**
     * The unique identifier of the device sending the telemetry.
     */
    @NotNull(message = "deviceId is required")
    private final Long deviceId;

    /**
     * The temperature measurement value.
     */
    @NotNull(message = "measurement is required")
    private final BigDecimal measurement;

    /**
     * The timestamp when the measurement was taken.
     */
    @NotNull(message = "date is required")
    private final Instant date;
}
