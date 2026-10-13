package com.github.acshmily.test.domain;

import com.github.acshmily.annotation.Encrypt;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

/** 父类带 @Encrypt 字段：验证类层级字段扫描（jakarta 版本）。 */
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
