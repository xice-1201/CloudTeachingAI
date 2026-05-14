package com.cloudteachingai.auth.repository;

import com.cloudteachingai.auth.entity.VerificationCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface VerificationCodeRepository extends JpaRepository<VerificationCode, Long> {

    /**
     * 查找邮箱最新的有效验证码
     */
    Optional<VerificationCode> findTopByEmailAndUsedFalseOrderByCreatedAtDesc(String email);

    /**
     * 标记邮箱所有验证码为已使用
     */
    @Modifying
    @Query("UPDATE VerificationCode v SET v.used = true WHERE v.email = :email")
    void markAllAsUsed(String email);

    void deleteByEmail(String email);

    /**
     * 删除过期的验证码
     */
    @Modifying
    @Query("DELETE FROM VerificationCode v WHERE v.expiresAt < :now")
    void deleteExpiredCodes(LocalDateTime now);
}
