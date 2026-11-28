package com.stokveltracker.service;

import com.stokveltracker.domain.AuditLog;
import com.stokveltracker.domain.StokvelGroup;
import com.stokveltracker.repo.AuditLogRepository;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final AuditLogRepository repo;

    public AuditService(AuditLogRepository repo) {
        this.repo = repo;
    }

    public void record(StokvelGroup group, String actor, String action, String detail) {
        repo.save(new AuditLog(group, actor, action, detail));
    }

    /** Keeps pre-existing audit rows visible when there is only one group to assign them to. */
    public void assignUngroupedTo(StokvelGroup group) {
        repo.assignUngroupedTo(group);
    }
}
