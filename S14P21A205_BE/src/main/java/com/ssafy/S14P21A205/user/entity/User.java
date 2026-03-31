package com.ssafy.S14P21A205.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.concurrent.ThreadLocalRandom;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "users",
        indexes = {
                @Index(name = "uk_users_email", columnList = "email", unique = true)
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false)
    private Integer id;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(nullable = false, length = 30)
    private String nickname;

    @Column(name = "is_bot", nullable = false)
    private boolean isBot;

    @Enumerated(EnumType.STRING)
    @Column(name = "bot_difficulty", length = 16)
    private BotDifficulty botDifficulty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(nullable = false)
    private Integer point;

    public User(String email, String nickname) {
        this.email = email;
        this.nickname = normalizeNickname(nickname);
        this.isBot = false;
        this.botDifficulty = null;
        this.role = UserRole.GENERAL;
        this.point = 0;
    }

    public static User createBot(String email, String nickname, BotDifficulty botDifficulty) {
        User user = new User(email, nickname);
        user.isBot = true;
        user.botDifficulty = botDifficulty;
        return user;
    }

    private static String normalizeNickname(String nickname) {
        if (nickname == null) {
            return defaultNickname();
        }
        String trimmed = nickname.trim();
        if (trimmed.isEmpty()) {
            return defaultNickname();
        }
        return trimmed.length() > 30 ? trimmed.substring(0, 30) : trimmed;
    }

    private static String defaultNickname() {
        return String.valueOf(ThreadLocalRandom.current().nextInt(100_000, 1_000_000));
    }

    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    public void addPoints(int points) {
        this.point += points;
    }

    public void usePoints(int points) {
        this.point -= points;
    }

    public enum UserRole {
        GENERAL,
        ADMIN
    }
}
