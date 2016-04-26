import spark.ModelAndView;
import spark.Session;
import spark.Spark;
import spark.template.mustache.MustacheTemplateEngine;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;

public class Main {
//    static HashMap<String, User> users = new HashMap<>();
//    static ArrayList<Message> messages = new ArrayList<>();


    public static void createTables(Connection conn) throws SQLException {
        Statement stmt = conn.createStatement();
        stmt.execute("CREATE TABLE IF NOT EXISTS users (id IDENTITY, name VARCHAR, password VARCHAR)");
        stmt.execute("CREATE TABLE IF NOT EXISTS messages (id IDENTITY, user_id INT, reply_id INT, text VARCHAR)");
    }

    public static void insertUser(Connection conn, String name, String password) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement("INSERT INTO users VALUES (null, ?, ?)");
        stmt.setString(1,name);
        stmt.setString(2,password);
        stmt.execute();
    }

    public static User selectUser(Connection conn, String name) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement("SELECT * FROM users WHERE name = ?");
        stmt.setString(1,name);
        ResultSet results = stmt.executeQuery();
        if (results.next()) {
            int id = results.getInt("id");
            String password = results.getString("password");
            return new User(id, name, password);
        }
        return null;
    }

    public static void insertMessage(Connection conn, int userId, int replyId, String text) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement("INSERT INTO messages VALUES (null, ?, ?, ?)");
        stmt.setInt(1, userId);
        stmt.setInt(2, replyId);
        stmt.setString(3, text);
        stmt.execute();
    }
    public static Message selectMessage(Connection conn, int id) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement(
                "SELECT * FROM messages JOIN users ON messages.user_id = users.id WHERE messages.id = ?");
        stmt.setInt(1, id);
        ResultSet results = stmt.executeQuery();
        if (results.next()) {
            int reply_id = results.getInt("messages.reply_id");
            String name = results.getString("users.name");
            String message = results.getString("messages.text");
            return new Message(id, reply_id, name, message);
        }
        return null;
    }

    public static ArrayList<Message> selectReplies(Connection conn, int replyId) throws SQLException {
        ArrayList<Message> messages = new ArrayList<>();
        PreparedStatement stmt = conn.prepareStatement(
                "SELECT * FROM messages INNER JOIN users ON messages.user_id = users.id WHERE messages.reply_id = ?");
        stmt.setInt(1,replyId);
        ResultSet results = stmt.executeQuery();
        while (results.next()) {
            int id = results.getInt("messages.id");
            String name = results.getString("users.name");
            String text = results.getString("messages.text");
            Message message = new Message(id,replyId,name,text);
            messages.add(message);
        }
        return messages;
    }

    public static void main(String[] args) throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:h2:./main");
        createTables(conn);

        Spark.init();

        addTestUsers(conn);
        addTestMessages(conn);

        Spark.get(
                "/",
                ((request, response) -> {
                    Session session = request.session();
                    String userName = session.attribute("userName");

                    String replyId = request.queryParams("replyId");
                    Integer replyIdNum = -1;
                    if (replyId != null) {
                        replyIdNum = Integer.valueOf(replyId);
                    }

                    ArrayList<Message> threads = selectReplies(conn, replyIdNum);
//                    for ( Message message : messages ) {
//                        if (message.replyId == replyIdNum ){
//                            threads.add(message);
//                        }
//                    }
                    HashMap m = new HashMap();
                    m.put("messages", threads);
                    m.put("userName", userName);
                    return new ModelAndView(m, "home.html");
                }),
                new MustacheTemplateEngine()
        );

        Spark.post(
                "/login",
                ((request, response) -> {
                    String loginName = request.queryParams("loginName");
                    String password = request.queryParams("password");
                    if (loginName == null) {
                        throw new Exception("Failed to enter a login name");
                    }

                    User user = selectUser(conn,loginName);
                    if (user == null) {
                        insertUser(conn, loginName, password);
                    }
                    else if (! password.equals(user.getPassword())) {   // check if password is valid
                        response.status(401);
                        return "Invalid Login";
                    }

                    Session session = request.session();
                    session.attribute("userName", loginName);

                    response.redirect("/");
                    return "";
                })
        );

        Spark.post(
                "/logout",
                ((request, response) -> {
                    request.session().invalidate();
                    response.redirect("/");
                    return "";
                })
        );
    }

    static void addTestMessages(Connection conn) throws SQLException {
        insertMessage (conn, 1, -1, "Hello world!");
        insertMessage (conn, 2, -1, "This is another thread");
        insertMessage (conn, 3, 1, "Cool thread, Alice!");
        insertMessage (conn, 4, 3, "Thanks");
    }

    static void addTestUsers(Connection conn) throws SQLException {
        insertUser(conn, "Alice", "");
        insertUser(conn, "Bob", "");
        insertUser(conn, "Charlie", "");
    }
}
