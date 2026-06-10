<?php

declare(strict_types=1);

final class Auth
{
    public static function startSession(): void
    {
        $config = require __DIR__ . '/../config/config.php';
        if (session_status() !== PHP_SESSION_ACTIVE) {
            session_name($config['admin']['session_name']);
            session_start();
        }
    }

    public static function requireAdmin(): void
    {
        self::startSession();
        if (empty($_SESSION['admin_id'])) {
            header('Location: /admin/login.php');
            exit;
        }
    }

    public static function attemptLogin(string $username, string $password): bool
    {
        self::startSession();
        $pdo = Database::connection();
        $stmt = $pdo->prepare('SELECT id, password_hash FROM admin_users WHERE username = ? LIMIT 1');
        $stmt->execute([$username]);
        $user = $stmt->fetch();

        if (!$user || !password_verify($password, $user['password_hash'])) {
            return false;
        }

        $_SESSION['admin_id'] = (int) $user['id'];
        $_SESSION['admin_username'] = $username;
        return true;
    }

    public static function logout(): void
    {
        self::startSession();
        $_SESSION = [];
        session_destroy();
    }

    public static function validateApiKey(): bool
    {
        $config = require __DIR__ . '/../config/config.php';
        $headerKey = $_SERVER['HTTP_X_API_KEY'] ?? '';
        return hash_equals($config['api_key'], $headerKey);
    }
}
