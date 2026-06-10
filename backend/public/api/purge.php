<?php

declare(strict_types=1);

require_once __DIR__ . '/../../src/Database.php';
require_once __DIR__ . '/../../src/Auth.php';
require_once __DIR__ . '/../../src/helpers.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST' && $_SERVER['REQUEST_METHOD'] !== 'GET') {
    jsonResponse(['error' => 'Método no permitido'], 405);
}

if (!Auth::validateApiKey()) {
    jsonResponse(['error' => 'API key inválida'], 401);
}

$config = require __DIR__ . '/../../config/config.php';
$pdo = Database::connection();
$retentionDays = (int) ($config['retention_days'] ?? 30);
$deleted = purgeOldLocationReports($pdo, $retentionDays);

jsonResponse([
    'success' => true,
    'retention_days' => $retentionDays,
    'deleted_rows' => $deleted,
]);
