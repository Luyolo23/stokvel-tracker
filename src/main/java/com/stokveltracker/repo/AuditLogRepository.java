package com.stokveltracker.repo;

import com.stokveltracker.domain.AuditLog;
import com.stokveltracker.domain.StokvelGroup;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findTop50ByGroupOrderByCreatedAtDesc(StokvelGroup group);

    /** One-time migration for audit rows created before entries were group-scoped. */
    @Modifying
    @Query("update AuditLog a set a.group = :group where a.group is null")
    int assignUngroupedTo(@Param("group") StokvelGroup group);
}
