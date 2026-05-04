import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;

public class Server {

  // these lists store the alert log
  // the same index is used across all four lists
  static int nextId = 1;
  static ArrayList<Integer> alertIds = new ArrayList<Integer>();
  static ArrayList<String> alertMessages = new ArrayList<String>();
  static ArrayList<String> alertTimes = new ArrayList<String>();

  // each alert has a list of clients that acknowledged it
  static ArrayList<ArrayList<String>> alertAcks = new ArrayList<ArrayList<String>>();

  // stores the clients that have registered or contacted the server
  static ArrayList<String> clients = new ArrayList<String>();

  // stores log lines and writes them to a text file
  static ArrayList<String> logs = new ArrayList<String>();
  static String logFile = "server-log.txt";
  static final Object logLock = new Object();

  public static void main(String[] args) throws Exception {
    int port = 8080;
    if (args.length > 0) {
      port = Integer.parseInt(args[0]);
    }

    ServerSocket server = new ServerSocket(port);
    System.out.println("Server running at http://localhost:" + port);
    System.out.println("type an alert message and press enter to send it");

    Thread consoleThread =
        new Thread(
            new Runnable() {
              public void run() {
                readConsoleAlerts();
              }
            });
    consoleThread.start();

    while (true) {
      Socket socket = server.accept();

      // one thread per connection lets multiple clients use the server
      Thread t =
          new Thread(
              new Runnable() {
                public void run() {
                  handle(socket);
                }
              });
      t.start();
    }
  }

  static void handle(Socket socket) {
    try {
      BufferedReader in =
          new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));

      // GET /poll?client=abc HTTP/1.1
      String firstLine = in.readLine();
      if (firstLine == null) {
        socket.close();
        return;
      }

      // read and ignore the rest of the browser request headers
      String line;
      while ((line = in.readLine()) != null && line.length() > 0) {}

      String path = "/";
      String[] parts = firstLine.split(" ");
      if (parts.length >= 2) {
        path = parts[1];
      }

      route(socket, path);
    } catch (Exception e) {
      try {
        socket.close();
      } catch (Exception closeError) {
      }
    }
  }

  static void route(Socket socket, String path) throws IOException {
    // choose which method to call based on the URL path
    if (path.startsWith("/register")) {
      register(socket, path);
    } else if (path.startsWith("/poll")) {
      poll(socket, path);
    } else if (path.startsWith("/ack")) {
      acknowledge(socket, path);
    } else if (path.startsWith("/history")) {
      history(socket);
    } else {
      reply(socket, "server is running");
    }
  }

  static void readConsoleAlerts() {
    try {
      BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
      String message;

      while ((message = input.readLine()) != null) {
        if (message.trim().length() > 0) {
          createAlert(message, "console");
          System.out.println("alert sent");
        }
      }
    } catch (Exception e) {
      System.out.println("console read error: " + e.getMessage());
    }
  }

  static void register(Socket socket, String path) throws IOException {
    // client sends its id here when it starts
    String client = getParam(path, "client");
    String ip = socket.getInetAddress().getHostAddress();
    if (client.length() == 0) {
      client = "unknown";
    }

    // synchronized protects shared lists when multiple clients connect
    synchronized (Server.class) {
      addClient(client);
    }

    addLog("register ip=" + ip + " client=" + client);

    // the client displays this welcome message after registering
    reply(socket, "welcome=" + encode("Welcome to the alert system."));
  }

  static void poll(Socket socket, String path) throws IOException {
    // client repeatedly calls /poll to ask if an alert is waiting
    String client = getParam(path, "client");
    if (client.length() == 0) {
      client = "unknown";
    }

    synchronized (Server.class) {
      addClient(client);

      // send the first alert this client has not acknowledged yet
      for (int i = 0; i < alertIds.size(); i++) {
        if (!alertAcks.get(i).contains(client)) {
          String response =
              "alert=true\n"
                  + "id="
                  + alertIds.get(i)
                  + "\nmessage="
                  + encode(alertMessages.get(i))
                  + "\ntime="
                  + encode(alertTimes.get(i));
          reply(socket, response);
          return;
        }
      }
    }

    reply(socket, "alert=false");
  }

  static void createAlert(String message, String source) {
    if (message.trim().length() == 0) {
      message = "ATTENTION STUDENTS!! EMERGENCY ALERT";
    }

    int id;
    synchronized (Server.class) {
      // give each alert a unique id
      id = nextId;
      nextId++;

      // store all parts of the alert in the history lists
      alertIds.add(id);
      alertMessages.add(message);
      alertTimes.add(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));

      // this alert starts with no acknowledgements
      alertAcks.add(new ArrayList<String>());
    }

    addLog("send source=" + source + " id=" + id + " message=" + message);
  }

  static void acknowledge(Socket socket, String path) throws IOException {
    // client calls /ack after the user clicks the acknowledge button
    String client = getParam(path, "client");
    String ip = socket.getInetAddress().getHostAddress();
    int id = toInt(getParam(path, "id"));
    boolean found = false;

    synchronized (Server.class) {
      addClient(client);
      for (int i = 0; i < alertIds.size(); i++) {
        if (alertIds.get(i) == id) {
          // record that this client has received this alert
          if (!alertAcks.get(i).contains(client)) {
            alertAcks.get(i).add(client);
          }
          found = true;
        }
      }
    }

    if (found) {
      addLog("ack ip=" + ip + " client=" + client + " id=" + id);
      reply(socket, "acknowledged=true");
    } else {
      reply(socket, "acknowledged=false");
    }
  }

  static void history(Socket socket) throws IOException {
    // build a plain text history report
    StringBuilder text = new StringBuilder();

    synchronized (Server.class) {
      // first line shows how many clients the server has seen
      text.append("clients=").append(clients.size()).append("\n");

      // then print one line for every alert sent
      for (int i = 0; i < alertIds.size(); i++) {
        text.append("id=")
            .append(alertIds.get(i))
            .append(" time=")
            .append(alertTimes.get(i))
            .append(" acknowledgements=")
            .append(alertAcks.get(i).size())
            .append(" message=")
            .append(alertMessages.get(i))
            .append("\n");
      }
    }

    reply(socket, text.toString());
  }

  static void addClient(String client) {
    if (!clients.contains(client)) {
      clients.add(client);
    }
  }

  static void addLog(String line) {
    synchronized (logLock) {
      String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
      logs.add(time + " " + line);
      writeLogs();
    }
  }

  static void writeLogs() {
    try {
      PrintWriter out = new PrintWriter(new FileWriter(logFile));
      for (int i = 0; i < logs.size(); i++) {
        out.println(logs.get(i));
      }
      out.close();
    } catch (Exception e) {
      System.out.println("log write error: " + e.getMessage());
    }
  }

  static String getParam(String path, String name) throws IOException {
    // reads values from the URL query string
    int question = path.indexOf('?');
    if (question < 0) {
      return "";
    }

    String query = path.substring(question + 1);
    String[] parts = query.split("&");
    for (int i = 0; i < parts.length; i++) {
      String[] pair = parts[i].split("=", 2);
      if (decode(pair[0]).equals(name)) {
        if (pair.length == 2) {
          return decode(pair[1]);
        }
        return "";
      }
    }

    return "";
  }

  static int toInt(String value) {
    // converts text into a number, returns -1 if the text is not a number
    try {
      return Integer.parseInt(value);
    } catch (Exception e) {
      return -1;
    }
  }

  static String encode(String value) throws UnsupportedEncodingException {
    // encode text so spaces and special characters can safely go in a URL
    return URLEncoder.encode(value, "UTF-8");
  }

  static String decode(String value) throws UnsupportedEncodingException {
    // decode URL text back into normal text
    return URLDecoder.decode(value, "UTF-8");
  }

  static void reply(Socket socket, String text) throws IOException {
    reply(socket, text, "text/plain");
  }

  static void reply(Socket socket, String text, String type) throws IOException {
    // sends the HTTP response back using the socket
    byte[] bytes = text.getBytes("UTF-8");
    OutputStream out = socket.getOutputStream();
    PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, "UTF-8"));

    writer.print("HTTP/1.1 200 OK\r\n");
    writer.print("Content-Type: " + type + "; charset=utf-8\r\n");
    writer.print("Content-Length: " + bytes.length + "\r\n");
    writer.print("Connection: close\r\n");
    writer.print("\r\n");
    writer.flush();

    out.write(bytes);
    out.flush();
    socket.close();
  }
}
