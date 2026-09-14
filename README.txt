白急線ポータル

index.html をブラウザで開くだけで動きます（サーバー不要、GitHub Pages にそのまま置けます）。
ダイヤデータは index.html 内に埋め込み済みです。
data/ フォルダには元の OuDiaSecond ファイルを入れています。

ダイヤを更新するとき:
 index.html 内の `const ROUTES = [ {name:'白急桜田線', text: "..."} ]`
    の文字列を新しい .oud2 の内容（JSON文字列化したもの）に置き換える

路線を増やすとき:
 index.html 内の ROUTES 配列に要素を追加します。
   const ROUTES = [
     {name:'白急桜田線', text: "…"},
     {name:'白急〇〇線', text: "…"},   // ← 追加（text は .oud2 の内容を JSON 文字列化したもの）
   ];
 走行位置・運用表・時刻表のそれぞれで路線を選べるようになります。

大和路線（リアルタイム走行位置）と運用投稿:
 - 大和路線ページは JR西日本「列車走行位置」の JSON
   (https://www.train-guide.westjr.co.jp/api/v3/yamatoji.json) を15秒ごとに読みます（非公式利用）。
   ブラウザから直接取得できない（CORS で失敗する）場合は、下記の GAS を設置すると中継されます。
 - 運用投稿を利用者間で共有するには gas/Code.gs を Google Apps Script のウェブアプリとして
   公開し、その URL を index.html の  const API_URL = '';  に入れてください。
   API_URL が空のあいだは、投稿はその端末の localStorage にだけ保存されます（共有されません）。
