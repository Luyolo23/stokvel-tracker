package com.stokveltracker.repo;

import com.stokveltracker.domain.AppUser;
import com.stokveltracker.domain.Contribution;
import com.stokveltracker.domain.ContributionStatus;
import com.stokveltracker.domain.Role;
import com.stokveltracker.domain.StokvelGroup;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContributionRepository extends JpaRepository<Contribution, Long> {

    boolean existsByUserAndPeriod(AppUser user, String period);

    List<Contribution> findByUserOrderByPeriodDesc(AppUser user);

    @Query("select max(c.period) from Contribution c where c.user.group = :group")
    String findMaxPeriodByGroup(@Param("group") StokvelGroup group);

    @Query("select c from Contribution c where c.user.group = :group")
    List<Contribution> findAllByGroup(@Param("group") StokvelGroup group);

    @Query("select c from Contribution c where c.user.group = :group and c.status = :status")
    List<Contribution> findByGroupAndStatus(@Param("group") StokvelGroup group,
                                            @Param("status") ContributionStatus status);

    @Query("select c from Contribution c where c.user.group = :group and c.status = :status")
    Page<Contribution> findPageByGroupAndStatus(@Param("group") StokvelGroup group,
                                                @Param("status") ContributionStatus status,
                                                Pageable pageable);

    @Query("select c from Contribution c where c.user.group = :group "
         + "and c.user.role = :role and c.status = :status")
    List<Contribution> findByGroupRoleAndStatus(@Param("group") StokvelGroup group,
                                                @Param("role") Role role,
                                                @Param("status") ContributionStatus status);

    @Query("select c from Contribution c where c.user.group = :group "
         + "and c.user.role = :role and c.status = :status")
    Page<Contribution> findPageByGroupRoleAndStatus(@Param("group") StokvelGroup group,
                                                    @Param("role") Role role,
                                                    @Param("status") ContributionStatus status,
                                                    Pageable pageable);

    @Query("select c from Contribution c where c.user.group = :group "
         + "and c.status in :statuses and c.dueDate < :today order by c.dueDate")
    List<Contribution> findOverdue(@Param("group") StokvelGroup group,
                                   @Param("statuses") Collection<ContributionStatus> statuses,
                                   @Param("today") LocalDate today);
}
