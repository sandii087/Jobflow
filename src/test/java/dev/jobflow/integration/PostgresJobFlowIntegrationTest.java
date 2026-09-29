package dev.jobflow.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jobflow.api.JobDtos.CreateJobRequest;
import dev.jobflow.domain.*;
import dev.jobflow.repo.*;
import dev.jobflow.service.JobService;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class PostgresJobFlowIntegrationTest {
  @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
  @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("jobflow.jwt-secret", () -> "test-signing-secret-must-be-at-least-thirty-two-bytes");
    registry.add("jobflow.worker.enabled", () -> false);
    registry.add("jobflow.rate-limit.submissions-per-minute", () -> 100);
  }

  @Autowired private JobService jobs;
  @Autowired private UserRepository users;
  @Autowired private JobRepository jobRepository;
  @Autowired private ObjectMapper json;

  @Test void flywayMigrationsAndConcurrentIdempotencyProduceOneJob() throws Exception {
    AppUser user = users.save(new AppUser("concurrent@example.test", "not-a-real-password-hash", Role.USER));
    CreateJobRequest request = new CreateJobRequest(JobType.DATA_PROCESSING, json.readTree("{\"name\":\"test\"}"), JobPriority.MEDIUM, 3);
    ExecutorService workers = Executors.newFixedThreadPool(8);
    try {
      List<Future<UUID>> results = workers.invokeAll(Collections.nCopies(8,
          () -> jobs.create(user.getId(), request, "same-logical-request").getId()));
      Set<UUID> ids = new HashSet<>();
      for (Future<UUID> result : results) ids.add(result.get());
      assertEquals(1, ids.size());
      assertEquals(1, jobRepository.count());
    } finally { workers.shutdownNow(); }
  }

  @Test void competingWorkersClaimDistinctRows() throws Exception {
    AppUser user = users.save(new AppUser("claims@example.test", "not-a-real-password-hash", Role.USER));
    for (int i = 0; i < 6; i++) {
      jobs.create(user.getId(), new CreateJobRequest(JobType.DATA_PROCESSING,
          json.readTree("{\"name\":\"job" + i + "\"}"), JobPriority.MEDIUM, 3), "claim-" + i);
    }
    ExecutorService workers = Executors.newFixedThreadPool(6);
    try {
      List<Future<Optional<Job>>> claims = workers.invokeAll(Collections.nCopies(6,
          () -> jobs.claimNext(Duration.ofMinutes(1))));
      Set<UUID> ids = new HashSet<>();
      for (Future<Optional<Job>> claim : claims) ids.add(claim.get().orElseThrow().getId());
      assertEquals(6, ids.size());
      assertEquals(6, jobRepository.countByStatus(JobStatus.PROCESSING));
    } finally { workers.shutdownNow(); }
  }

  @Test void expiredLeaseIsRecoveredForRetry() throws Exception {
    AppUser user = users.save(new AppUser("recovery@example.test", "not-a-real-password-hash", Role.USER));
    Job queued = jobs.create(user.getId(), new CreateJobRequest(JobType.DATA_PROCESSING,
        json.readTree("{\"name\":\"recovery\"}"), JobPriority.LOW, 2), "recovery-key");
    assertEquals(queued.getId(), jobs.claimNext(Duration.ofSeconds(-1)).orElseThrow().getId());
    assertEquals(1, jobs.recoverExpired().size());
    assertEquals(JobStatus.RETRY_WAIT, jobRepository.findById(queued.getId()).orElseThrow().getStatus());
  }
}
