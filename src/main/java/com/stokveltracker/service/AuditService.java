package com.stokveltracker.service;

import com.stokveltracker.domain.AuditLog;
import com.stokveltracker.repo.AuditLogRepository;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final AuditLogRepository repo;

    public AuditService(AuditLogRepository repo) {
        this.repo = repo;
    }

    public void record(String actor, String action, String detail) {
        repo.save(new AuditLog(actor, action, detail));
    }
}
