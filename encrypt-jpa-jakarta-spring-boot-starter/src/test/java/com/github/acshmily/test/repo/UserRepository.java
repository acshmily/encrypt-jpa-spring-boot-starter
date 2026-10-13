package com.github.acshmily.test.repo;

import com.github.acshmily.annotation.BlindIndexParam;
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

    Optional<User> findByPhone(@EncryptParam String phone);

    Optional<User> findByIdAndPhone(Long id, @EncryptParam String phone);

    boolean existsByPhone(@EncryptParam String phone);

    List<User> findByName(String name);

    @Query("select u from User u where u.phone = :phone")
    List<User> searchByCustomQuery(@Param("phone") @EncryptParam String phone);

    Page<User> findAll(Pageable pageable);

    /** 盲索引模糊查询（2.1.0） */
    List<User> findByPhoneBidxContaining(@BlindIndexParam String keyword);
}
