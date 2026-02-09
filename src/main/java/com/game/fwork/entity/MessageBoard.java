package com.game.fwork.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter @Setter
@Table(name = "t_message_board")
public class MessageBoard {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "target_user_id", nullable = false)
    private User targetUser;  // 被留言的人

    @ManyToOne
    @JoinColumn(name = "sender_user_id", nullable = false)
    private User senderUser;  // 留言的人

    @Column(name = "content", nullable = false, length = 255)
    private String content;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // 构造函数
    public MessageBoard() {
        this.createdAt = LocalDateTime.now();
    }

    public MessageBoard(User targetUser, User senderUser, String content) {
        this.targetUser = targetUser;
        this.senderUser = senderUser;
        this.content = content;
        this.createdAt = LocalDateTime.now();
    }
}