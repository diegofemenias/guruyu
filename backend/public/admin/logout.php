<?php

declare(strict_types=1);

require_once __DIR__ . '/../../src/Auth.php';

Auth::logout();
header('Location: /admin/login.php');
exit;
