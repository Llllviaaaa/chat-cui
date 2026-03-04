package com.chatcui.gateway.routing;

import java.util.Objects;

public record RouteCasResult(
        Status status,
        RouteOwnershipRecord record,
        long expectedRouteVersion,
        Long currentRouteVersion) {

    public enum Status {
        APPLIED,
        VERSION_CONFLICT,
        MISSING
    }

    public RouteCasResult {
        status = Objects.requireNonNull(status, "status must not be null");
        if (status == Status.APPLIED && record == null) {
            throw new IllegalArgumentException("record must be present when status is APPLIED");
        }
        if (status == Status.VERSION_CONFLICT && record == null) {
            throw new IllegalArgumentException("record must be present when status is VERSION_CONFLICT");
        }
        if (status != Status.MISSING && currentRouteVersion == null) {
            throw new IllegalArgumentException("currentRouteVersion must be present when route exists");
        }
    }

    public static RouteCasResult applied(RouteOwnershipRecord record) {
        return new RouteCasResult(
                Status.APPLIED,
                Objects.requireNonNull(record, "record must not be null"),
                record.routeVersion(),
                record.routeVersion());
    }

    public static RouteCasResult versionConflict(long expectedRouteVersion, RouteOwnershipRecord currentRecord) {
        RouteOwnershipRecord conflictRecord = Objects.requireNonNull(currentRecord, "currentRecord must not be null");
        return new RouteCasResult(
                Status.VERSION_CONFLICT,
                conflictRecord,
                expectedRouteVersion,
                conflictRecord.routeVersion());
    }

    public static RouteCasResult missing(long expectedRouteVersion) {
        return new RouteCasResult(Status.MISSING, null, expectedRouteVersion, null);
    }

    public boolean applied() {
        return status == Status.APPLIED;
    }

    public boolean versionConflict() {
        return status == Status.VERSION_CONFLICT;
    }
}
