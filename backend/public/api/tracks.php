<?php

declare(strict_types=1);

require_once __DIR__ . '/../../src/Database.php';
require_once __DIR__ . '/../../src/Auth.php';
require_once __DIR__ . '/../../src/helpers.php';

if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    jsonResponse(['error' => 'Método no permitido'], 405);
}

if (!Auth::validateApiKey()) {
    jsonResponse(['error' => 'API key inválida'], 401);
}

$config = require __DIR__ . '/../../config/config.php';
$pdo = Database::connection();

$requestUuid = trim((string) ($_GET['device_uuid'] ?? ''));
$limit = (int) ($config['track_points'] ?? 50);

if ($requestUuid !== '') {
    ensureDeviceExists($pdo, $requestUuid);
}

jsonResponse([
    'success' => true,
    'track_points' => max(1, min(50, $limit)),
    'tracks' => deviceTracks(
        $pdo,
        $limit,
        $requestUuid !== '' ? $requestUuid : null,
        enabledOnly: true,
    ),
]);
