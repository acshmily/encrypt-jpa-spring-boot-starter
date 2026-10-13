package com.github.acshmily.test.repo;

import com.github.acshmily.annotation.EncryptParam;
import com.github.acshmily.test.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /** @EncryptParam 在下标 0：基础等值查询 */
    Optional<User> findByPhone(@EncryptParam String phone);

    /** 回归用例：@EncryptParam 不在下标 0（1.x 缓存下标错位会加密错误参数） */
    Optional<User> findByIdAndPhone(Long id, @EncryptParam String phone);

    boolean existsByPhone(@EncryptParam String phone);

    long countByPhone(@EncryptParam String phone);

    User getByPhone(@EncryptParam String phone);

    List<User> findByName(String name);

    @Query("select u from User u where u.phone = :phone")
    List<User> searchByCustomQuery(@Param("phone") @EncryptParam String phone);

    Page<User> findAll(Pageable pageable);
}
