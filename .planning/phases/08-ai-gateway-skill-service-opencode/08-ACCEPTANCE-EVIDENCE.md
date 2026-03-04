# Phase 08 Acceptance Evidence

This document captures timestamped command-level verification evidence for Phase 08 plans `01` through `04`.

## Evidence Index

| ID | Plan | Command | UTC Start | UTC End | Exit | Result |
|---|---|---|---|---|---|---|
| `08-01-v1` | 08-01 | `.\\mvnw.cmd -pl gateway "-Dtest=RouteKeyFactoryTest,RedisRouteOwnershipStoreTest" test` | 2026-03-04T14:06:47Z | 2026-03-04T14:06:51Z | 0 | BUILD SUCCESS, 6 tests passed |
| `08-01-v2` | 08-01 | `rg "route_version|skill_owner|gateway_owner|fenced_owner|tenant_id|session_id" gateway/src/main/java/com/chatcui/gateway/routing` | 2026-03-04T14:07:07Z | 2026-03-04T14:07:07Z | 0 | Route/fence field contract present |
| `08-02-v1` | 08-02 | `.\\mvnw.cmd -pl "gateway,skill-service" "-Dtest=BridgePersistencePublisherTest,CrossInstanceRelayIntegrationTest" test` | 2026-03-04T14:07:07Z | 2026-03-04T14:07:12Z | 0 | BUILD SUCCESS, gateway+skill relay tests passed |
| `08-02-v2` | 08-02 | `rg "session_id\\s*\\+.*turn_id\\s*\\+.*seq\\s*\\+.*topic|route_version|skill-service owner|gateway owner" gateway/src/main/java/com/chatcui/gateway skill-service/src/main/java/com/chatcui/skill` | 2026-03-04T14:07:13Z | 2026-03-04T14:07:13Z | 0 | Dedupe tuple + route version references present |
| `08-03-v1` | 08-03 | `.\\mvnw.cmd -pl "gateway,skill-service" "-Dtest=DeliveryAckStateMachineTest,DeliveryRetryQueueTest,ResumeCoordinatorTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` | 2026-03-04T14:07:31Z | 2026-03-04T14:07:35Z | 0 | BUILD SUCCESS, 16 gateway tests passed |
| `08-03-v2` | 08-03 | `rg "OWNER_FENCED|route_version|gateway_owner_accepted|client_delivered|client_delivery_timeout|ROUTE_REPLAY_WINDOW_EXPIRED" gateway/src/main/java/com/chatcui/gateway skill-service/src/main/java/com/chatcui/skill` | 2026-03-04T14:07:35Z | 2026-03-04T14:07:35Z | 0 | Ack/recovery/fence strings present |
| `08-04-v1` | 08-04 | `.\\mvnw.cmd -pl "gateway,skill-service" "-Dtest=SkillPersistenceForwardingIntegrationTest,TurnPersistenceServiceTest" test` | 2026-03-04T14:07:35Z | 2026-03-04T14:07:42Z | 0 | BUILD SUCCESS, 10 tests passed |
| `08-04-v2` | 08-04 | `rg "P08-(ROUTE|FENCE|RELAY|DEDUPE|ACK|RECOVERY|OBS)-01|chatcui\\.gateway|chatcui\\.skill|trace_id|route_version" .planning/phases/08-ai-gateway-skill-service-opencode/08-OBSERVABILITY-BASELINE.md .planning/phases/08-ai-gateway-skill-service-opencode/08-ACCEPTANCE-EVIDENCE.md .planning/phases/08-ai-gateway-skill-service-opencode/08-VERIFICATION.md` | 2026-03-04T14:09:09Z | 2026-03-04T14:09:10Z | 0 | Requirement IDs + observability strings verified across closure docs |

## Notes on Plan 03 Verification Command

The unadjusted plan-03 command initially failed in `skill-service` because those selected tests exist only in `gateway`.  
Execution used `-Dsurefire.failIfNoSpecifiedTests=false` to keep the command scope (`gateway,skill-service`) while preserving all targeted gateway assertions and successful module build.

## Command Output Excerpts

### 08-01-v1

```text
Running RedisRouteOwnershipStoreTest ... Tests run: 3, Failures: 0, Errors: 0
Running RouteKeyFactoryTest ... Tests run: 3, Failures: 0, Errors: 0
BUILD SUCCESS
```

### 08-02-v1

```text
Running BridgePersistencePublisherTest ... Tests run: 9, Failures: 0, Errors: 0
Running CrossInstanceRelayIntegrationTest ... Tests run: 3, Failures: 0, Errors: 0
BUILD SUCCESS
```

### 08-03-v1

```text
Running DeliveryRetryQueueTest ... Tests run: 6, Failures: 0, Errors: 0
Running DeliveryAckStateMachineTest ... Tests run: 3, Failures: 0, Errors: 0
Running ResumeCoordinatorTest ... Tests run: 7, Failures: 0, Errors: 0
BUILD SUCCESS
```

### 08-04-v1

```text
Running SkillPersistenceForwardingIntegrationTest ... Tests run: 4, Failures: 0, Errors: 0
Running TurnPersistenceServiceTest ... Tests run: 6, Failures: 0, Errors: 0
BUILD SUCCESS
```

## Requirement Trace Links

- Requirement truth mapping: `08-VERIFICATION.md`
- Observability metric inventory: `08-OBSERVABILITY-BASELINE.md`
