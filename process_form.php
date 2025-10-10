<?php
// 文字化け防止のため、エンコーディングを設定
header('Content-Type: text/html; charset=UTF-8');

if ($_SERVER["REQUEST_METHOD"] == "POST") {
    // フォームがPOSTで送信されたことを確認
    echo "<h1>送信成功しました！</h1>";
    echo "<p>WebサーバーはPOSTリクエストを正しく受け付けました。</p>";
    // 受け取ったデータを全て表示
    echo "<pre>";
    print_r($_POST);
    echo "</pre>";
} else {
    // POST以外の方法でアクセスされた場合
    echo "<h1>エラー</h1>";
    echo "<p>このページはフォームからアクセスしてください。POSTリクエストではありませんでした。</p>";
}
?>
