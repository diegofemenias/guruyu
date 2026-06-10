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
$selfEnabled = null;

if ($requestUuid !== '') {
    ensureDeviceExists($pdo, $requestUuid);
    $selfEnabled = isDeviceEnabled($pdo, $requestUuid);
}

jsonResponse([
    'success' => true,
    'self_enabled' => $selfEnabled,
    'stale_minutes' => (int) $config['stale_minutes'],
    'devices' => latestLocationsForEnabledDevices($pdo, (int) $config['stale_minutes']),
]);
