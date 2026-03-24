package com.cloudteachingai.user.repository;

import com.cloudteachingai.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Page<User> findByRole(User.UserRole role, Pageable pageable);

    Page<User> findByRoleAndUsernameContainingIgnoreCase(User.UserRole role, String keyword, Pageable pageable);
}
