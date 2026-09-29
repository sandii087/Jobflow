package dev.jobflow.service;

import dev.jobflow.api.JobDtos.CreateJobRequest;
import dev.jobflow.domain.*;
import dev.jobflow.repo.*;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Keeps a losing idempotency insert isolated from the follow-up winner lookup. */
@Service
public class JobCreationPersistence {
  private final JobRepository jobs;
  private final UserRepository users;

  public JobCreationPersistence(JobRepository jobs, UserRepository users) { this.jobs = jobs; this.users = users; }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Job insert(UUID ownerId, CreateJobRequest request, String key) {
    AppUser owner = users.getReferenceById(ownerId);
    return jobs.saveAndFlush(new Job(owner, request.type(), request.payload(),
        request.priority() == null ? JobPriority.MEDIUM : request.priority(),
        request.maxAttempts() == null ? 3 : request.maxAttempts(), key));
  }
}
