package com.cloudteachingai.auth.repository;

import com.cloudteachingai.auth.entity.AuthCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AuthCredentialRepository extends JpaRepository<AuthCredential, Long> {

    Optional<AuthCredential> findByEmail(String email);

    boolean existsByEmail(String email);

    void deleteByEmail(String email);
}
