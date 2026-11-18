package com.leadpilot.user;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByRole(Role role);
    List<UserAccount> findAllByOrderByNameAscIdAsc();
    List<UserAccount> findByRoleAndActiveTrueOrderByNameAscIdAsc(Role role);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserAccount u where u.role = :role and u.active = true order by u.id")
    List<UserAccount> findActiveAccountsForUpdate(Role role);
}
