package com.langxi.babydiary.platform.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.langxi.babydiary.platform.infrastructure.BackgroundJobMapper;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class BackgroundJobWorker {
  private static final Logger log = LoggerFactory.getLogger(BackgroundJobWorker.class);
  private final BackgroundJobMapper jobs;
  private final ObjectMapper json;
  private final TransactionTemplate transactions;
  private final Map<String, BackgroundJobHandler> handlers;
  private final List<String> handlerTypes;
  private final boolean enabled;
  private final String workerId;
  private final int staleMinutes;

  public BackgroundJobWorker(
      BackgroundJobMapper jobs,
      ObjectMapper json,
      TransactionTemplate transactions,
      List<BackgroundJobHandler> handlers,
      @Value("${app.jobs.enabled:true}") boolean enabled,
      @Value("${app.jobs.worker-id:single-node}") String workerId,
      @Value("${app.jobs.stale-after-minutes:30}") int staleMinutes) {
    this.jobs = jobs;
    this.json = json;
    this.transactions = transactions;
    this.enabled = enabled;
    this.workerId =
        workerId == null ? "single-node" : workerId.substring(0, Math.min(workerId.length(), 40));
    this.staleMinutes = Math.max(5, staleMinutes);
    Map<String, BackgroundJobHandler> values = new LinkedHashMap<>();
    for (BackgroundJobHandler handler : handlers) {
      if (values.putIfAbsent(handler.type(), handler) != null) {
        throw new IllegalStateException("Duplicate background job handler: " + handler.type());
      }
    }
    this.handlers = Map.copyOf(values);
    this.handlerTypes = this.handlers.keySet().stream().sorted().toList();
  }

  @Scheduled(fixedDelayString = "${app.jobs.poll-delay-ms:2000}")
  public void poll() {
    if (!enabled || handlerTypes.isEmpty()) return;
    String claim = workerId + ":" + UUID.randomUUID();
    BackgroundJobMapper.JobRow job =
        transactions.execute(
            status -> {
              LocalDateTime now = now();
              return jobs.claim(claim, now, handlerTypes) == 1 ? jobs.findClaimed(claim) : null;
            });
    if (job == null) return;
    execute(job, claim);
  }

  @Scheduled(fixedDelayString = "${app.jobs.recovery-delay-ms:300000}")
  public void recoverStale() {
    if (!enabled) return;
    LocalDateTime now = now();
    LocalDateTime staleBefore = now.minusMinutes(staleMinutes);
    transactions.executeWithoutResult(
        status -> {
          int retried = jobs.recoverRetryable(staleBefore, now);
          int failed = jobs.failExhausted(staleBefore, now);
          if (retried + failed > 0)
            log.warn("Recovered {} stale jobs; {} exhausted", retried, failed);
        });
  }

  private void execute(BackgroundJobMapper.JobRow job, String claim) {
    BackgroundJobHandler handler = handlers.get(job.jobType());
    if (handler == null) {
      fail(job, claim, new IllegalStateException("No handler registered for " + job.jobType()));
      return;
    }
    try {
      JsonNode payload = json.readTree(job.payload());
      JsonNode result = handler.handle(payload);
      String serialized =
          json.writeValueAsString(result == null ? json.createObjectNode() : result);
      transactions.executeWithoutResult(
          status -> {
            if (jobs.succeed(job.jobId(), claim, serialized, now()) != 1) {
              throw new IllegalStateException("Background job claim was lost");
            }
          });
    } catch (Exception exception) {
      fail(job, claim, exception);
    }
  }

  private void fail(BackgroundJobMapper.JobRow job, String claim, Exception exception) {
    boolean terminal = job.attemptCount() >= job.maxAttempts();
    long delaySeconds = Math.min(3600, 15L * (1L << Math.min(job.attemptCount() - 1, 7)));
    LocalDateTime now = now();
    String message =
        exception.getClass().getSimpleName()
            + ": "
            + (exception.getMessage() == null ? "未知错误" : exception.getMessage());
    message = message.substring(0, Math.min(message.length(), 2000));
    String error = message;
    transactions.executeWithoutResult(
        status ->
            jobs.fail(job.jobId(), claim, terminal, now.plusSeconds(delaySeconds), error, now));
    log.warn(
        "Background job {} ({}) failed on attempt {}/{}",
        job.jobId(),
        job.jobType(),
        job.attemptCount(),
        job.maxAttempts(),
        exception);
  }

  private LocalDateTime now() {
    return LocalDateTime.now(ZoneOffset.UTC);
  }
}
