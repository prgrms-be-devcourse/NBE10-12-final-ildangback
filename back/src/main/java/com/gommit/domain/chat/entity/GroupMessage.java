package com.gommit.domain.chat.entity;

import com.gommit.global.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "group_messages")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupMessage extends BaseEntity {

    @Column(nullable = false)
    private Long groupId;

    private Long senderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageType messageType;

    @Column(nullable = false, length = 1000)
    private String content;

    @Builder
    public GroupMessage(Long groupId, Long senderId, String content) {
        this.groupId = groupId;
        this.senderId = senderId;
        this.content = content;
        this.messageType = MessageType.TEXT;
    }
}
