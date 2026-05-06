package com.cloudteachingai.user.service;

import com.cloudteachingai.user.dto.AdminAuditLogResponse;
import com.cloudteachingai.user.dto.PageResponse;
import com.cloudteachingai.user.entity.AdminAuditLog;
import com.cloudteachingai.user.entity.User;
import com.cloudteachingai.user.repository.AdminAuditLogRepository;
import com.cloudteachingai.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AdminAuditLogService {

    private final AdminAuditLogRepository adminAuditLogRepository;
    private final UserRepository userRepository;

    @Transactional
    public AdminAuditLogResponse record(Long actorId, String action, String targetType, Long targetId, String targetName, String detail) {
        String actorName = actorId == null
                ? null
                : userRepository.findById(actorId).map(User::getUsername).orElse("User-" + actorId);

        AdminAuditLog saved = adminAuditLogRepository.save(AdminAuditLog.builder()
                .actorId(actorId)
                .actorName(actorName)
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .targetName(targetName)
                .detail(detail)
                .build());
        return AdminAuditLogResponse.from(saved);
    }

    public PageResponse<AdminAuditLogResponse> listLogs(String keyword, String action, String targetType, int page, int pageSize) {
        Pageable pageable = PageRequest.of(toPageIndex(page), toPageSize(pageSize), Sort.by(Sort.Direction.DESC, "createdAt"));
        Specification<AdminAuditLog> specification = Specification
                .where(withKeyword(keyword))
                .and(withAction(action))
                .and(withTargetType(targetType));
        Page<AdminAuditLog> logs = adminAuditLogRepository.findAll(specification, pageable);
        return PageResponse.<AdminAuditLogResponse>builder()
                .items(logs.getContent().stream().map(AdminAuditLogResponse::from).toList())
                .total((int) logs.getTotalElements())
                .page(page)
                .pageSize(pageSize)
                .build();
    }

    private int toPageIndex(int page) {
        return Math.max(page, 1) - 1;
    }

    private int toPageSize(int pageSize) {
        return Math.max(1, Math.min(pageSize, 100));
    }

    private Specification<AdminAuditLog> withKeyword(String keyword) {
        return (root, query, criteriaBuilder) -> {
            if (!StringUtils.hasText(keyword)) {
                return criteriaBuilder.conjunction();
            }
            String pattern = "%" + keyword.trim().toLowerCase() + "%";
            return criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("actorName")), pattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("targetName")), pattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("detail")), pattern)
            );
        };
    }

    private Specification<AdminAuditLog> withAction(String action) {
        return (root, query, criteriaBuilder) -> StringUtils.hasText(action)
                ? criteriaBuilder.equal(root.get("action"), action.trim())
                : criteriaBuilder.conjunction();
    }

    private Specification<AdminAuditLog> withTargetType(String targetType) {
        return (root, query, criteriaBuilder) -> StringUtils.hasText(targetType)
                ? criteriaBuilder.equal(root.get("targetType"), targetType.trim())
                : criteriaBuilder.conjunction();
    }
}
