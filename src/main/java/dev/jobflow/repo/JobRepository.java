package dev.jobflow.repo;
import dev.jobflow.domain.*; import java.time.Instant; import java.util.*; import org.springframework.data.domain.*; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param;
public interface JobRepository extends JpaRepository<Job,UUID>{
 Optional<Job> findByIdAndOwnerId(UUID id,UUID ownerId); Optional<Job> findByOwnerIdAndIdempotencyKey(UUID ownerId,String key); Page<Job> findByOwnerId(UUID ownerId,Pageable page); Page<Job> findByStatus(JobStatus status,Pageable page);
 @Query(value="SELECT id FROM jobs WHERE status IN ('QUEUED','RETRY_WAIT') AND available_at <= clock_timestamp() ORDER BY (CASE priority WHEN 'HIGH' THEN 120 WHEN 'MEDIUM' THEN 60 ELSE 0 END + FLOOR(EXTRACT(EPOCH FROM (clock_timestamp()-created_at))/60)) DESC, created_at ASC FOR UPDATE SKIP LOCKED LIMIT 1",nativeQuery=true) Optional<UUID> lockNextEligibleId();
 @Query("select j from Job j where j.status = 'PROCESSING' and j.leaseExpiresAt < :now") List<Job> findExpiredLeases(@Param("now") Instant now);
 long countByStatus(JobStatus status); long countByOwnerId(UUID ownerId); long countByOwnerIdAndStatus(UUID ownerId, JobStatus status);
}
