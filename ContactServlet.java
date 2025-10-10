import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Properties;

// JavaMail API
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

// Servletへのアクセスパスを定義 (HTMLフォームのactionに合わせる)
@WebServlet("/contact")
public class ContactServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    
    // ⭐ 送信先メールアドレス
    private static final String TO_EMAIL = "aonami.citybus@gmail.com"; 
    
    // ⭐ SMTPサーバーの認証情報 (🚨 アプリパスワードを使用してください 🚨)
    private static final String SMTP_HOST = "smtp.gmail.com";
    private static final String SMTP_PORT = "587";
    
    // 🔔 フォームの送信元（Gmailアカウント）の認証情報に置き換えてください 🔔
    private static final String SENDER_EMAIL = "your-gmail-account@gmail.com"; // 👈 あなたのGmailアドレス
    private static final String SENDER_PASS = "your-app-password";             // 👈 Gmailで生成したアプリパスワード

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // 1. 文字化け防止のためエンコーディングを設定
        request.setCharacterEncoding("UTF-8");

        // 2. フォームデータの取得
        String name = request.getParameter("name");
        String email = request.getParameter("email");
        String tel = request.getParameter("tel");
        String content = request.getParameter("content");
        String replyNeeded = request.getParameter("reply_needed");
        
        // 郵便番号やバス車両番号など、他の項目も同様に取得
        String zipcode = request.getParameter("zipcode");
        String zipcode2 = request.getParameter("zipcode2");
        String address = request.getParameter("address");
        String busNumberBottom = request.getParameter("bus_number_bottom");

        // 3. 簡易バリデーション (必須項目チェック)
        if (name == null || name.isEmpty() || email == null || email.isEmpty() || content == null || content.isEmpty()) {
            request.setAttribute("errorMessage", "必須項目が入力されていません。");
            request.getRequestDispatcher("/guide.html").forward(request, response);
            return;
        }

        // 4. JavaMailによるメール送信処理
        try {
            // SMTPプロパティの設定
            Properties props = new Properties();
            props.put("mail.smtp.host", SMTP_HOST);
            props.put("mail.smtp.port", SMTP_PORT);
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true"); // TLS有効化
            props.put("mail.smtp.ssl.protocols", "TLSv1.2"); // セキュリティ強化

            // 認証セッションの作成
            Session session = Session.getInstance(props, new Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(SENDER_EMAIL, SENDER_PASS);
                }
            });

            // メッセージオブジェクトの作成
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(SENDER_EMAIL, "青波シティバス お問い合わせ", "UTF-8"));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(TO_EMAIL));
            message.setSubject("【お問い合わせフォーム】新しい投稿 (" + name + "様)", "UTF-8");

            // メール本文の作成
            String mailBody = "以下の内容でお問い合わせがありました。\n\n";
            mailBody += "----------------------------------------\n";
            mailBody += "【お名前】: " + name + "\n";
            mailBody += "【メール】: " + email + "\n";
            mailBody += "【電話番号】: " + (tel != null ? tel : "未入力") + "\n";
            mailBody += "【郵便番号】: " + (zipcode != null ? zipcode + "-" + zipcode2 : "未入力") + "\n";
            mailBody += "【ご住所】: " + (address != null ? address : "未入力") + "\n";
            mailBody += "【回答の要否】: " + (replyNeeded != null ? (replyNeeded.equals("yes") ? "必要" : "不要") : "未回答") + "\
            mailBody += "----------------------------------------\n";
            mailBody += "【お問い合わせ内容】:\n" + content + "\n";
            mailBody += "----------------------------------------\n";
            
            message.setText(mailBody, "UTF-8");

            // メール送信
            Transport.send(message);

            // 5. 送信成功後の処理 (サンクスページにリダイレクト)
            response.sendRedirect("thank_you.html"); 

        } catch (jakarta.mail.MessagingException e) {
            // メール送信エラーのログ出力とエラー画面へのフォワード
            e.printStackTrace();
            request.setAttribute("errorMessage", "メール送信エラーが発生しました。時間を置いて再度お試しください。");
            request.getRequestDispatcher("/error.jsp").forward(request, response);
        } catch (Exception e) {
            // その他のエラー
            e.printStackTrace();
            request.setAttribute("errorMessage", "サーバー処理中に予期せぬエラーが発生しました。");
            request.getRequestDispatcher("/error.jsp").forward(request, response);
        }
    }
}
