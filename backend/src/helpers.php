<?php

declare(strict_types=1);

function jsonResponse(array $data, int $status = 200): void
{
    http_response_code($status);
    header('Content-Type: application/json; charset=utf-8');
    echo json_encode($data, JSON_UNESCAPED_UNICODE);
    exit;
}

function readJsonBody(): array
{
    $raw = file_get_contents('php://input');
    if ($raw === false || $raw === '') {
        return [];
    }
    $decoded = json_decode($raw, true);
    return is_array($decoded) ? $decoded : [];
}

function ensureDeviceExists(PDO $pdo, string $uuid): void
{
    $stmt = $pdo->prepare('INSERT IGNORE INTO devices (uuid, display_name, is_enabled) VALUES (?, ?, 0)');
    $stmt->execute([$uuid, 'Dispositivo ' . substr($uuid, 0, 8)]);
}

function isDeviceEnabled(PDO $pdo, string $uuid): bool
{
    $stmt = $pdo->prepare('SELECT is_enabled FROM devices WHERE uuid = ? LIMIT 1');
    $stmt->execute([$uuid]);
    $row = $stmt->fetch();
    return $row && (int) $row['is_enabled'] === 1;
}

function latestLocationsForEnabledDevices(PDO $pdo, int $staleMinutes): array
{
    $sql = <<<SQL
        SELECT
            d.uuid,
            d.display_name,
            d.is_enabled,
            lr.latitude,
            lr.longitude,
            lr.reported_at,
            TIMESTAMPDIFF(MINUTE, lr.received_at, UTC_TIMESTAMP()) AS minutes_ago
        FROM devices d
        INNER JOIN location_reports lr ON lr.id = (
            SELECT id
            FROM location_reports
            WHERE device_uuid = d.uuid
            ORDER BY reported_at DESC
            LIMIT 1
        )
        WHERE d.is_enabled = 1
        ORDER BY d.display_name ASC
    SQL;

    $rows = $pdo->query($sql)->fetchAll();
    $result = [];

    foreach ($rows as $row) {
        $minutesAgo = (int) ($row['minutes_ago'] ?? 999);
        $result[] = [
            'uuid' => $row['uuid'],
            'display_name' => $row['display_name'],
            'latitude' => (float) $row['latitude'],
            'longitude' => (float) $row['longitude'],
            'reported_at' => $row['reported_at'],
            'is_stale' => $minutesAgo >= $staleMinutes,
        ];
    }

    return $result;
}

function purgeOldLocationReports(PDO $pdo, int $retentionDays): int
{
    if ($retentionDays < 1) {
        return 0;
    }

    $stmt = $pdo->prepare(
        'DELETE FROM location_reports WHERE received_at < DATE_SUB(UTC_TIMESTAMP(), INTERVAL ? DAY)'
    );
    $stmt->execute([$retentionDays]);

    return $stmt->rowCount();
}

function maybePurgeOldLocationReports(PDO $pdo): void
{
    $config = require __DIR__ . '/../config/config.php';
    $retentionDays = (int) ($config['retention_days'] ?? 30);
    $intervalSeconds = (int) ($config['purge_interval_seconds'] ?? 3600);
    $lockFile = sys_get_temp_dir() . '/guruyu_purge.lock';

    if (file_exists($lockFile) && (time() - (int) filemtime($lockFile)) < $intervalSeconds) {
        return;
    }

    @touch($lockFile);
    purgeOldLocationReports($pdo, $retentionDays);
}
