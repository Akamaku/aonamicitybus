// ... (ContactServlet.java 内)

@WebServlet("/contact") // 👈 HTMLの action にこのパス（例: action="/contact"）を設定
public class ContactServlet extends HttpServlet {
    // ...
    
    // フォームの POST リクエストを処理するのはこのメソッドです。
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        // ... (ここに処理を記述)
    }
    
    // ...
}
