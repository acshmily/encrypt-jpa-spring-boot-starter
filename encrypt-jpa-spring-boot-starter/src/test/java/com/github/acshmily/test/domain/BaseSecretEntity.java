package com.github.acshmily.test.domain;

import com.github.acshmily.annotation.Encrypt;

import javax.persistence.Column;
import javax.persistence.MappedSuperclass;

/**
 * 父类带 @Encrypt 字段：验证 2.0.0 的类层级字段扫描
 * （1.x 只扫 getDeclaredFields，父类字段不会被加密）。
 */
@MappedSuperclass
public abstract class BaseSecretEntity {

    @Encrypt
    @Column(length = 8000)
    private String secretNote;

    public String getSecretNote() {
        return secretNote;
    }

    public void setSecretNote(String secretNote) {
        this.secretNote = secretNote;
    }
}
