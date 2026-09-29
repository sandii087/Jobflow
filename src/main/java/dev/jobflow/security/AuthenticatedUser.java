package dev.jobflow.security; import dev.jobflow.domain.Role; import java.util.UUID; public record AuthenticatedUser(UUID id,Role role){}
