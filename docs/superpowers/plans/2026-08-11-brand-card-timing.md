# 大牌券精确时序 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将大牌券自动领取固定为 09:30:00 开始、最多 5 次，并记录实际领取窗口开始时间。

**Architecture:** `BrandCardClaimServiceImpl` 负责在准备 cron 时创建任务和解析域名；`BrandCardClaimExecutor` 负责目标时刻后的五次受控请求。服务层在落库时使用目标时刻作为执行历史开始时间。

**Tech Stack:** Java 17, Spring Boot, Hutool HTTP, JUnit 5, Vue 3。

---

### Task 1: 锁定五次上限与首请求时刻

**Files:**
- Modify: `src/test/java/io/github/xiaocan/service/BrandCardClaimExecutorTest.java`
- Modify: `src/main/java/io/github/xiaocan/service/BrandCardClaimExecutor.java`

- [ ] **Step 1: 写失败测试**

```java
BrandCardClaimExecutionResult result = executor.executeAutomatic(
        126938104L, "token", 12, Duration.ofMillis(100), Duration.ofMillis(400));
assertEquals(5, result.attempts());
assertEquals(target, callTimes.get(0));
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `mvn -q -Dtest=BrandCardClaimExecutorTest test`

Expected: 请求次数为旧配置的 12 次，断言失败。

- [ ] **Step 3: 实现服务端五次上限**

```java
private static final int AUTOMATIC_MAX_ATTEMPTS = 5;
int attemptsLimit = Math.min(maxAttempts, AUTOMATIC_MAX_ATTEMPTS);
```

- [ ] **Step 4: 运行测试并确认通过**

Run: `mvn -q -Dtest=BrandCardClaimExecutorTest test`

Expected: PASS。

### Task 2: 修正准备时间和执行历史

**Files:**
- Modify: `src/main/java/io/github/xiaocan/service/impl/BrandCardClaimServiceImpl.java`
- Modify: `src/main/java/io/github/xiaocan/http/XiaochanHttp.java`
- Test: `src/test/java/io/github/xiaocan/service/BrandCardClaimExecutorTest.java`

- [ ] **Step 1: 将默认准备 cron 改为 09:29:55**

```java
private static final String DEFAULT_CRON = "55 29 9 * * ?";
```

- [ ] **Step 2: 准备阶段仅做 DNS 预解析**

```java
XiaochanHttp.warmBrandCardEndpoint();
```

- [ ] **Step 3: 使用目标时刻保存历史开始时间**

```java
saveHistory(config, LocalDateTime.ofInstant(target, APP_ZONE), result);
```

- [ ] **Step 4: 运行全量后端测试**

Run: `mvn -q test`

Expected: PASS。

### Task 3: 固化默认配置与页面提示

**Files:**
- Modify: `src/main/java/io/github/xiaocan/service/impl/BrandCardClaimServiceImpl.java`
- Modify: `src/main/java/io/github/xiaocan/model/dto/BrandCardClaimConfigDTO.java`
- Modify: `frontend/src/views/BrandCardClaimView.vue`

- [ ] **Step 1: 将默认最大次数改为 5**

```java
private static final int DEFAULT_MAX_ATTEMPTS = 5;
```

- [ ] **Step 2: 页面显示固定五次和实际首次请求时间**

```text
09:29:55 准备，09:30:00.000 第一次请求，最多 5 次。
```

- [ ] **Step 3: 构建前端**

Run: `npm run build`

Expected: PASS。

### Task 4: 提交与发布

**Files:**
- Modify: 上述文件

- [ ] **Step 1: 校验改动**

Run: `git diff --check && docker compose config -q`

Expected: PASS。

- [ ] **Step 2: 提交并同步部署分支**

```bash
git commit -m "fix: tighten brand card claim timing"
git push origin main
git push origin feature/auto-grab
```
