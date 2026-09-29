package dev.jobflow.domain;
import jakarta.persistence.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="users") public class AppUser {
 @Id private UUID id; @Column(nullable=false, unique=true) private String email; @Column(name="password_hash",nullable=false) private String passwordHash; @Enumerated(EnumType.STRING) @Column(nullable=false) private Role role; @Column(name="created_at",nullable=false) private Instant createdAt;
 protected AppUser(){} public AppUser(String email,String passwordHash,Role role){this.id=UUID.randomUUID();this.email=email;this.passwordHash=passwordHash;this.role=role;this.createdAt=Instant.now();}
 public UUID getId(){return id;} public String getEmail(){return email;} public String getPasswordHash(){return passwordHash;} public Role getRole(){return role;}
}
