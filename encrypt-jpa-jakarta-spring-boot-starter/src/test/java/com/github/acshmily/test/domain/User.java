package com.github.acshmily.test.domain;

import com.github.acshmily.annotation.BlindIndex;
import com.github.acshmily.annotation.Encrypt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User extends BaseSecretEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    /** 确定性加密（默认）：可配合 @EncryptParam 等值查询 */
    @Encrypt
    private String phone;

    /** phone 的盲索引影子列（2.1.0）：LIKE 模糊查询 */
    @BlindIndex("phone")
    @Column(name = "phone_bidx", length = 4000)
    private String phoneBidx;

    /** 随机 IV 加密：安全性最优；模糊查询走盲索引 */
    @Encrypt(random = true)
    @Column(length = 8000)
    private String email;

    public User() {
    }

    public User(String name, String phone, String email, String secretNote) {
        this.name = name;
        this.phone = phone;
        this.email = email;
        setSecretNote(secretNote);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
