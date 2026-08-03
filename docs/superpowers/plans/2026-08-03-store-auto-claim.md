# 门店自动抢单 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让指定门店监控能按返利和评价条件自动选择活动、在可抢时间调用小蚕抢单接口，并保存可审计的抢单记录。

**Architecture:** 自动抢配置保存在既有 `monitor_config.ext_config` 的 `StoreExtNotifyConfig` 内。候选选择、HTTP 调用、执行重试、持久记录和监控调度分离，普通提醒流程不变。

**Tech Stack:** Spring Boot 3、MyBatis-Plus、Hutool HTTP、MySQL 8、Vue 3、Element Plus、JUnit 5。

---

### Task 1: 自动抢配置与候选选择

**Files:**
- Modify: `src/main/java/io/github/xiaocan/model/StoreExtNotifyConfig.java`
- Create: `src/main/java/io/github/xiaocan/model/StoreAutoClaimConfig.java`
- Create: `src/main/java/io/github/xiaocan/service/StoreAutoClaimCandidateSelector.java`
- Test: `src/test/java/io/github/xiaocan/service/StoreAutoClaimCandidateSelectorTest.java`

- [x] **Step 1: Write failing tests**

```java
@Test
void choosesReviewOnlyWhenItsBestRebateIsStrictlyHigher() {
    assertSame(review(15), selector.select(List.of(noReview(12), review(15))).orElseThrow());
}

@Test
void choosesNoReviewWhenRebatesAreEqual() {
    assertSame(noReview(12), selector.select(List.of(review(12), noReview(12))).orElseThrow());
}
```

- [x] **Step 2: Verify RED**

Run: `mvn -q -Dtest=StoreAutoClaimCandidateSelectorTest test`

Expected: compilation failure because the selector does not exist.

- [x] **Step 3: Implement minimal production code**

```java
@Data
public class StoreAutoClaimConfig {
    private Boolean enabled = false;
    private Integer maxAttempts = 5;
    private Integer minIntervalMs = 150;
    private Integer maxIntervalMs = 350;
}

public Optional<StoreInfo> select(List<StoreInfo> stores) {
    StoreInfo review = best(stores, 2);
    StoreInfo noReview = best(stores, 99);
    if (review == null) return Optional.ofNullable(noReview);
    return Optional.of(noReview == null || review.getRebatePrice().compareTo(noReview.getRebatePrice()) > 0
            ? review : noReview);
}
```

Add `private StoreAutoClaimConfig autoClaimConfig = new StoreAutoClaimConfig();` to `StoreExtNotifyConfig`.

- [x] **Step 4: Verify GREEN**

Run: `mvn -q -DskipTests=false test`

- [x] **Step 5: Commit**

```bash
git add src/main src/test
git commit -m "feat: add auto claim candidate selection"
```

### Task 2: 抢单 HTTP 合约与重试执行器

**Files:**
- Modify: `src/main/java/io/github/xiaocan/http/XiaochanHttp.java`
- Create: `src/main/java/io/github/xiaocan/model/StoreAutoClaimAttempt.java`
- Create: `src/main/java/io/github/xiaocan/model/StoreAutoClaimResult.java`
- Create: `src/main/java/io/github/xiaocan/model/StoreAutoClaimStopReason.java`
- Create: `src/main/java/io/github/xiaocan/service/StoreAutoClaimClient.java`
- Create: `src/main/java/io/github/xiaocan/service/StoreAutoClaimExecutor.java`
- Test: `src/test/java/io/github/xiaocan/http/XiaochanHttpStoreAutoClaimTest.java`
- Test: `src/test/java/io/github/xiaocan/service/StoreAutoClaimExecutorTest.java`

- [x] **Step 1: Write failing contract and retry tests**

```java
@Test
void omitsRedpackIdWhenThereIsNoEligibleRedpack() {
    var request = XiaochanHttp.buildStoreAutoClaimRequest(parts(null));
    assertEquals("SilkwormService.GrabPromotionQuota", request.headers().get("methodname"));
    assertFalse(request.body().contains("redpack_id"));
}

@Test
void retriesOnlyTransportFailuresUntilSuccess() {
    var result = executor.execute(parts, 5);
    assertTrue(result.success());
    assertEquals(3, result.attempts());
}
```

- [x] **Step 2: Verify RED**

Run: `mvn -q -Dtest=XiaochanHttpStoreAutoClaimTest,StoreAutoClaimExecutorTest test`

Expected: compilation failure because the store-claim API is absent.

- [x] **Step 3: Implement the exact HAR contract**

Build `SilkwormService.GrabPromotionQuota` with `city_code`, `if_advance_order`, `if_pre_order`, `latitude`, `longitude`, `promotion_id`, `silk_id`, and `store_platform`; add `redpack_id` only when non-null. Generate new `X-Garen`, `X-Nami`, `X-Ashe`, and `X-Session-Id` per request, and carry `X-Sivir` and `x-Teemo`.

```java
if (Objects.equals(code, 0)) return StoreAutoClaimAttempt.success(orderId, message);
if (transportFailure) return StoreAutoClaimAttempt.retryable(message);
return StoreAutoClaimAttempt.stop(code, message, BUSINESS_FAILURE);
```

Non-zero business responses, verification, authentication failure, sold-out and expired states stop without retry. Only transport exceptions retry with a random 150-350ms delay, capped by configuration.

- [x] **Step 4: Verify GREEN**

Run: `mvn -q -DskipTests=false test`

- [x] **Step 5: Commit**

```bash
git add src/main src/test
git commit -m "feat: add store auto claim http executor"
```

### Task 3: 红包预检、执行记录和 API

**Files:**
- Create: `src/main/java/io/github/xiaocan/model/entity/StoreAutoClaimHistoryEntity.java`
- Create: `src/main/java/io/github/xiaocan/mapper/StoreAutoClaimHistoryMapper.java`
- Create: `src/main/java/io/github/xiaocan/model/dto/StoreAutoClaimHistoryQueryDTO.java`
- Create: `src/main/java/io/github/xiaocan/model/vo/StoreAutoClaimHistoryVO.java`
- Create: `src/main/java/io/github/xiaocan/service/StoreAutoClaimService.java`
- Create: `src/main/java/io/github/xiaocan/service/impl/StoreAutoClaimServiceImpl.java`
- Create: `src/main/java/io/github/xiaocan/controller/StoreAutoClaimController.java`
- Modify: `ddl.sql`
- Create: `deploy/migrations/20260803_store_auto_claim.sql`
- Test: `src/test/java/io/github/xiaocan/service/StoreAutoClaimServiceTest.java`

- [x] **Step 1: Write failing service tests**

```java
@Test
void savesReturnedOrderIdOnSuccess() {
    service.execute(config, location, candidate);
    verify(historyMapper).insert(argThat(row -> row.getPromotionOrderId() != null));
}

@Test
void recordsMissingCredentialsWithoutSendingAClaim() {
    assertEquals(MISSING_CREDENTIALS, service.execute(config, location, candidate).stopReason());
    verifyNoInteractions(claimClient);
}
```

- [x] **Step 2: Verify RED**

Run: `mvn -q -Dtest=StoreAutoClaimServiceTest test`

- [x] **Step 3: Implement preflight and history**

Before claim, load the current user's existing `BrandCardClaimConfigEntity`; require its `silkId` and `X-Sivir`. Query `RedPackService.GetOrderUserRedPackList`; pass the first `available_items[].user_red_pack_id` only when present. Persist user/config/activity/order identifiers, selected condition and rebate, schedule and execution timestamps, attempts, code/message, success and stop reason. Add `POST /api/store-auto-claim/history/page` and restrict it to the current user's rows.

- [x] **Step 4: Verify GREEN**

Run: `mvn -q -DskipTests=false test`

Run: `docker compose config -q`

- [x] **Step 5: Commit**

```bash
git add src/main ddl.sql deploy/migrations src/test
git commit -m "feat: persist store auto claim history"
```

### Task 4: 监控与到点调度

**Files:**
- Create: `src/main/java/io/github/xiaocan/tasks/StoreAutoClaimTask.java`
- Modify: `src/main/java/io/github/xiaocan/tasks/StoreTask.java`
- Test: `src/test/java/io/github/xiaocan/tasks/StoreAutoClaimTaskTest.java`

- [x] **Step 1: Write failing scheduling tests**

```java
@Test
void schedulesFutureCandidateAtStartWithoutClaimingEarly() {
    task.inspect(config, location, List.of(futureCandidate));
    verify(scheduler).schedule(any(Runnable.class), eq(expectedStart));
    verifyNoInteractions(autoClaimService);
}

@Test
void claimsCurrentCandidateOnlyOnce() {
    task.inspect(config, location, List.of(currentCandidate));
    verify(autoClaimService).execute(config, location, currentCandidate);
}
```

- [x] **Step 2: Verify RED**

Run: `mvn -q -Dtest=StoreAutoClaimTaskTest test`

- [x] **Step 3: Implement scheduling**

Poll only enabled specified-store auto-claim configurations every 10 seconds, respecting their existing time/day/status restrictions. Find candidates using the same name and `uniqId` rules as `StoreTask`. For a future activity register one runnable keyed by `configId:promotionId`; for an active activity run through the task scheduler. Remove the key in `finally`, preventing duplicate queued/running claims. Call the same inspection after the normal `StoreTask` finds activities, without changing its notification behavior.

- [x] **Step 4: Verify GREEN**

Run: `mvn -q -DskipTests=false test`

- [x] **Step 5: Commit**

```bash
git add src/main src/test
git commit -m "feat: trigger automatic claims from store monitoring"
```

### Task 5: 监控管理和部署闭环

**Files:**
- Modify: `frontend/src/views/HomeView.vue`
- Modify: `frontend/src/views/MonitorConfigView.vue`
- Modify: `README.md`
- Modify: `ddl.sql`

- [x] **Step 1: Verify available frontend test tooling**

Run: `npm run`

Expected: inspect whether a component-test script exists; if absent, use the production build as the automated frontend verification.

- [x] **Step 2: Implement configuration and history UI**

Add an “自动抢单” switch to the specified-store monitor dialog. Persist `autoClaimConfig` while retaining existing `storeInfo` and `remindFrequency`. Display the enabled state in monitor management and add a “抢单记录” command opening a paginated dialog backed by the history API. Rows show selected activity, evaluation type, rebate, attempts, result message, order number and execution time. Never render full `X-Sivir`.

- [x] **Step 3: Document operations**

Document: configure `silk_id` and `X-Sivir` in the existing 大牌券 page; create a specified-store monitor; enable auto claim; inspect histories; run the SQL migration before rebuilding an existing Docker deployment. State that verification/risk responses stop the task and success is not guaranteed.

- [x] **Step 4: Verify build and deployment files**

Run: `npm ci --include=dev --registry=https://registry.npmjs.org && npm run build`

Run: `mvn -q -DskipTests=false test`

Run: `git diff --check`

Run: `docker compose config -q`

- [ ] **Step 5: Commit**

```bash
git add frontend README.md ddl.sql deploy/migrations
git commit -m "feat: add automatic store claim operations"
```
