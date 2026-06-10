<?php

declare(strict_types=1);

require_once __DIR__ . '/../../src/Database.php';
require_once __DIR__ . '/../../src/Auth.php';

Auth::startSession();

if (!empty($_SESSION['admin_id'])) {
    header('Location: /admin/index.php');
    exit;
}

$error = '';

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $username = trim((string) ($_POST['username'] ?? ''));
    $password = (string) ($_POST['password'] ?? '');

    if (Auth::attemptLogin($username, $password)) {
        header('Location: /admin/index.php');
        exit;
    }

    $error = 'Usuario o contraseña incorrectos';
}
?>
<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Guruyu Admin - Iniciar sesión</title>
    <style>
        * { box-sizing: border-box; }
        body { font-family: system-ui, sans-serif; background: #f4f6f8; margin: 0; min-height: 100vh; display: grid; place-items: center; }
        .card { background: #fff; padding: 2rem; border-radius: 12px; box-shadow: 0 8px 24px rgba(0,0,0,.08); width: min(100%, 380px); }
        h1 { margin: 0 0 1.5rem; font-size: 1.4rem; }
        label { display: block; margin-bottom: .35rem; font-weight: 600; }
        input { width: 100%; padding: .7rem .8rem; margin-bottom: 1rem; border: 1px solid #d0d7de; border-radius: 8px; }
        button { width: 100%; padding: .75rem; border: 0; border-radius: 8px; background: #2563eb; color: #fff; font-weight: 600; cursor: pointer; }
        .error { color: #b91c1c; margin-bottom: 1rem; }
    </style>
</head>
<body>
    <div class="card">
        <h1>Guruyu Admin</h1>
        <?php if ($error): ?><div class="error"><?= htmlspecialchars($error) ?></div><?php endif; ?>
        <form method="post">
            <label for="username">Usuario</label>
            <input id="username" name="username" required autocomplete="username">
            <label for="password">Contraseña</label>
            <input id="password" name="password" type="password" required autocomplete="current-password">
            <button type="submit">Entrar</button>
        </form>
    </div>
</body>
</html>
