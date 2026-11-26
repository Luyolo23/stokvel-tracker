package com.stokveltracker.repo;

import com.stokveltracker.domain.StokvelGroup;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupRepository extends JpaRepository<StokvelGroup, Long> {}
